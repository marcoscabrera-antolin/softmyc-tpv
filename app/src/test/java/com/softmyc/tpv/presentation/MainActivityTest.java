package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.auth.FakeApiServer;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;
import com.softmyc.tpv.data.local.VentaLocal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Menú principal: cada botón abre su pantalla, la ayuda de Fase 2 y el selector de negocio solo aparecen para el
 * rol admin (la ocultación es de interfaz; la autorización real la valida el servidor).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String NEGOCIO = "menutest";
    private static final long TIMEOUT_MS = 8000;

    private final android.content.Context app = ApplicationProvider.getApplicationContext();
    private final ExecutorService fondo = Executors.newSingleThreadExecutor();
    private ActivityController<MainActivity> controlador;

    @Before
    public void setUp() {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO);
        SecurePreferencesHelper.getPrinterPrefs(app).edit().putString("user_role", "tpv_operator").commit();
        Auth.store(app).save(AuthSession.demo(NEGOCIO, "tpv_operator", "ana", System.currentTimeMillis()));
        controlador = Robolectric.buildActivity(MainActivity.class).setup();
    }

    @After
    public void tearDown() {
        fondo.shutdownNow();
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
    }

    @Test
    public void hacer_pedido_abre_el_punto_de_venta() {
        pulsar(R.id.btnHacerPedido);

        assertEquals(PedidoActivity.class.getName(), siguiente().getComponent().getClassName());
    }

    @Test
    public void ver_productos_abre_la_carta_de_hamburguesas() {
        pulsar(R.id.btnVerProductos);

        Intent intent = siguiente();
        assertEquals(ListaProductosActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("hamburguesa", intent.getStringExtra("CATEGORIA"));
    }

    @Test
    public void estadisticas_abre_la_pantalla_de_estadisticas() {
        pulsar(R.id.btnEstadisticas);

        assertEquals(EstadisticasActivity.class.getName(), siguiente().getComponent().getClassName());
    }

    @Test
    public void gestion_crud_abre_la_modificacion_de_precios() {
        pulsar(R.id.btnGestionCrud);

        assertEquals(ModificarPreciosActivity.class.getName(), siguiente().getComponent().getClassName());
    }

    @Test
    public void stock_y_arqueo_de_cocina_abren_sus_pantallas() {
        pulsar(R.id.btnStock);
        assertEquals(StockActivity.class.getName(), siguiente().getComponent().getClassName());

        pulsar(R.id.btnArqueoCocina);
        assertEquals(ArqueoCocinaActivity.class.getName(), siguiente().getComponent().getClassName());
    }

    @Test
    public void ajustes_y_ayuda_abren_sus_pantallas() {
        pulsar(R.id.fabAjustes);
        assertEquals(AjustesActivity.class.getName(), siguiente().getComponent().getClassName());

        pulsar(R.id.btnAyuda);
        assertEquals(AyudaActivity.class.getName(), siguiente().getComponent().getClassName());
    }

    @Test
    public void pedidos_web_y_simulador_fase_2_abren_sus_pantallas() {
        pulsar(R.id.btnPedidosWeb);
        assertEquals(PedidosWebActivity.class.getName(), siguiente().getComponent().getClassName());

        pulsar(R.id.btnSimuladorFase2);
        assertEquals(com.softmyc.tpv.presentation.Fase2DemoActivity.class.getName(),
                siguiente().getComponent().getClassName());
    }

    @Test
    public void historial_de_ventas_abre_el_historial() {
        pulsar(R.id.btnHistorialVentas);

        assertEquals(HistorialVentasActivity.class.getName(), siguiente().getComponent().getClassName());
    }

    @Test
    public void el_selector_de_negocio_se_oculta_para_un_operador() {
        View selector = controlador.get().findViewById(R.id.btnCambiarRestaurante);

        assertEquals(View.GONE, selector.getVisibility());
    }

    @Test
    public void el_selector_de_negocio_se_muestra_para_el_admin() {
        SecurePreferencesHelper.getPrinterPrefs(app).edit().putString("user_role", "admin").commit();
        ActivityController<MainActivity> admin = Robolectric.buildActivity(MainActivity.class).setup();

        View selector = admin.get().findViewById(R.id.btnCambiarRestaurante);

        assertEquals(View.VISIBLE, selector.getVisibility());
    }

    @Test
    public void banner_avisa_de_las_ventas_pendientes_y_sincronizar_ahora_las_envia() throws Exception {
        FakeApiServer servidor = new FakeApiServer();
        try {
            servidor.on("/api/tpv/orders/sync", 200, "{\"success\":true,\"rejected\":[]}");
            SecurePreferencesHelper.getPrinterPrefs(app).edit()
                    .putString("backend_base_url", servidor.baseUrl() + "/api/tpv/orders")
                    .putString("backend_api_key", "clave-test")
                    .commit();
            fondo.submit(() -> AppDatabase.getDatabaseForTenant(app, NEGOCIO).ventaDao()
                    .insert(new VentaLocal(System.currentTimeMillis(), 4.5, "[]", false, "Efectivo")))
                    .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            ActivityController<MainActivity> conPendientes = Robolectric.buildActivity(MainActivity.class).setup();

            View fila = filaDelBanner(conPendientes);
            esperarA(() -> fila.getVisibility() == View.VISIBLE);
            TextView aviso = (TextView) ((ViewGroup) fila).getChildAt(0);
            assertTrue(aviso.getText().toString().startsWith("1 venta sin sincronizar"));

            ((Button) ((ViewGroup) fila).getChildAt(1)).performClick();
            esperarA(() -> servidor.countOf("/api/tpv/orders/sync") >= 1);

            assertEquals(1, servidor.countOf("/api/tpv/orders/sync"));
        } finally {
            servidor.close();
        }
    }

    /** La fila del aviso de sincronización es la última hija de la raíz de la pantalla (se añade en código). */
    private static View filaDelBanner(ActivityController<MainActivity> controlador) {
        ViewGroup raiz = (ViewGroup) ((ViewGroup) controlador.get().findViewById(android.R.id.content)).getChildAt(0);
        return raiz.getChildAt(raiz.getChildCount() - 1);
    }

    private static void esperarA(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando el banner de sincronización");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }

    private void pulsar(int id) {
        controlador.get().findViewById(id).performClick();
    }

    private Intent siguiente() {
        Intent intent = Shadows.shadowOf(controlador.get()).getNextStartedActivity();
        assertNotNull("debe abrirse una pantalla", intent);
        return intent;
    }
}
