package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
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
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Ciclo de subida periódica con sesión válida del mismo negocio: sin nada pendiente no reprograma; si el servidor
 * rechaza con error un cambio de catálogo, sí reprograma. El servidor es {@link FakeApiServer}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SyncJobCicloSubidaTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Negocio fuera de los catálogos iniciales: su base no se siembra en segundo plano. */
    private static final String NEGOCIO = "sjtest";
    private static final int CODIGO = 555;
    private static final long TIMEOUT_SEGUNDOS = 15;

    private final Context context = ApplicationProvider.getApplicationContext();
    private final ExecutorService fondo = Executors.newSingleThreadExecutor();
    private FakeApiServer server;

    @Before
    public void setUp() throws Exception {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(context);
        server = new FakeApiServer();
        SecurePreferencesHelper.getPrinterPrefs(context).edit().clear()
                .putString("backend_base_url", server.baseUrl() + "/api/tpv/orders")
                .putString("backend_api_key", "clave-test")
                .commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(context), NEGOCIO);
        long ahora = System.currentTimeMillis();
        Auth.store(context).save(new AuthSession("token-test", "refresco-test", ahora + 3_600_000L,
                ahora + 86_400_000L, "ana", "tpv_operator", NEGOCIO, false));
    }

    @After
    public void tearDown() {
        server.close();
        fondo.shutdownNow();
        Auth.store(context).clear();
        SecurePreferencesHelper.getSessionPrefs(context).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(context).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(context);
    }

    @Test
    public void sin_nada_pendiente_el_ciclo_termina_sin_reprogramar() throws Exception {
        List<Boolean> llamadas = ejecutarEnFondo();

        assertEquals(Collections.singletonList(false), llamadas);
    }

    @Test
    public void cambio_de_catalogo_rechazado_con_error_de_servidor_reprograma_el_ciclo() throws Exception {
        server.on("/api/tpv/products", 500, "{}");
        insertarProducto();
        new CatalogOutbox(SecurePreferencesHelper.getPrinterPrefs(context))
                .add(NEGOCIO, CatalogOutbox.UPSERT, CODIGO);

        List<Boolean> llamadas = ejecutarEnFondo();

        assertEquals(Collections.singletonList(true), llamadas);
        assertEquals(1, new CatalogOutbox(SecurePreferencesHelper.getPrinterPrefs(context)).pending(NEGOCIO).size());
    }

    /** Ejecuta el ciclo fuera del hilo principal (las bases no admiten consultas ahí) y espera su resultado. */
    private List<Boolean> ejecutarEnFondo() throws InterruptedException {
        List<Boolean> llamadas = new ArrayList<>();
        CountDownLatch fin = new CountDownLatch(1);
        fondo.execute(() -> SyncJobService.ejecutarCiclo(context, reprogramar -> {
            synchronized (llamadas) {
                llamadas.add(reprogramar);
            }
            fin.countDown();
        }));
        assertTrue("el ciclo no terminó", fin.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS));
        synchronized (llamadas) {
            return new ArrayList<>(llamadas);
        }
    }

    private void insertarProducto() throws Exception {
        Future<?> alta = fondo.submit(() -> AppDatabase.getDatabaseForTenant(context, NEGOCIO).productoDao()
                .insert(new ProductoLocal(CODIGO, "Pan", 0.5, "Panadería", new ArrayList<>())));
        alta.get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
    }
}
