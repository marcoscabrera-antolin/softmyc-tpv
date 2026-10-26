package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.widget.Button;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.auth.FakeApiServer;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;
import com.softmyc.tpv.data.sync.CatalogOutbox;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Alta, edición y baja de productos del catálogo local. Cada cambio se anota en la cola de catálogo del negocio
 * y se intenta enviar al servidor de prueba ({@link FakeApiServer}); el aviso depende de si el envío llega.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ModificarPreciosActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Negocio fuera de los catálogos iniciales: su base no se siembra en segundo plano. */
    private static final String NEGOCIO = "preciostest";
    private static final long TIMEOUT_MS = 8000;
    private static final int CODIGO_EXISTENTE = 42;

    private final android.content.Context app = ApplicationProvider.getApplicationContext();
    private final ExecutorService fondo = Executors.newSingleThreadExecutor();
    private FakeApiServer server;
    private ActivityController<ModificarPreciosActivity> controlador;

    @Before
    public void setUp() throws Exception {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        server = new FakeApiServer();
        server.on("/api/tpv/products", 200, "{}");
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear()
                .putString("backend_base_url", server.baseUrl() + "/api/tpv/orders")
                .putString("backend_api_key", "clave-test")
                .commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO);
        Auth.store(app).save(AuthSession.demo(NEGOCIO, "admin", "ana", System.currentTimeMillis()));
        insertarProductoExistente();
        controlador = Robolectric.buildActivity(ModificarPreciosActivity.class).setup();
    }

    @After
    public void tearDown() {
        server.close();
        fondo.shutdownNow();
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
    }

    @Test
    public void id_que_no_es_numero_avisa_y_no_busca() {
        campo(R.id.inputID).setText("abc");

        controlador.get().findViewById(R.id.btnBuscar).performClick();

        assertEquals("El ID debe ser un número", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void id_libre_habilita_solo_la_creacion() throws Exception {
        campo(R.id.inputID).setText("999");
        controlador.get().findViewById(R.id.btnBuscar).performClick();
        esperarA(() -> "ID libre. Puedes crear uno nuevo.".equals(ShadowToast.getTextOfLatestToast()));

        assertTrue(boton(R.id.btnCrear).isEnabled());
        assertFalse(boton(R.id.btnGuardar).isEnabled());
        assertFalse(boton(R.id.btnEliminar).isEnabled());
    }

    @Test
    public void buscar_un_producto_existente_rellena_el_formulario_con_sus_datos() throws Exception {
        campo(R.id.inputID).setText(String.valueOf(CODIGO_EXISTENTE));

        controlador.get().findViewById(R.id.btnBuscar).performClick();
        esperarA(() -> "Producto cargado".equals(ShadowToast.getTextOfLatestToast()));

        assertEquals("Pan", campo(R.id.inputNombre).getText().toString());
        assertEquals("1.25", campo(R.id.inputPrecio).getText().toString());
        assertEquals("Panadería", campo(R.id.inputCategoria).getText().toString());
        assertEquals("harina, agua", campo(R.id.inputIngredientes).getText().toString());
        assertTrue(boton(R.id.btnGuardar).isEnabled());
        assertFalse(boton(R.id.btnCrear).isEnabled());
    }

    @Test
    public void crear_guarda_el_producto_normaliza_precio_y_categoria_y_lo_encola() throws Exception {
        rellenar("77", "Rosquilla", "2,50", "dulces  de  horno", "azúcar, , harina");

        boton(R.id.btnCrear).performClick();
        esperarA(() -> productoEnBd(77) != null);
        esperarA(() -> "🌐 Sincronizado con el servidor".equals(ShadowToast.getTextOfLatestToast()));

        ProductoLocal creado = productoEnBd(77);
        assertEquals("Rosquilla", creado.nombre);
        assertEquals(2.5, creado.precio, 1e-9);
        assertEquals("Dulces De Horno", creado.categoria);
        assertEquals(Arrays.asList("azúcar", "", "harina"), creado.ingredientes);
        assertEquals("el alta sale de la cola tras el envío", 0, pendientesDeEnviar());
        assertEquals("", campo(R.id.inputID).getText().toString());
    }

    @Test
    public void crear_sin_categoria_la_marca_como_otros() throws Exception {
        rellenar("78", "Agua", "1", "   ", "");

        boton(R.id.btnCrear).performClick();
        esperarA(() -> productoEnBd(78) != null);

        assertEquals("Otros", productoEnBd(78).categoria);
    }

    @Test
    public void crear_sin_nombre_no_guarda_nada_y_avisa_de_los_datos_obligatorios() throws Exception {
        rellenar("79", "", "2", "Bebidas", "");

        boton(R.id.btnCrear).performClick();

        assertEquals("Faltan datos obligatorios (ID, Nombre, Precio)", ShadowToast.getTextOfLatestToast());
        assertNull(productoEnBd(79));
    }

    @Test
    public void guardar_cambios_actualiza_el_precio_del_producto_buscado() throws Exception {
        buscarExistente();

        campo(R.id.inputPrecio).setText("3");
        boton(R.id.btnGuardar).performClick();
        esperarA(() -> productoEnBd(CODIGO_EXISTENTE).precio == 3.0);

        assertEquals(3.0, productoEnBd(CODIGO_EXISTENTE).precio, 1e-9);
        esperarA(() -> "🌐 Sincronizado con el servidor".equals(ShadowToast.getTextOfLatestToast()));
    }

    @Test
    public void si_el_servidor_no_responde_el_cambio_queda_pendiente_en_la_cola() throws Exception {
        server.close();
        rellenar("80", "Donut", "1.80", "Dulces", "");

        boton(R.id.btnCrear).performClick();
        esperarA(() -> ShadowToast.getTextOfLatestToast() != null
                && ShadowToast.getTextOfLatestToast().startsWith("⚠️ Guardado en la tablet"));

        assertEquals("Guardado en la tablet; 1 cambio de catálogo pendiente de enviar",
                ShadowToast.getTextOfLatestToast().replace("⚠️ ", ""));
        assertEquals(1, pendientesDeEnviar());
    }

    @Test
    public void eliminar_pide_confirmacion_y_al_aceptar_borra_y_encola_la_baja() throws Exception {
        server.on("/api/tpv/products/" + CODIGO_EXISTENTE, 200, "{}");
        buscarExistente();

        boton(R.id.btnEliminar).performClick();
        assertNotNull("debe pedir confirmación", ShadowAlertDialog.getLatestAlertDialog());
        ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        esperarA(() -> productoEnBd(CODIGO_EXISTENTE) == null);

        assertNull(productoEnBd(CODIGO_EXISTENTE));
        // La baja llega al servidor (los avisos de toast se pisan entre sí: se comprueba la petición, no el texto).
        esperarA(() -> server.countOf("/api/tpv/products/" + CODIGO_EXISTENTE) == 1);
    }

    @Test
    public void eliminar_cancelado_conserva_el_producto() throws Exception {
        buscarExistente();

        boton(R.id.btnEliminar).performClick();
        ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();

        assertEquals("Pan", productoEnBd(CODIGO_EXISTENTE).nombre);
    }

    @Test
    public void guardar_sin_producto_cargado_no_hace_nada() throws Exception {
        rellenar("81", "Algo", "1", "Otros", "");

        boton(R.id.btnGuardar).performClick();
        ShadowLooper.idleMainLooper();

        assertNull(productoEnBd(81));
    }

    private void buscarExistente() throws Exception {
        campo(R.id.inputID).setText(String.valueOf(CODIGO_EXISTENTE));
        controlador.get().findViewById(R.id.btnBuscar).performClick();
        esperarA(() -> "Producto cargado".equals(ShadowToast.getTextOfLatestToast()));
    }

    private void rellenar(String id, String nombre, String precio, String categoria, String ingredientes) {
        campo(R.id.inputID).setText(id);
        campo(R.id.inputNombre).setText(nombre);
        campo(R.id.inputPrecio).setText(precio);
        campo(R.id.inputCategoria).setText(categoria);
        campo(R.id.inputIngredientes).setText(ingredientes);
    }

    private EditText campo(int id) {
        return (EditText) controlador.get().findViewById(id);
    }

    private Button boton(int id) {
        return (Button) controlador.get().findViewById(id);
    }

    private void insertarProductoExistente() throws Exception {
        ProductoLocal pan = new ProductoLocal(CODIGO_EXISTENTE, "Pan", 1.25, "Panadería",
                new ArrayList<>(Arrays.asList("harina", "agua")));
        fondo.submit(() -> AppDatabase.getDatabaseForTenant(app, NEGOCIO).productoDao().insert(pan))
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private ProductoLocal productoEnBd(int codigo) {
        try {
            Future<ProductoLocal> consulta = fondo.submit(() ->
                    AppDatabase.getDatabaseForTenant(app, NEGOCIO).productoDao().getByCodigo(codigo));
            return consulta.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo consultar el producto " + codigo, e);
        }
    }

    private int pendientesDeEnviar() {
        return new CatalogOutbox(SecurePreferencesHelper.getPrinterPrefs(app)).pending(NEGOCIO).size();
    }

    /** Procesa la cola del hilo principal hasta que se cumpla la condición (las escrituras llegan desde el hilo de disco). */
    private static void esperarA(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando la acción de la pantalla");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }
}
