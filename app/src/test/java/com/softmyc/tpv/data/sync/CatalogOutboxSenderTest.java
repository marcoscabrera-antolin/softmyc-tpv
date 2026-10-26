package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.FakeApiServer;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.json.JSONObject;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Envío de la cola de catálogo al servidor real de pruebas ({@link FakeApiServer}). Las operaciones se
 * encolan en una base de negocio en disco; el envío se lanza desde un hilo de fondo porque Room no debe
 * consultarse en el hilo principal.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CatalogOutboxSenderTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Negocio fuera de los catálogos iniciales: su base no se siembra en segundo plano durante el test. */
    private static final String TENANT = "outboxtest";
    private static final int CODIGO = 987001;
    private static final long TIMEOUT_SEGUNDOS = 10;

    private final Context context = ApplicationProvider.getApplicationContext();
    private final ExecutorService fondo = Executors.newSingleThreadExecutor();
    private FakeApiServer server;
    private CatalogOutbox outbox;

    /** Resultado capturado del envío. */
    private static final class Final {
        final CountDownLatch hecho = new CountDownLatch(1);
        volatile int pendientes = -1;
        volatile int descartadas = -1;

        void registrar(int pendientes, int descartadas) {
            this.pendientes = pendientes;
            this.descartadas = descartadas;
            hecho.countDown();
        }
    }

    @Before
    public void setUp() throws Exception {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(context);
        server = new FakeApiServer();
        SecurePreferencesHelper.getPrinterPrefs(context).edit()
                .clear()
                .putString("backend_base_url", server.baseUrl() + "/api/tpv/orders")
                .putString("backend_api_key", "clave-test")
                .commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(context), TENANT);
        outbox = new CatalogOutbox(SecurePreferencesHelper.getPrinterPrefs(context));
        insertarProductoLocal(CODIGO, "Manzana");
    }

    @After
    public void tearDown() {
        server.close();
        fondo.shutdownNow();
        SecurePreferencesHelper.getPrinterPrefs(context).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(context);
    }

    @Test
    public void colaVaciaNoHaceNinguna_peticion_y_termina_sin_pendientes() throws Exception {
        Final resultado = enviar();

        assertEquals(0, resultado.pendientes);
        assertEquals(0, resultado.descartadas);
        assertEquals(0, server.requests().size());
    }

    @Test
    public void upsert_aceptado_por_el_servidor_sale_de_la_cola() throws Exception {
        server.on("/api/tpv/products", 200, "{}");
        outbox.add(TENANT, CatalogOutbox.UPSERT, CODIGO);

        Final resultado = enviar();

        assertEquals(0, resultado.pendientes);
        assertEquals(0, resultado.descartadas);
        assertTrue(outbox.pending(TENANT).isEmpty());
        assertEquals(1, server.countOf("/api/tpv/products"));
    }

    @Test
    public void upsert_envia_el_producto_local_con_su_codigo_y_precio() throws Exception {
        server.on("/api/tpv/products", 200, "{}");
        outbox.add(TENANT, CatalogOutbox.UPSERT, CODIGO);

        enviar();

        FakeApiServer.Request peticion = server.requests().get(0);
        assertEquals("POST", peticion.method);
        JSONObject cuerpo = new JSONObject(peticion.body);
        assertEquals(CODIGO, cuerpo.getInt("codigo"));
        assertEquals("Manzana", cuerpo.getString("nombre"));
        assertEquals(2.5, cuerpo.getDouble("precio"), 1e-9);
    }

    @Test
    public void delete_con_404_se_da_por_hecho_porque_el_producto_ya_no_existe() throws Exception {
        server.on("/api/tpv/products/" + CODIGO, 404, "{}");
        outbox.add(TENANT, CatalogOutbox.DELETE, CODIGO);

        Final resultado = enviar();

        assertEquals(0, resultado.pendientes);
        assertEquals(0, resultado.descartadas);
        assertTrue(outbox.pending(TENANT).isEmpty());
    }

    @Test
    public void delete_aceptado_se_retira_de_la_cola() throws Exception {
        server.on("/api/tpv/products/" + CODIGO, 200, "{}");
        outbox.add(TENANT, CatalogOutbox.DELETE, CODIGO);

        Final resultado = enviar();

        assertEquals(0, resultado.pendientes);
        assertEquals(1, server.countOf("/api/tpv/products/" + CODIGO));
        assertTrue(outbox.pending(TENANT).isEmpty());
    }

    @Test
    public void rechazo_400_se_descarta_para_no_bloquear_la_cola() throws Exception {
        server.on("/api/tpv/products", 400, "{\"error\":\"precio inválido\"}");
        outbox.add(TENANT, CatalogOutbox.UPSERT, CODIGO);

        Final resultado = enviar();

        assertEquals(0, resultado.pendientes);
        assertEquals(1, resultado.descartadas);
        assertTrue(outbox.pending(TENANT).isEmpty());
    }

    @Test
    public void rechazo_403_se_descarta_como_definitivo() throws Exception {
        server.on("/api/tpv/products", 403, "{}");
        outbox.add(TENANT, CatalogOutbox.UPSERT, CODIGO);

        Final resultado = enviar();

        assertEquals(1, resultado.descartadas);
        assertTrue(outbox.pending(TENANT).isEmpty());
    }

    @Test
    public void error_500_deja_la_operacion_en_cola_y_detiene_el_envio() throws Exception {
        server.on("/api/tpv/products", 500, "{}");
        outbox.add(TENANT, CatalogOutbox.UPSERT, CODIGO);

        Final resultado = enviar();

        assertEquals(1, resultado.pendientes);
        assertEquals(0, resultado.descartadas);
        assertEquals(1, outbox.pending(TENANT).size());
    }

    @Test
    public void error_401_se_reintenta_y_no_se_descarta() throws Exception {
        server.on("/api/tpv/products", 401, "{}");
        outbox.add(TENANT, CatalogOutbox.UPSERT, CODIGO);

        Final resultado = enviar();

        assertEquals(1, resultado.pendientes);
        assertEquals(0, resultado.descartadas);
    }

    @Test
    public void error_429_se_reintenta_y_no_se_descarta() throws Exception {
        server.on("/api/tpv/products", 429, "{}");
        outbox.add(TENANT, CatalogOutbox.UPSERT, CODIGO);

        Final resultado = enviar();

        assertEquals(1, resultado.pendientes);
        assertEquals(0, resultado.descartadas);
    }

    @Test
    public void si_una_operacion_falla_con_500_las_siguientes_no_se_envian() throws Exception {
        insertarProductoLocal(CODIGO + 1, "Pera");
        server.on("/api/tpv/products", 500, "{}");
        outbox.add(TENANT, CatalogOutbox.UPSERT, CODIGO);
        outbox.add(TENANT, CatalogOutbox.UPSERT, CODIGO + 1);

        Final resultado = enviar();

        assertEquals(2, resultado.pendientes);
        assertEquals(1, server.countOf("/api/tpv/products"));
    }

    @Test
    public void producto_borrado_localmente_antes_del_envio_se_retira_sin_peticion() throws Exception {
        outbox.add(TENANT, CatalogOutbox.UPSERT, CODIGO + 50);

        Final resultado = enviar();

        assertEquals(0, resultado.pendientes);
        assertEquals(0, server.requests().size());
        assertTrue(outbox.pending(TENANT).isEmpty());
    }

    @Test
    public void sin_red_la_operacion_queda_pendiente() throws Exception {
        server.close();
        outbox.add(TENANT, CatalogOutbox.UPSERT, CODIGO);

        Final resultado = enviar();

        assertEquals(1, resultado.pendientes);
        assertEquals(0, resultado.descartadas);
        assertFalse(outbox.isEmpty(TENANT));
    }

    private Final enviar() throws InterruptedException {
        Final resultado = new Final();
        fondo.execute(() -> new CatalogOutboxSender(context, TENANT)
                .enviar(resultado::registrar));
        assertTrue("el envío debe terminar", resultado.hecho.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS));
        return resultado;
    }

    private void insertarProductoLocal(int codigo, String nombre) throws Exception {
        Future<?> insercion = fondo.submit(() -> {
            ProductoLocal producto = new ProductoLocal(codigo, nombre, 2.5, "Fruta", new ArrayList<>());
            AppDatabase.getDatabaseForTenant(context, TENANT).productoDao().insert(producto);
        });
        insercion.get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
    }
}
