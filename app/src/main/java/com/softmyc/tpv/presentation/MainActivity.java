package com.softmyc.tpv.presentation;

import com.softmyc.tpv.data.printer.TicketGenerator;
import com.softmyc.tpv.data.printer.PrinterHelper;
import com.softmyc.tpv.R;
import com.softmyc.tpv.util.ResumenSemanalCorreo;
import com.softmyc.tpv.presentation.util.VentasNavegacionHelper;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.sync.SyncBanner;
import com.softmyc.tpv.data.sync.SyncScheduler;
import com.softmyc.tpv.data.sync.SyncStatusStore;
import com.softmyc.tpv.data.sync.VentaUploader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Actividad principal de la aplicación. Sirve como menú inicial desde el cual
 * el usuario puede navegar a las distintas funcionalidades:
 *  - Hacer un pedido (PedidoActivity)
 *  - Ver productos disponibles (ListaProductosActivity)
 *  - Ver estadísticas (EstadisticasActivity)
 *  - Gestión CRUD de precios (ModificarPreciosActivity)
 *  - Ajustes de impresora y carta CSV (AjustesActivity)
 *  - Enviar resumen semanal por correo
 */
public class MainActivity extends BaseActivity {

    private View btnHacerPedido, btnVerProductos, btnEstadisticas, btnModificarPrecios, btnResumenSemanal, btnHistorialVentas, btnAyuda, fabAjustes, btnPedidosWeb;
    private com.google.android.material.button.MaterialButton btnCambiarRestaurante;
    private final ExecutorService hiloSync = Executors.newSingleThreadExecutor();
    private TextView tvAvisoSync;
    private Button btnSincronizarAhora;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        programarSincronizacionPeriodica();
        configurarAvisoSincronizacion();

        // 1. Vincular componentes del layout
        btnHacerPedido        = findViewById(R.id.btnHacerPedido);
        btnVerProductos       = findViewById(R.id.btnVerProductos);
        btnEstadisticas       = findViewById(R.id.btnEstadisticas);
        btnModificarPrecios   = findViewById(R.id.btnGestionCrud);
        btnResumenSemanal     = findViewById(R.id.btnEnviarResumen);
        btnHistorialVentas    = findViewById(R.id.btnHistorialVentas);
        btnPedidosWeb         = findViewById(R.id.btnPedidosWeb);
        btnAyuda              = findViewById(R.id.btnAyuda);
        fabAjustes            = findViewById(R.id.fabAjustes);
        btnCambiarRestaurante = findViewById(R.id.btnCambiarRestaurante);

        // 2. Configurar listeners de navegación y multi-tenant admin
        configurarBotonAdminTenant();

        if (btnPedidosWeb != null) {
            btnPedidosWeb.setOnClickListener(v ->
                    startActivity(new Intent(this, PedidosWebActivity.class)));
        }

        btnHistorialVentas.setOnClickListener(v ->
                VentasNavegacionHelper.navegarAlHistorial(this));

        btnHacerPedido.setOnClickListener(v ->
                startActivity(new Intent(this, PedidoActivity.class)));

        btnVerProductos.setOnClickListener(v -> {
            Intent intent = new Intent(this, ListaProductosActivity.class);
            intent.putExtra("CATEGORIA", "hamburguesa");
            startActivity(intent);
        });

        btnEstadisticas.setOnClickListener(v ->
                startActivity(new Intent(this, EstadisticasActivity.class)));

        btnModificarPrecios.setOnClickListener(v ->
                startActivity(new Intent(this, ModificarPreciosActivity.class)));

        findViewById(R.id.btnStock).setOnClickListener(v ->
                startActivity(new Intent(this, StockActivity.class)));

        findViewById(R.id.btnArqueoCocina).setOnClickListener(v ->
                startActivity(new Intent(this, ArqueoCocinaActivity.class)));

        btnResumenSemanal.setOnClickListener(v ->
                ResumenSemanalCorreo.enviarResumen(this));

        fabAjustes.setOnClickListener(v ->
                startActivity(new Intent(this, AjustesActivity.class)));

        btnAyuda.setOnClickListener(v ->
                startActivity(new Intent(this, AyudaActivity.class)));

        View btnSimuladorFase2 = findViewById(R.id.btnSimuladorFase2);
        if (btnSimuladorFase2 != null) {
            btnSimuladorFase2.setOnClickListener(v ->
                    startActivity(new Intent(this, Fase2DemoActivity.class)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refrescarAvisoSync();
    }

    @Override
    protected void onDestroy() {
        hiloSync.shutdown();
        super.onDestroy();
    }

    /** El reintento periódico solo tiene sentido con una sesión real; se reprograma al abrir la app (idempotente). */
    private void programarSincronizacionPeriodica() {
        AuthSession sesion = Auth.store(this).load();
        if (sesion != null && !sesion.demo) SyncScheduler.programar(this);
    }

    /** Banner discreto en la parte inferior: "N ventas sin sincronizar · motivo", visible solo con pendientes. */
    private void configurarAvisoSincronizacion() {
        ConstraintLayout raiz = (ConstraintLayout) ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        int margen = Math.round(12 * getResources().getDisplayMetrics().density);

        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        fila.setBackgroundColor(Color.argb(230, 50, 50, 50));
        fila.setPadding(margen, margen, margen, margen);
        fila.setVisibility(View.GONE);

        tvAvisoSync = new TextView(this);
        tvAvisoSync.setTextColor(Color.WHITE);
        tvAvisoSync.setTextSize(14);
        fila.addView(tvAvisoSync, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnSincronizarAhora = new Button(this);
        btnSincronizarAhora.setText(getString(R.string.sincronizar_ahora));
        btnSincronizarAhora.setAllCaps(false);
        btnSincronizarAhora.setOnClickListener(v -> sincronizarAhora());
        fila.addView(btnSincronizarAhora);

        ConstraintLayout.LayoutParams posicion = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        posicion.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        posicion.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        posicion.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        raiz.addView(fila, posicion);
    }

    private void refrescarAvisoSync() {
        if (tvAvisoSync == null) return;
        hiloSync.execute(() -> {
            SyncBanner.Aviso aviso = SyncBanner.describir(pendientesSeguro(), SyncStatusStore.of(this).leer());
            runOnUiThread(() -> mostrarAvisoSync(aviso));
        });
    }

    private int pendientesSeguro() {
        try {
            return VentaUploader.pendientes(this);
        } catch (RuntimeException e) {
            // Sin negocio activo no hay base que consultar: no hay nada que avisar.
            Log.w("MainActivity", "No se pudieron contar las ventas pendientes: " + e.getMessage());
            return 0;
        }
    }

    private void mostrarAvisoSync(SyncBanner.Aviso aviso) {
        if (isFinishing() || tvAvisoSync == null) return;
        tvAvisoSync.setText(aviso.texto());
        ((View) tvAvisoSync.getParent()).setVisibility(aviso.visible() ? View.VISIBLE : View.GONE);
    }

    private void sincronizarAhora() {
        btnSincronizarAhora.setEnabled(false);
        VentaUploader subida = new VentaUploader(this, hiloSync, rechazadas -> runOnUiThread(() ->
                Toast.makeText(this, getResources().getQuantityString(R.plurals.ventas_rechazadas_servidor, rechazadas, rechazadas), Toast.LENGTH_LONG).show()));
        subida.subirPendientes(() -> runOnUiThread(() -> {
            if (isFinishing()) return;
            btnSincronizarAhora.setEnabled(true);
            refrescarAvisoSync();
        }));
    }

    /**
     * Muestra u oculta el selector de negocio según el rol guardado en las preferencias.
     * Ojo: esta ocultación es solo de cliente (UX). El rol y el negocio locales pueden manipularse;
     * la autorización real la valida el backend en cada petición (sesión y negocio), no esta pantalla.
     */
    private void configurarBotonAdminTenant() {
        if (btnCambiarRestaurante == null) return;

        SharedPreferences prefs = com.softmyc.tpv.data.SecurePreferencesHelper.getPrinterPrefs(this);
        String role = prefs.getString("user_role", "tpv_operator");
        String currentRestId = prefs.getString("restaurant_id", "lanube");

        if ("admin".equalsIgnoreCase(role)) {
            btnCambiarRestaurante.setVisibility(View.VISIBLE);
            String nombreNegocio = com.softmyc.tpv.data.local.TenantCatalogSeeder.getBusinessName(currentRestId);
            String icon = com.softmyc.tpv.data.local.TenantCatalogSeeder.getBusinessIcon(currentRestId);
            btnCambiarRestaurante.setText(icon + " " + nombreNegocio);

            btnCambiarRestaurante.setOnClickListener(v -> {
                AdminTenantSelectorDialog dialog = AdminTenantSelectorDialog.newInstance();
                dialog.setOnTenantSwitchedListener((newId, newName) -> {
                    configurarBotonAdminTenant();
                    recreate();
                });
                dialog.show(getSupportFragmentManager(), "AdminTenantSelectorMain");
            });
        } else {
            btnCambiarRestaurante.setVisibility(View.GONE);
        }
    }
}