package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.view.View;
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
import com.softmyc.tpv.presentation.custom.SalesChartView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Estadísticas del mes: ranking de productos por unidades vendidas (con medallas en los tres primeros) y el gráfico
 * con un punto por día del mes, sumando solo las ventas del mes en curso.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EstadisticasActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Negocio fuera de los catálogos iniciales: su base no se siembra en segundo plano. */
    private static final String NEGOCIO = "estadisticastest";
    private static final long TIMEOUT_MS = 8000;
    private static final long CUARENTA_DIAS_MS = 40L * 24 * 60 * 60 * 1000;

    private final android.content.Context app = ApplicationProvider.getApplicationContext();
    private final ExecutorService fondo = Executors.newSingleThreadExecutor();

    @Before
    public void setUp() {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO);
        Auth.store(app).save(AuthSession.demo(NEGOCIO, "admin", "ana", System.currentTimeMillis()));
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
    public void ranking_suma_unidades_por_producto_del_mes_y_pone_medallas() throws Exception {
        long ahora = System.currentTimeMillis();
        insertarVenta(ahora, 10.0, item("Café", 3), item("Tarta", 1));
        insertarVenta(ahora, 5.0, item("Café", 1), item("Bocata", 2));
        ActivityController<EstadisticasActivity> controlador = arrancar();

        ListView ranking = esperarRanking(controlador, 3);

        assertEquals("Café", ((Map.Entry<?, ?>) ranking.getItemAtPosition(0)).getKey());
        assertEquals(4L, ((Map.Entry<?, ?>) ranking.getItemAtPosition(0)).getValue());
        assertEquals("Bocata", ((Map.Entry<?, ?>) ranking.getItemAtPosition(1)).getKey());
        assertEquals("Tarta", ((Map.Entry<?, ?>) ranking.getItemAtPosition(2)).getKey());
        assertEquals("🥇", textoFila(ranking, 0, R.id.txtPosicion));
        assertEquals("🥈", textoFila(ranking, 1, R.id.txtPosicion));
        assertEquals("🥉", textoFila(ranking, 2, R.id.txtPosicion));
        assertEquals("4 uds", textoFila(ranking, 0, R.id.txtVentas));
    }

    @Test
    public void el_grafico_tiene_un_punto_por_dia_del_mes_y_suma_solo_las_ventas_del_mes() throws Exception {
        long ahora = System.currentTimeMillis();
        insertarVenta(ahora, 10.0, item("Café", 1));
        insertarVenta(ahora - CUARENTA_DIAS_MS, 99.0, item("Antigua", 1));
        ActivityController<EstadisticasActivity> controlador = arrancar();
        esperarA(() -> valores(controlador).stream().mapToDouble(Double::doubleValue).sum() > 0);

        List<Double> valores = valores(controlador);

        assertEquals(Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH), valores.size());
        assertEquals(10.0, valores.stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    public void sin_ventas_el_ranking_esta_vacio_y_el_grafico_a_cero() throws Exception {
        ActivityController<EstadisticasActivity> controlador = arrancar();
        esperarRanking(controlador, 0);

        assertEquals(0, ((ListView) controlador.get().findViewById(R.id.listaRanking)).getCount());
        assertEquals(0.0, valores(controlador).stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    public void venta_con_json_corrupto_cuenta_en_el_grafico_pero_no_en_el_ranking() throws Exception {
        insertarVentaCruda(System.currentTimeMillis(), 7.0, "{corrupto");
        ActivityController<EstadisticasActivity> controlador = arrancar();
        esperarA(() -> valores(controlador).stream().mapToDouble(Double::doubleValue).sum() > 0);

        assertEquals(7.0, valores(controlador).stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertEquals(0, ((ListView) controlador.get().findViewById(R.id.listaRanking)).getCount());
    }

    private ActivityController<EstadisticasActivity> arrancar() {
        return Robolectric.buildActivity(EstadisticasActivity.class).setup();
    }

    private static List<Double> valores(ActivityController<EstadisticasActivity> controlador) {
        SalesChartView grafico = controlador.get().findViewById(R.id.salesChartView);
        assertNotNull("gráfico no encontrado", grafico);
        try {
            Field campo = SalesChartView.class.getDeclaredField("values");
            campo.setAccessible(true);
            return new ArrayList<>((List<Double>) campo.get(grafico));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("SalesChartView ya no tiene values: actualiza el test", e);
        }
    }

    private ListView esperarRanking(ActivityController<EstadisticasActivity> controlador, int cantidad)
            throws InterruptedException {
        esperarA(() -> {
            ListView lista = controlador.get().findViewById(R.id.listaRanking);
            return lista.getAdapter() != null && lista.getCount() == cantidad;
        });
        return controlador.get().findViewById(R.id.listaRanking);
    }

    private static String textoFila(ListView lista, int posicion, int id) {
        View fila = lista.getAdapter().getView(posicion, null, lista);
        return ((TextView) fila.findViewById(id)).getText().toString();
    }

    private static PedidoItem item(String nombre, int cantidad) {
        return new PedidoItem(new Producto(1, nombre, 2.0, "Carta", new ArrayList<>(), "UD"), cantidad);
    }

    private void insertarVenta(long fecha, double total, PedidoItem... items) throws Exception {
        insertarVentaCruda(fecha, total, SyncManager.serializarItems(Arrays.asList(items)));
    }

    private void insertarVentaCruda(long fecha, double total, String json) throws Exception {
        fondo.submit(() -> AppDatabase.getDatabaseForTenant(app, NEGOCIO).ventaDao()
                .insert(new VentaLocal(fecha, total, json, false, "Efectivo"))).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static void esperarA(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando las estadísticas");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }
}
