package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.ProductoLocal;
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
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Arqueo de cocina: solo cuentan las ventas de categorías marcadas como cocina, con el desglose por método de pago;
 * el ticket solo se imprime si hay líneas, y las categorías de cocina se eligen en un diálogo de selección múltiple.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ArqueoCocinaActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Negocio fuera de los catálogos iniciales: su base no se siembra en segundo plano. */
    private static final String NEGOCIO = "arqueotest";
    private static final long TIMEOUT_MS = 8000;

    private final android.content.Context app = ApplicationProvider.getApplicationContext();
    private final ExecutorService fondo = Executors.newSingleThreadExecutor();
    private Locale localeOriginal;

    @Before
    public void setUp() throws Exception {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(new Locale("es", "ES"));
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
        Locale.setDefault(localeOriginal);
    }

    @Test
    public void suma_solo_las_ventas_de_categorias_de_cocina_y_desglosa_el_pago() throws Exception {
        sembrarCatalogo();
        insertarVenta(System.currentTimeMillis(), "Efectivo", linea("Hamburguesa Clásica", "Hamburguesas", 6.0, 2));
        insertarVenta(System.currentTimeMillis(), "Tarjeta", linea("Agua", "Bebidas", 1.0, 1));

        ActivityController<ArqueoCocinaActivity> controlador = arrancar();
        esperarA(() -> texto(controlador, R.id.txtTotalCocina).startsWith("Cocina hoy"));

        assertEquals("Cocina hoy: 12,00 €", texto(controlador, R.id.txtTotalCocina));
        assertTrue(texto(controlador, R.id.txtDesglose).contains("Efectivo 12,00 €"));
        assertTrue(texto(controlador, R.id.txtDesglose).contains("Tarjeta 0,00 €"));
        assertTrue(texto(controlador, R.id.txtCategorias).contains("Hamburguesas"));
        assertEquals(View.GONE, controlador.get().findViewById(R.id.txtArqueoVacio).getVisibility());
    }

    @Test
    public void la_lista_muestra_unidades_nombre_e_importe_de_cada_producto() throws Exception {
        sembrarCatalogo();
        insertarVenta(System.currentTimeMillis(), "Efectivo", linea("Hamburguesa Clásica", "Hamburguesas", 6.0, 2));
        ActivityController<ArqueoCocinaActivity> controlador = arrancar();

        ListView lista = esperarLista(controlador, 1);

        assertEquals("2x  Hamburguesa Clásica   —   12,00 €", lista.getItemAtPosition(0));
    }

    @Test
    public void sin_ventas_de_cocina_muestra_el_aviso_y_total_cero() throws Exception {
        sembrarCatalogo();
        ActivityController<ArqueoCocinaActivity> controlador = arrancar();

        esperarA(() -> controlador.get().findViewById(R.id.txtArqueoVacio).getVisibility() == View.VISIBLE);

        assertEquals("Cocina hoy: 0,00 €", texto(controlador, R.id.txtTotalCocina));
    }

    @Test
    public void cambiar_a_mes_recalcula_con_su_etiqueta() throws Exception {
        sembrarCatalogo();
        ActivityController<ArqueoCocinaActivity> controlador = arrancar();
        esperarA(() -> texto(controlador, R.id.txtTotalCocina).startsWith("Cocina hoy"));

        controlador.get().findViewById(R.id.btnPeriodoMes).performClick();
        esperarA(() -> texto(controlador, R.id.txtTotalCocina).startsWith("Cocina este mes"));

        assertEquals("Cocina este mes: 0,00 €", texto(controlador, R.id.txtTotalCocina));
    }

    @Test
    public void imprimir_sin_lineas_avisa_y_no_manda_nada() throws Exception {
        sembrarCatalogo();
        ActivityController<ArqueoCocinaActivity> controlador = arrancar();
        esperarA(() -> texto(controlador, R.id.txtTotalCocina).startsWith("Cocina hoy"));

        controlador.get().findViewById(R.id.btnImprimirArqueo).performClick();

        assertEquals("No hay ventas de cocina que imprimir", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void imprimir_con_lineas_lanza_el_envio_a_la_impresora_configurada() throws Exception {
        sembrarCatalogo();
        insertarVenta(System.currentTimeMillis(), "Efectivo", linea("Hamburguesa Clásica", "Hamburguesas", 6.0, 1));
        SecurePreferencesHelper.getPrinterPrefs(app).edit()
                .putString("impresora_ip", "127.0.0.1")
                .putInt("impresora_puerto", 9)
                .commit();
        ActivityController<ArqueoCocinaActivity> controlador = arrancar();
        esperarA(() -> texto(controlador, R.id.txtTotalCocina).startsWith("Cocina hoy"));

        controlador.get().findViewById(R.id.btnImprimirArqueo).performClick();

        assertEquals("Imprimiendo arqueo en 127.0.0.1", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void elegir_categorias_sin_catalogo_avisa_que_no_hay_categorias() throws Exception {
        ActivityController<ArqueoCocinaActivity> controlador = arrancar();
        esperarA(() -> texto(controlador, R.id.txtTotalCocina).startsWith("Cocina hoy"));

        controlador.get().findViewById(R.id.btnCategoriasCocina).performClick();

        assertEquals("No hay categorías en el catálogo", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void guardar_categorias_de_cocina_las_persiste_y_recalcula() throws Exception {
        sembrarCatalogo();
        insertarVenta(System.currentTimeMillis(), "Efectivo", linea("Agua", "Bebidas", 1.0, 3));
        ActivityController<ArqueoCocinaActivity> controlador = arrancar();
        esperarA(() -> texto(controlador, R.id.txtTotalCocina).startsWith("Cocina hoy"));
        assertEquals("Cocina hoy: 0,00 €", texto(controlador, R.id.txtTotalCocina));

        controlador.get().findViewById(R.id.btnCategoriasCocina).performClick();
        AlertDialog dialogo = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialogo);
        int indiceBebidas = Arrays.asList(nombresDelDialogo(dialogo)).indexOf("Bebidas");
        dialogo.getListView().performItemClick(null, indiceBebidas, 0);
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        esperarA(() -> texto(controlador, R.id.txtTotalCocina).equals("Cocina hoy: 3,00 €"));

        assertTrue(texto(controlador, R.id.txtCategorias).contains("Bebidas"));
        // Hamburguesas ya era categoría de cocina por defecto y el diálogo la deja marcada.
        assertEquals(new java.util.HashSet<>(Arrays.asList("Hamburguesas", "Bebidas")),
                SecurePreferencesHelper.getPrinterPrefs(app).getStringSet("categorias_cocina", null));
    }

    private static String[] nombresDelDialogo(AlertDialog dialogo) {
        android.widget.ListAdapter adaptador = dialogo.getListView().getAdapter();
        String[] nombres = new String[adaptador.getCount()];
        for (int i = 0; i < nombres.length; i++) {
            nombres[i] = String.valueOf(adaptador.getItem(i));
        }
        return nombres;
    }

    private ActivityController<ArqueoCocinaActivity> arrancar() {
        return Robolectric.buildActivity(ArqueoCocinaActivity.class).setup();
    }

    private static String texto(ActivityController<ArqueoCocinaActivity> controlador, int id) {
        TextView vista = controlador.get().findViewById(id);
        assertNotNull("vista " + id, vista);
        return vista.getText().toString();
    }

    private ListView esperarLista(ActivityController<ArqueoCocinaActivity> controlador, int cantidad)
            throws InterruptedException {
        esperarA(() -> {
            ListView lista = controlador.get().findViewById(R.id.listaProductosCocina);
            return lista.getAdapter() != null && lista.getCount() == cantidad;
        });
        return controlador.get().findViewById(R.id.listaProductosCocina);
    }

    private void sembrarCatalogo() throws Exception {
        fondo.submit(() -> AppDatabase.getDatabaseForTenant(app, NEGOCIO).productoDao().insertAll(Arrays.asList(
                new ProductoLocal(1, "Hamburguesa Clásica", 6.0, "Hamburguesas", new ArrayList<>()),
                new ProductoLocal(2, "Agua", 1.0, "Bebidas", new ArrayList<>())))).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static PedidoItem linea(String nombre, String categoria, double precio, int cantidad) {
        return new PedidoItem(new Producto(1, nombre, precio, categoria, new ArrayList<>(), "UD"), cantidad);
    }

    private void insertarVenta(long fecha, String metodo, PedidoItem... items) throws Exception {
        String json = SyncManager.serializarItems(Arrays.asList(items));
        fondo.submit(() -> AppDatabase.getDatabaseForTenant(app, NEGOCIO).ventaDao()
                .insert(new VentaLocal(fecha, items[0].getCantidad() * items[0].getProducto().getPrecio(), json, false, metodo)))
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static void esperarA(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando el arqueo de cocina");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }
}
