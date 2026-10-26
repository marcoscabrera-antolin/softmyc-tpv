package com.softmyc.tpv.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Resumen semanal de ventas: agregados de los últimos 7 días (total, nº de pedidos, producto estrella, desglose),
 * cierre del diálogo, no mostrarlo si la Activity se cierra y el correo con el resumen.
 * Las consultas a Room corren en segundo plano; el test espera a que el diálogo se pinte.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ResumenSemanalCorreoTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final long TIMEOUT_MS = 5000;
    private static final long DIA_MS = 24L * 60 * 60 * 1000;
    /** Negocio fuera de los catálogos iniciales: su base no se siembra durante el test. */
    private static final String NEGOCIO = "resumentest";

    private android.content.Context app;
    private Locale localeOriginal;
    private ExecutorService fondo;

    /** Activity con el tema Material que exige el diálogo del resumen. */
    public static class PantallaResumen extends AppCompatActivity {
        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            setTheme(R.style.Theme_MyApplication);
            super.onCreate(savedInstanceState);
        }
    }

    @Before
    public void setUp() {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(new Locale("es", "ES"));
        app = ApplicationProvider.getApplicationContext();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO);
        fondo = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() throws Exception {
        fondo.shutdownNow();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        Locale.setDefault(localeOriginal);
    }

    @Test
    public void resumen_suma_las_ventas_de_la_semana_y_elige_el_producto_mas_vendido() throws Exception {
        insertarVenta(System.currentTimeMillis(), 10.0, item("Café", 2), item("Tarta", 1));
        insertarVenta(System.currentTimeMillis(), 5.0, item("Café", 1));
        ActivityController<PantallaResumen> controlador = arrancarActividad();

        AlertDialog dialogo = abrirResumen(controlador.get());

        assertEquals("15,00 €", texto(dialogo, R.id.txtTotalSemanales));
        assertEquals("2 pedidos", texto(dialogo, R.id.txtPedidosSemanales));
        assertEquals("Café (3 uds)", texto(dialogo, R.id.txtProductoEstrella));
    }

    @Test
    public void el_desglose_tiene_una_fila_por_dia_y_la_de_hoy_recoge_la_venta() throws Exception {
        insertarVenta(System.currentTimeMillis(), 7.25, item("Bocata", 1));
        ActivityController<PantallaResumen> controlador = arrancarActividad();

        AlertDialog dialogo = abrirResumen(controlador.get());

        LinearLayout dias = dialogo.findViewById(R.id.containerDias);
        assertEquals(7, dias.getChildCount());
        TextView totalHoy = dias.getChildAt(6).findViewById(R.id.txtDiaTotal);
        assertEquals("7,25 €", totalHoy.getText().toString());
        assertEquals("0,00 €", ((TextView) dias.getChildAt(0).findViewById(R.id.txtDiaTotal)).getText().toString());
    }

    @Test
    public void sin_ventas_en_la_semana_muestra_cero_pedidos_y_ninguno() throws Exception {
        ActivityController<PantallaResumen> controlador = arrancarActividad();

        AlertDialog dialogo = abrirResumen(controlador.get());

        assertEquals("0,00 €", texto(dialogo, R.id.txtTotalSemanales));
        assertEquals("0 pedidos", texto(dialogo, R.id.txtPedidosSemanales));
        assertEquals("Ninguno", texto(dialogo, R.id.txtProductoEstrella));
    }

    @Test
    public void un_unico_pedido_usa_el_singular() throws Exception {
        insertarVenta(System.currentTimeMillis(), 3.0, item("Agua", 1));
        ActivityController<PantallaResumen> controlador = arrancarActividad();

        AlertDialog dialogo = abrirResumen(controlador.get());

        assertEquals("1 pedido", texto(dialogo, R.id.txtPedidosSemanales));
    }

    @Test
    public void ventas_anteriores_a_la_ventana_de_siete_dias_no_cuentan() throws Exception {
        insertarVenta(System.currentTimeMillis() - 10 * DIA_MS, 99.0, item("Antigua", 1));
        ActivityController<PantallaResumen> controlador = arrancarActividad();

        AlertDialog dialogo = abrirResumen(controlador.get());

        assertEquals("0,00 €", texto(dialogo, R.id.txtTotalSemanales));
        assertEquals("0 pedidos", texto(dialogo, R.id.txtPedidosSemanales));
    }

    @Test
    public void una_venta_con_json_corrupto_cuenta_en_el_total_pero_no_en_el_producto_estrella() throws Exception {
        insertarVentaCruda(System.currentTimeMillis(), 4.0, "{corrupto");
        ActivityController<PantallaResumen> controlador = arrancarActividad();

        AlertDialog dialogo = abrirResumen(controlador.get());

        assertEquals("4,00 €", texto(dialogo, R.id.txtTotalSemanales));
        assertEquals("1 pedido", texto(dialogo, R.id.txtPedidosSemanales));
        assertEquals("Ninguno", texto(dialogo, R.id.txtProductoEstrella));
    }

    @Test
    public void el_boton_cerrar_quita_el_dialogo() throws Exception {
        ActivityController<PantallaResumen> controlador = arrancarActividad();
        AlertDialog dialogo = abrirResumen(controlador.get());

        dialogo.findViewById(R.id.btnCerrarResumen).performClick();

        assertFalse(dialogo.isShowing());
    }

    @Test
    public void si_la_activity_se_esta_cerrando_no_se_abre_el_resumen() {
        ActivityController<PantallaResumen> controlador = arrancarActividad();
        Activity activity = controlador.get();
        activity.finish();

        ResumenSemanalCorreo.enviarResumen(activity);

        assertNull(ShadowDialog.getLatestDialog());
    }

    @Test
    public void el_boton_de_correo_abre_el_selector_con_asunto_y_desglose() throws Exception {
        insertarVenta(System.currentTimeMillis(), 6.0, item("Café", 2));
        ActivityController<PantallaResumen> controlador = arrancarActividad();
        AlertDialog dialogo = abrirResumen(controlador.get());

        dialogo.findViewById(R.id.btnEnviarResumenEmail).performClick();

        Intent selector = Shadows.shadowOf(controlador.get()).getNextStartedActivity();
        assertNotNull("debe abrirse el selector de correo", selector);
        assertEquals(Intent.ACTION_CHOOSER, selector.getAction());
        Intent correo = selector.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull(correo);
        assertEquals(Intent.ACTION_SENDTO, correo.getAction());
        assertEquals("[SoftMYC TPV] Resumen de Ventas Semanal", correo.getStringExtra(Intent.EXTRA_SUBJECT));
        String cuerpo = correo.getStringExtra(Intent.EXTRA_TEXT);
        assertTrue(cuerpo.contains("Facturación Total: 6,00 €"));
        assertTrue(cuerpo.contains("Producto Estrella: Café (2 uds)"));
    }

    private ActivityController<PantallaResumen> arrancarActividad() {
        return Robolectric.buildActivity(PantallaResumen.class).create().start().resume().visible();
    }

    /** Abre el resumen y espera a que las consultas terminen y el diálogo muestre los datos. */
    private AlertDialog abrirResumen(Activity activity) throws InterruptedException {
        ResumenSemanalCorreo.enviarResumen(activity);
        AlertDialog dialogo = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull("el diálogo del resumen debe mostrarse", dialogo);
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (dialogo.findViewById(R.id.layoutCarga).getVisibility() != View.GONE) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando los datos del resumen");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
        return dialogo;
    }

    private static String texto(AlertDialog dialogo, int id) {
        return ((TextView) dialogo.findViewById(id)).getText().toString();
    }

    private static PedidoItem item(String nombre, int cantidad) {
        return new PedidoItem(new Producto(1, nombre, 2.0, "Carta", new ArrayList<>(), "UD"), cantidad);
    }

    private void insertarVenta(long fecha, double total, PedidoItem... items) throws Exception {
        insertarVentaCruda(fecha, total, SyncManager.serializarItems(Arrays.asList(items)));
    }

    private void insertarVentaCruda(long fecha, double total, String itemsJson) throws Exception {
        Future<?> alta = fondo.submit(() -> AppDatabase.getDatabase(app).ventaDao()
                .insert(new VentaLocal(fecha, total, itemsJson, false, "Efectivo")));
        alta.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
}
