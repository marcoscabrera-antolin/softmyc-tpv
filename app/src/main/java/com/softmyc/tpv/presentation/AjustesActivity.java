package com.softmyc.tpv.presentation;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.printer.PrinterHelper;
import com.softmyc.tpv.domain.port.AjustesImpresora;
import com.softmyc.tpv.domain.port.ValidadorImpresora;
import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.data.local.AppDatabase;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AjustesActivity extends BaseActivity {

    private static final int PICK_CSV_FILE = 101;

    private EditText etImpresoraIp, etImpresoraPuerto;
    private EditText etNombreRestaurante, etDireccionFiscal, etInformacionFiscal;
    private EditText etBackendUrl, etApiKey, etSimboloMoneda;
    private android.widget.Spinner spinnerTamanioLetra;
    private Button btnProbarImpresion, btnGuardarIp, btnSeleccionarCsv, btnGuardarAjustesTicket, btnGuardarServidor;
    private ImageButton btnVolver;
    private TextView tvLogs;

    private View cardAdminTenantSelector;
    private TextView tvAdminCurrentTenantInfo;
    private Button btnCambiarTenantAdminAjustes;

    private Button btnCerrarSesion;
    private TextView tvUsuarioSesionInfo;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ajustes);

        setupBackButton();

        etImpresoraIp       = findViewById(R.id.etImpresoraIp);
        etImpresoraPuerto   = findViewById(R.id.etImpresoraPuerto);
        btnProbarImpresion  = findViewById(R.id.btnProbarImpresion);
        btnGuardarIp        = findViewById(R.id.btnGuardarIp);
        btnSeleccionarCsv   = findViewById(R.id.btnSeleccionarCsv);
        btnVolver           = findViewById(R.id.btnVolver);
        tvLogs              = findViewById(R.id.tvLogsImportacion);

        etNombreRestaurante = findViewById(R.id.etNombreRestaurante);
        etDireccionFiscal   = findViewById(R.id.etDireccionFiscal);
        etInformacionFiscal = findViewById(R.id.etInformacionFiscal);
        spinnerTamanioLetra = findViewById(R.id.spinnerTamanioLetra);
        btnGuardarAjustesTicket = findViewById(R.id.btnGuardarAjustesTicket);

        etBackendUrl        = findViewById(R.id.etBackendUrl);
        etApiKey            = findViewById(R.id.etApiKey);
        etSimboloMoneda     = findViewById(R.id.etSimboloMoneda);
        btnGuardarServidor  = findViewById(R.id.btnGuardarServidor);

        cardAdminTenantSelector      = findViewById(R.id.cardAdminTenantSelector);
        tvAdminCurrentTenantInfo     = findViewById(R.id.tvAdminCurrentTenantInfo);
        btnCambiarTenantAdminAjustes = findViewById(R.id.btnCambiarTenantAdminAjustes);

        btnCerrarSesion              = findViewById(R.id.btnCerrarSesion);
        tvUsuarioSesionInfo          = findViewById(R.id.tvUsuarioSesionInfo);

        String[] opcionesLetra = {"Normal (Estándar)", "Mediano (Doble altura)", "Grande (Doble alto y ancho)"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, R.layout.spinner_item_dark, opcionesLetra);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_dark);
        spinnerTamanioLetra.setAdapter(adapter);

        btnVolver.setOnClickListener(v -> finish());

        cargarAjustesImpresora();
        cargarAjustesTicket();
        cargarAjustesServidor();
        configurarSeccionAdminTenant();
        configurarSeccionCerrarSesion();

        btnGuardarIp.setOnClickListener(v -> guardarIpImpresora());
        btnProbarImpresion.setOnClickListener(v -> probarConexionImpresora());
        btnSeleccionarCsv.setOnClickListener(v -> abrirSelectorDeCSV());
        btnGuardarAjustesTicket.setOnClickListener(v -> guardarAjustesTicket());
        btnGuardarServidor.setOnClickListener(v -> guardarAjustesServidor());
    }

    private void configurarSeccionCerrarSesion() {
        SharedPreferences printerPrefs = getSecurePreferences();
        SharedPreferences sessionPrefs = com.softmyc.tpv.data.SecurePreferencesHelper.getSessionPrefs(this);

        String username = printerPrefs.getString("user_name", sessionPrefs.getString("user_name", "Operador TPV"));
        String role = printerPrefs.getString("user_role", sessionPrefs.getString("user_role", "tpv_operator"));

        if (tvUsuarioSesionInfo != null) {
            tvUsuarioSesionInfo.setText(getString(R.string.ajustes_usuario_sesion_fmt, username, role.toUpperCase(java.util.Locale.ROOT)));
        }

        if (btnCerrarSesion != null) {
            btnCerrarSesion.setOnClickListener(v -> mostrarDialogoConfirmacionCerrarSesion());
        }
    }

    private void mostrarDialogoConfirmacionCerrarSesion() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.confirmar_cierre_de_sesion))
                .setMessage(getString(R.string.seguro_que_quieres_cerrar_sesion))
                .setPositiveButton(getString(R.string.cerrar_sesion), (dialog, which) -> ejecutarCierreDeSesion())
                .setNegativeButton(getString(R.string.cancelar), null)
                .show();
    }

    private void ejecutarCierreDeSesion() {
        SharedPreferences printerPrefs = getSecurePreferences();
        SharedPreferences sessionPrefs = com.softmyc.tpv.data.SecurePreferencesHelper.getSessionPrefs(this);

        com.softmyc.tpv.data.sync.SyncScheduler.cancelar(this);
        // La sesión local se cierra siempre y de inmediato; el servidor se avisa después (si hay red) para revocar los tokens.
        final com.softmyc.tpv.data.auth.AuthSession sesionCerrada = com.softmyc.tpv.data.auth.Auth.store(this).load();
        com.softmyc.tpv.data.auth.Auth.store(this).clear();
        final android.content.Context appContext = getApplicationContext();
        new Thread(() -> com.softmyc.tpv.data.auth.Auth.manager(appContext).revokeRemote(sesionCerrada), "cerrar-sesion").start();

        printerPrefs.edit()
                .remove("auth_token")
                .remove("user_role")
                .remove("user_name")
                .remove("restaurant_id")
                .remove("current_tenant")
                .apply();

        sessionPrefs.edit()
                .remove("auth_token")
                .remove("hora_login")
                .remove("user_role")
                .remove("user_name")
                .remove("restaurant_id")
                .remove("current_tenant")
                .apply();

        Toast.makeText(this, getString(R.string.sesion_cerrada_correctamente), Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void configurarSeccionAdminTenant() {
        if (cardAdminTenantSelector == null) return;
        SharedPreferences prefs = getSecurePreferences();
        String role = prefs.getString("user_role", "tpv_operator");
        String currentRestId = prefs.getString("restaurant_id", "lanube");

        if ("admin".equalsIgnoreCase(role)) {
            cardAdminTenantSelector.setVisibility(View.VISIBLE);
            String nombreNegocio = "lanube".equalsIgnoreCase(currentRestId) ? "La Nube Bocatería" : ("elbocata".equalsIgnoreCase(currentRestId) ? "El Bocata" : currentRestId);
            if (tvAdminCurrentTenantInfo != null) {
                tvAdminCurrentTenantInfo.setText(getString(R.string.ajustes_restaurante_activo_fmt, nombreNegocio, currentRestId));
            }
            if (btnCambiarTenantAdminAjustes != null) {
                btnCambiarTenantAdminAjustes.setOnClickListener(v -> {
                    AdminTenantSelectorDialog dialog = AdminTenantSelectorDialog.newInstance();
                    dialog.setOnTenantSwitchedListener((newId, newName) -> {
                        configurarSeccionAdminTenant();
                    });
                    dialog.show(getSupportFragmentManager(), "AdminTenantSelectorAjustes");
                });
            }
        } else {
            cardAdminTenantSelector.setVisibility(View.GONE);
        }
    }

    protected SharedPreferences getSecurePreferences() {
        return com.softmyc.tpv.data.SecurePreferencesHelper.getPrinterPrefs(this);
    }

    private void cargarAjustesTicket() {
        SharedPreferences prefs = getSecurePreferences();
        String localNombre = prefs.getString("nombre_restaurante", "SoftMYC");
        String localDireccion = prefs.getString("direccion_fiscal", "");
        String localInfo = prefs.getString("informacion_fiscal", "");
        int localTamanio = prefs.getInt("tamanio_letra", 0);

        etNombreRestaurante.setText(localNombre);
        etDireccionFiscal.setText(localDireccion);
        etInformacionFiscal.setText(localInfo);
        if (localTamanio >= 0 && localTamanio <= 2) {
            spinnerTamanioLetra.setSelection(localTamanio);
        }
    }

    private void guardarAjustesTicket() {
        String nombre = etNombreRestaurante.getText().toString().trim();
        String direccion = etDireccionFiscal.getText().toString().trim();
        String informacion = etInformacionFiscal.getText().toString().trim();
        int tamanio = spinnerTamanioLetra.getSelectedItemPosition();

        if (nombre.isEmpty()) {
            Toast.makeText(this, getString(R.string.escribe_un_nombre_para_el_establecimient), Toast.LENGTH_SHORT).show();
            return;
        }

        getSecurePreferences().edit()
                .putString("nombre_restaurante", nombre)
                .putString("direccion_fiscal", direccion)
                .putString("informacion_fiscal", informacion)
                .putInt("tamanio_letra", tamanio)
                .apply();

        Toast.makeText(this, getString(R.string.ajustes_de_ticket_guardados_con_exito), Toast.LENGTH_SHORT).show();
    }

    private void cargarAjustesServidor() {
        SharedPreferences prefs = getSecurePreferences();
        String url = prefs.getString("backend_base_url", "");
        String apiKey = prefs.getString("backend_api_key", "");
        String moneda = prefs.getString("simbolo_moneda", "EUR");
        if (moneda.equals("€")) moneda = "EUR";

        etBackendUrl.setText(url);
        etApiKey.setText(apiKey);
        etSimboloMoneda.setText(moneda);
    }

    private void guardarAjustesServidor() {
        String url = etBackendUrl.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        String moneda = etSimboloMoneda.getText().toString().trim();

        if (moneda.isEmpty() || moneda.equals("€")) moneda = "EUR";

        // Normalizar la URL defensivamente para corregir typos antes de guardarla
        String cleanedUrl = com.softmyc.tpv.data.network.OrderSyncService.cleanUrl(url);

        if (!url.isEmpty() && (cleanedUrl.isEmpty() || (!cleanedUrl.startsWith("http://") && !cleanedUrl.startsWith("https://")))) {
            Toast.makeText(this, getString(R.string.url_del_servidor_no_valida), Toast.LENGTH_LONG).show();
            return;
        }

        // Cambiar de servidor exige confirmación: cierra la sesión y reenvía todas las ventas al nuevo servidor.
        if (com.softmyc.tpv.data.sync.SyncReset.cambiaDeServidor(com.softmyc.tpv.data.auth.BackendUrl.root(this), cleanedUrl)) {
            final String urlNueva = cleanedUrl;
            final String claveNueva = apiKey;
            final String monedaNueva = moneda;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.cambiar_de_servidor))
                    .setMessage(getString(R.string.ajustes_cambiar_servidor_msg))
                    .setPositiveButton(getString(R.string.cambiar), (dialog, which) -> aplicarAjustesServidor(urlNueva, claveNueva, monedaNueva))
                    .setNegativeButton(getString(R.string.cancelar), null)
                    .show();
            return;
        }
        aplicarAjustesServidor(cleanedUrl, apiKey, moneda);
    }

    private void aplicarAjustesServidor(String cleanedUrl, String apiKey, String moneda) {
        // Cambiar de servidor cierra la sesión: los tokens solo valen para el servidor que los emitió.
        final boolean cambiaDeServidor = com.softmyc.tpv.data.sync.SyncReset.cambiaDeServidor(
                com.softmyc.tpv.data.auth.BackendUrl.root(this), cleanedUrl);
        getSecurePreferences().edit()
                .putString("backend_base_url", cleanedUrl)
                .putString("backend_api_key", apiKey)
                .putString("simbolo_moneda", moneda)
                .apply();

        // Actualizar el EditText para mostrar la URL normalizada al usuario
        etBackendUrl.setText(cleanedUrl);

        Toast.makeText(this, getString(R.string.servidor_y_credenciales_guardados_con_ex), Toast.LENGTH_SHORT).show();
        if (cambiaDeServidor) reenviarVentasAlNuevoServidor();
        if (cambiaDeServidor && com.softmyc.tpv.data.auth.Auth.store(this).load() != null) {
            Toast.makeText(this, getString(R.string.has_cambiado_de_servidor_inicia_sesion_d), Toast.LENGTH_LONG).show();
            ejecutarCierreDeSesion();
        }
    }

    /** ARQ-10: las marcas de sincronizada eran del servidor anterior; se ponen todas pendientes (la subida es idempotente). */
    private void reenviarVentasAlNuevoServidor() {
        com.softmyc.tpv.data.sync.SyncScheduler.cancelar(this);
        final android.content.Context appContext = getApplicationContext();
        executor.execute(() -> {
            int reenviar = com.softmyc.tpv.data.sync.SyncReset.marcarTodasPendientesEnTodosLosNegocios(appContext);
            AppExecutors.enUiSiViva(this, () -> Toast.makeText(this,
                    getResources().getQuantityString(R.plurals.ajustes_ventas_reenviar, reenviar, reenviar), Toast.LENGTH_LONG).show());
        });
    }

    private void cargarAjustesImpresora() {
        SharedPreferences prefs = getSecurePreferences();
        String localIp = prefs.getString("impresora_ip", "192.168.0.20");
        int localPort = prefs.getInt("impresora_puerto", AjustesImpresora.PUERTO_POR_DEFECTO);

        etImpresoraIp.setText(localIp);
        etImpresoraPuerto.setText(String.valueOf(localPort));
    }

    private void guardarIpImpresora() {
        String ip = etImpresoraIp.getText().toString().trim();
        String puertoStr = etImpresoraPuerto.getText().toString().trim();

        if (ip.isEmpty() || puertoStr.isEmpty()) {
            Toast.makeText(this, getString(R.string.completa_la_direccion_ip_y_puerto), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ValidadorImpresora.esDireccionValida(ip)) {
            Toast.makeText(this, getString(R.string.la_direccion_ip_o_el_nombre_de_host_no_e), Toast.LENGTH_SHORT).show();
            return;
        }

        int puerto = ValidadorImpresora.puertoValido(puertoStr);
        if (puerto == ValidadorImpresora.PUERTO_INVALIDO) {
            Toast.makeText(this, getString(R.string.el_puerto_debe_ser_un_numero_entre_1_y_6), Toast.LENGTH_SHORT).show();
            return;
        }

        getSecurePreferences().edit()
                .putString("impresora_ip", ip)
                .putInt("impresora_puerto", puerto)
                .apply();

        Toast.makeText(this, getString(R.string.configuracion_guardada), Toast.LENGTH_SHORT).show();
    }

    private void probarConexionImpresora() {
        String ip = etImpresoraIp.getText().toString().trim();
        String puertoStr = etImpresoraPuerto.getText().toString().trim();

        if (ip.isEmpty() || puertoStr.isEmpty()) {
            Toast.makeText(this, getString(R.string.establece_valores_correctos_para_ip_y_pu), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ValidadorImpresora.esDireccionValida(ip)) {
            Toast.makeText(this, getString(R.string.la_direccion_ip_o_el_nombre_de_host_no_e), Toast.LENGTH_SHORT).show();
            return;
        }

        int puerto = ValidadorImpresora.puertoValido(puertoStr);
        if (puerto == ValidadorImpresora.PUERTO_INVALIDO) {
            Toast.makeText(this, getString(R.string.el_puerto_debe_ser_un_numero_entre_1_y_6), Toast.LENGTH_SHORT).show();
            return;
        }
        int tamanio = spinnerTamanioLetra.getSelectedItemPosition();
        Toast.makeText(this, getString(R.string.ajustes_enviando_ticket_prueba_fmt, ip), Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                PrinterHelper printer = new PrinterHelper(ip, puerto, tamanio);
                String ticketPrueba = "\n\nSoftMYC - Ticket de Prueba\n" +
                        "--------------------------------\n" +
                        "Impresion de red exitosa!\n" +
                        "Software TPV Enterprise\n\n\n";
                printer.print(AjustesActivity.this, ticketPrueba);
            } catch (Exception e) {
                AppExecutors.enUiSiViva(this, () -> Toast.makeText(AjustesActivity.this, getString(R.string.error_de_impresion_revisa_conexion_de_re), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void abrirSelectorDeCSV() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Selecciona Archivo CSV de Carta"), PICK_CSV_FILE);
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();   // sin esto cada visita a Ajustes dejaba un hilo vivo para siempre
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CSV_FILE && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                importarArchivoCSV(fileUri);
            }
        }
    }

    private void importarArchivoCSV(Uri csvUri) {
        tvLogs.setText(getString(R.string.iniciando_importacion_n));
        executor.execute(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(csvUri)) {
                if (inputStream == null) {
                    AppExecutors.enUiSiViva(this, () -> tvLogs.setText(getString(R.string.no_se_pudo_abrir_el_archivo_seleccionado)));
                    return;
                }
                com.softmyc.tpv.data.CsvParser.ParseResult result = com.softmyc.tpv.data.CsvParser.parse(inputStream);

                AppDatabase db = AppDatabase.getDatabase(AjustesActivity.this);
                boolean applied = com.softmyc.tpv.data.local.CatalogSync.replaceCatalog(
                        db, result.productos, result.extras, result.salsas);

                StringBuilder strLogs = new StringBuilder();
                for (String logLine : result.logs) {
                    strLogs.append(logLine).append("\n");
                }

                if (!applied) {
                    strLogs.append("\n=== IMPORTACIÓN CANCELADA ===\n");
                    strLogs.append("El archivo no contiene ningún producto válido. La carta actual no se ha modificado.\n");
                } else {
                    strLogs.append("\n=== IMPORTACIÓN COMPLETADA ===\n");
                    strLogs.append("Productos cargados: ").append(result.productos.size()).append("\n");
                    strLogs.append("Ingredientes extras: ").append(result.extras.size()).append("\n");
                    strLogs.append("Salsas cargadas: ").append(result.salsas.size()).append("\n");
                    strLogs.append("El stock y las unidades de medida de los productos existentes se han conservado.\n");
                }

                AppExecutors.enUiSiViva(this, () -> tvLogs.setText(strLogs.toString()));

            } catch (Exception e) {
                AppExecutors.enUiSiViva(this, () -> {
                    tvLogs.setText(getString(R.string.ajustes_error_leyendo_archivo_fmt, e.getMessage()));
                    Toast.makeText(AjustesActivity.this, getString(R.string.falla_al_leer_csv), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
