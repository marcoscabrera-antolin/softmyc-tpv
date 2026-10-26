package com.softmyc.tpv.presentation;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.*;
import com.softmyc.tpv.data.repo.ProductoRepositoryRoom;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;
import com.softmyc.tpv.domain.repository.ProductoRepository;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.sync.CatalogOutbox;
import com.softmyc.tpv.data.sync.CatalogOutboxSender;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModificarPreciosActivity extends BaseActivity {

    private EditText inputID, inputNombre, inputPrecio, inputIngredientes, inputCategoria;
    private Button btnBuscar, btnCrear, btnGuardar, btnEliminar;

    private Integer codigoActual = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modificar_precios);

        setupBackButton();
        inicializarVistas();

        btnBuscar.setOnClickListener(v -> buscarProducto());
        btnCrear.setOnClickListener(v -> crearProductoNuevo());
        btnGuardar.setOnClickListener(v -> guardarCambios());
        btnEliminar.setOnClickListener(v -> confirmarEliminacion());
        
        inputID.setOnEditorActionListener((v, actionId, event) -> {
             buscarProducto();
             return true; 
        });
    }

    private void inicializarVistas() {
        inputID = findViewById(R.id.inputID);
        inputNombre = findViewById(R.id.inputNombre);
        inputPrecio = findViewById(R.id.inputPrecio);
        inputIngredientes = findViewById(R.id.inputIngredientes);
        inputCategoria = findViewById(R.id.inputCategoria);

        btnBuscar = findViewById(R.id.btnBuscar);
        btnCrear = findViewById(R.id.btnCrear);
        btnGuardar = findViewById(R.id.btnGuardar);
        btnEliminar = findViewById(R.id.btnEliminar);
    }

    private void buscarProducto() {
        String idTxt = inputID.getText().toString().trim();
        if (idTxt.isEmpty()) return;

        int id;
        try {
            id = Integer.parseInt(idTxt);
        } catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.el_id_debe_ser_un_numero), Toast.LENGTH_SHORT).show();
            return;
        }
        limpiarCamposMenosID();

        AppExecutors.disco().execute(() -> {
            ProductoCatalogo pl = repositorio().buscarPorCodigo(id);
            AppExecutors.enUiSiViva(this, () -> {
                if (pl != null) {
                    codigoActual = pl.codigo;
                    inputNombre.setText(pl.nombre);
                    inputPrecio.setText(String.valueOf(pl.precio));
                    inputCategoria.setText(pl.categoria);

                    if (pl.ingredientes != null) {
                        inputIngredientes.setText(String.join(", ", pl.ingredientes));
                    }
                    activarModoEdicion(true);
                    Toast.makeText(this, getString(R.string.producto_cargado), Toast.LENGTH_SHORT).show();
                } else {
                    activarModoEdicion(false);
                    Toast.makeText(this, getString(R.string.id_libre_puedes_crear_uno_nuevo), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void crearProductoNuevo() {
        ProductoCatalogo pl = recogerDatosDelFormulario();
        if (pl == null) return;

        AppExecutors.disco().execute(() -> {
            repositorio().guardar(pl);
            anotarCambio(CatalogOutbox.UPSERT, pl.codigo);
            AppExecutors.enUiSiViva(this, () -> {
                Toast.makeText(this, getString(R.string.producto_creado_localmente), Toast.LENGTH_SHORT).show();
                enviarCola();
                limpiarTodo();
            });
        });
    }

    private void guardarCambios() {
        if (codigoActual == null) return;

        ProductoCatalogo pl = recogerDatosDelFormulario();
        if (pl == null) return;

        AppExecutors.disco().execute(() -> {
            int filas = repositorio().actualizarDatos(pl);
            if (filas == 0) {
                AppExecutors.enUiSiViva(this, () -> Toast.makeText(this,
                        "⚠️ El producto ya no existe; no se ha guardado nada", Toast.LENGTH_LONG).show());
                return;
            }
            anotarCambio(CatalogOutbox.UPSERT, pl.codigo);
            AppExecutors.enUiSiViva(this, () -> {
                Toast.makeText(this, getString(R.string.cambios_guardados_localmente), Toast.LENGTH_SHORT).show();
                enviarCola();
                limpiarTodo();
            });
        });
    }

    private void confirmarEliminacion() {
        if (codigoActual == null) return;
        final int codigo = codigoActual;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.eliminar_producto))
                .setMessage(getString(R.string.esta_accion_no_se_puede_deshacer))
                .setPositiveButton(getString(R.string.eliminar), (dialog, which) -> {
                    AppExecutors.disco().execute(() -> {
                        repositorio().eliminar(codigo);
                        anotarCambio(CatalogOutbox.DELETE, codigo);
                        AppExecutors.enUiSiViva(this, () -> {
                            Toast.makeText(this, getString(R.string.producto_eliminado_localmente), Toast.LENGTH_SHORT).show();
                            enviarCola();
                            limpiarTodo();
                        });
                    });
                })
                .setNegativeButton(getString(R.string.cancelar), null)
                .show();
    }

    /** Anota el cambio en la cola de catálogo (en el mismo hilo que la escritura en Room, antes de cualquier descarga). */
    private void anotarCambio(String tipo, int codigo) {
        final android.content.Context app = getApplicationContext();
        final String tenant = TenantContext.read(SecurePreferencesHelper.getPrinterPrefs(app));
        if (tenant == null) return;   // sin negocio activo no hay base local que editar ni cola que alimentar
        new CatalogOutbox(SecurePreferencesHelper.getPrinterPrefs(app)).add(tenant, tipo, codigo);
    }

    /**
     * Envía la cola al servidor. Si falla (sin red, sin permiso), los cambios quedan en la cola y la sincronización
     * no sobrescribirá el catálogo local hasta que lleguen; si el servidor los rechaza definitivamente, se avisa.
     */
    private void enviarCola() {
        final android.content.Context app = getApplicationContext();
        final String tenant = TenantContext.read(SecurePreferencesHelper.getPrinterPrefs(app));
        if (tenant == null) {
            Toast.makeText(app, TenantContext.NO_TENANT_MESSAGE, Toast.LENGTH_LONG).show();
            return;
        }
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        AppExecutors.disco().execute(() ->
            new CatalogOutboxSender(app, tenant).enviar((pendientes, descartadas) -> main.post(() -> {
                String aviso;
                if (descartadas > 0) {
                    aviso = getResources().getQuantityString(R.plurals.precios_cambios_rechazados, descartadas, descartadas);
                } else if (pendientes > 0) {
                    aviso = getResources().getQuantityString(R.plurals.precios_cambios_pendientes, pendientes, pendientes);
                } else {
                    aviso = getString(R.string.precios_sincronizado_servidor);
                }
                Toast.makeText(app, aviso, pendientes == 0 && descartadas == 0 ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
            })));
    }

    private ProductoRepository repositorio() {
        return new ProductoRepositoryRoom(getApplicationContext());
    }

    private ProductoCatalogo recogerDatosDelFormulario() {
        String idTxt = inputID.getText().toString().trim();
        String nombre = inputNombre.getText().toString().trim();
        String precioTxt = inputPrecio.getText().toString().replace(",", ".").trim();
        String rawCat = inputCategoria.getText().toString().trim();
        String categoria = capitalizeCategory(rawCat);
        String ingredientesTxt = inputIngredientes.getText().toString().trim();

        if (TextUtils.isEmpty(idTxt) || TextUtils.isEmpty(nombre) || TextUtils.isEmpty(precioTxt)) {
            Toast.makeText(this, getString(R.string.faltan_datos_obligatorios_id_nombre_prec), Toast.LENGTH_SHORT).show();
            return null;
        }

        List<String> listaIngredientes = new ArrayList<>();
        if (!ingredientesTxt.isEmpty()) {
            String[] partes = ingredientesTxt.split(",");
            for (String p : partes) {
                listaIngredientes.add(p.trim());
            }
        }

        int codigo = Integer.parseInt(idTxt);
        double precio = Double.parseDouble(precioTxt);

        return new ProductoCatalogo(codigo, nombre, precio, categoria, listaIngredientes, "UD", -1);
    }

    private void activarModoEdicion(boolean modoEdicion) {
        btnCrear.setEnabled(!modoEdicion);
        btnGuardar.setEnabled(modoEdicion);
        btnEliminar.setEnabled(modoEdicion);

        btnCrear.setAlpha(modoEdicion ? 0.3f : 1.0f);
        btnGuardar.setAlpha(modoEdicion ? 1.0f : 0.3f);
    }

    private void limpiarCamposMenosID() {
        inputNombre.setText("");
        inputPrecio.setText("");
        inputIngredientes.setText("");
        inputCategoria.setText("");
        codigoActual = null;
    }

    private void limpiarTodo() {
        inputID.setText("");
        limpiarCamposMenosID();
        activarModoEdicion(false);
    }

    private String capitalizeCategory(String input) {
        if (input == null || input.trim().isEmpty()) return "Otros";
        String[] words = input.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) {
                sb.append(w.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}