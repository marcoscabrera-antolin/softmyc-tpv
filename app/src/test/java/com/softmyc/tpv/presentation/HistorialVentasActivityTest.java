package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;
import com.softmyc.tpv.data.local.VentaLocal;
import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Historial de ventas: filtros por día/mes/año, buscador por contenido o importe, detalle del ticket y cierre de
 * caja (sin ventas no imprime). Las ventas se siembran en la base del negocio de prueba.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HistorialVentasActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Negocio fuera de los catálogos iniciales: su base no se siembra en segundo plano. */
    private static final String NEGOCIO = "historialtest";
    private static final long TIMEOUT_MS = 8000;
    private static final long DOS_DIAS_MS = 2L * 24 * 60 * 60 * 1000;

    private final android.content.Context app = ApplicationProvider.getApplicationContext();
    private final ExecutorService fondo = Executors.newSingleThreadExecutor();
    private ActivityController<HistorialVentasActivity> controlador;
    private Locale localeOriginal;

    @Before
    public void setUp() throws Exception {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(new Locale("es", "ES"));
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO);
        Auth.store(app).save(AuthSession.demo(NEGOCIO, "admin", "ana", System.currentTimeMillis()));
        long ahora = System.currentTimeMillis();
        insertarVenta(ahora - 1000, 10.0, "Efectivo", item("Café", 2));
        insertarVenta(ahora - 2000, 4.5, "Tarjeta", item("Tarta", 1));
        insertarVenta(ahora - DOS_DIAS_MS, 99.0, "Efectivo", item("Antigua", 1));
        controlador = Robolectric.buildActivity(HistorialVentasActivity.class).setup();
        esperarA(() -> lista().getCount() == 2);
    }

    @After
    public void tearDown() {
        fondo.shutdownNow();
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        Locale.setDefault(localeOriginal);
    }

    @Test
    public void por_defecto_muestra_las_ventas_de_hoy_con_su_total() {
        assertEquals("VENTAS DE HOY", texto(R.id.txtTituloFiltro));
        assertEquals("14,50€", texto(R.id.txtTotalCaja));
    }

    @Test
    public void el_filtro_del_mes_y_del_anio_cambia_el_titulo_y_recalcula() throws Exception {
        controlador.get().findViewById(R.id.btnMes).performClick();
        esperarA(() -> "VENTAS DEL MES".equals(texto(R.id.txtTituloFiltro)));
        assertTrue(lista().getCount() >= 2);

        controlador.get().findViewById(R.id.btnAnio).performClick();
        esperarA(() -> "VENTAS DEL AÑO".equals(texto(R.id.txtTituloFiltro)));
        assertTrue(lista().getCount() >= 2);
    }

    @Test
    public void el_buscador_filtra_por_producto_del_ticket() throws Exception {
        ((EditText) controlador.get().findViewById(R.id.etBuscarVentas)).setText("café");
        ShadowLooper.idleMainLooper();

        assertEquals(1, lista().getCount());
        assertEquals("10,00€", texto(R.id.txtTotalCaja));
    }

    @Test
    public void el_buscador_filtra_por_importe_con_la_coma_decimal() throws Exception {
        ((EditText) controlador.get().findViewById(R.id.etBuscarVentas)).setText("4,50");
        ShadowLooper.idleMainLooper();

        assertEquals(1, lista().getCount());
        assertEquals("4,50€", texto(R.id.txtTotalCaja));
    }

    @Test
    public void buscar_algo_que_no_existe_deja_la_lista_vacia_y_total_cero() throws Exception {
        ((EditText) controlador.get().findViewById(R.id.etBuscarVentas)).setText("zzz");
        ShadowLooper.idleMainLooper();

        assertEquals(0, lista().getCount());
        assertEquals("0,00€", texto(R.id.txtTotalCaja));
    }

    @Test
    public void pulsar_una_venta_muestra_el_detalle_de_sus_lineas() throws Exception {
        lista().performItemClick(null, 0, 0);
        ShadowLooper.idleMainLooper();

        assertNotNull(ShadowAlertDialog.getLatestAlertDialog());
        assertTrue(tituloDelDialogo().startsWith("Detalle del Ticket #"));
    }

    @Test
    public void venta_sin_lineas_indica_que_no_hay_detalle() throws Exception {
        insertarVentaCruda(System.currentTimeMillis(), 1.0, "Efectivo", "[]");
        controlador.get().findViewById(R.id.btnDia).performClick();
        esperarA(() -> lista().getCount() == 3);

        // Las ventas se listan de la más reciente a la más antigua: la nueva (sin líneas) es la primera.
        lista().performItemClick(null, 0, 0);
        ShadowLooper.idleMainLooper();

        assertEquals("No hay detalle de la venta.", mensajeDelDialogo());
    }

    @Test
    public void cierre_de_caja_sin_ventas_en_el_periodo_no_imprime() throws Exception {
        ((EditText) controlador.get().findViewById(R.id.etBuscarVentas)).setText("zzz");
        ShadowLooper.idleMainLooper();

        controlador.get().findViewById(R.id.btnImprimirCierre).performClick();

        assertEquals("No hay ventas en este periodo para el cierre", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void cierre_de_caja_con_ventas_lanza_la_impresion_a_la_impresora_configurada() throws Exception {
        SecurePreferencesHelper.getPrinterPrefs(app).edit()
                .putString("impresora_ip", "127.0.0.1")
                .putInt("impresora_puerto", 9)
                .commit();

        controlador.get().findViewById(R.id.btnImprimirCierre).performClick();

        assertEquals("Imprimiendo Cierre en 127.0.0.1", ShadowToast.getTextOfLatestToast());
    }

    private static String tituloDelDialogo() {
        return Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle().toString();
    }

    private static String mensajeDelDialogo() {
        return Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getMessage().toString();
    }

    private ListView lista() {
        return controlador.get().findViewById(R.id.listaVentas);
    }

    private String texto(int id) {
        View vista = controlador.get().findViewById(id);
        assertNotNull("vista " + id, vista);
        if (vista instanceof Button) return ((Button) vista).getText().toString();
        return ((TextView) vista).getText().toString();
    }

    private static PedidoItem item(String nombre, int cantidad) {
        return new PedidoItem(new Producto(1, nombre, 2.0, "Carta", new ArrayList<>(), "UD"), cantidad);
    }

    private void insertarVenta(long fecha, double total, String metodo, PedidoItem... items) throws Exception {
        insertarVentaCruda(fecha, total, metodo, SyncManager.serializarItems(java.util.Arrays.asList(items)));
    }

    private void insertarVentaCruda(long fecha, double total, String metodo, String itemsJson) throws Exception {
        Future<?> alta = fondo.submit(() -> AppDatabase.getDatabaseForTenant(app, NEGOCIO).ventaDao()
                .insert(new VentaLocal(fecha, total, itemsJson, false, metodo)));
        alta.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static void esperarA(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando el historial de ventas");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }
}
