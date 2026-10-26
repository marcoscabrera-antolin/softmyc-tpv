package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.VentaDao;
import com.softmyc.tpv.data.local.VentaLocal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SyncResetTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String TENANT_PRUEBA = "reseteo";

    private final android.content.Context context = ApplicationProvider.getApplicationContext();
    /** La base no admite consultas en el hilo principal (el del test): se delegan a un hilo propio. */
    private final ExecutorService hiloDb = Executors.newSingleThreadExecutor();
    private VentaDao dao;

    @Before
    public void setUp() throws Exception {
        dao = AppDatabase.getDatabaseForTenant(context, TENANT_PRUEBA).ventaDao();
        vaciar();
    }

    private void vaciar() throws Exception {
        enHilo(() -> {
            dao.deleteAll();
            return null;
        });
    }

    @After
    public void tearDown() throws Exception {
        vaciar();
        hiloDb.shutdown();
    }

    private <T> T enHilo(java.util.concurrent.Callable<T> tarea) throws Exception {
        return hiloDb.submit(tarea).get();
    }

    @Test
    public void cambiarAOtraRaizEsUnCambioDeServidor() {
        assertTrue(SyncReset.cambiaDeServidor("https://a.example.com", "https://b.example.com/api/tpv/orders"));
    }

    @Test
    public void mismaRaizConOtraRutaNoEsUnCambioDeServidor() {
        assertFalse(SyncReset.cambiaDeServidor("https://a.example.com", "https://a.example.com/api/tpv/orders"));
    }

    @Test
    public void comparacionDeRaizIgnoraMayusculasYBarraFinal() {
        assertFalse(SyncReset.cambiaDeServidor("https://A.example.com", "https://a.example.com/"));
    }

    @Test
    public void urlVaciaConservaElServidorActual() {
        assertFalse(SyncReset.cambiaDeServidor("https://a.example.com", ""));
        assertFalse(SyncReset.cambiaDeServidor("https://a.example.com", null));
    }

    @Test
    public void elArchivoDeLaBaseDeLaNubeCorrespondeAlNegocioLanube() {
        assertEquals("lanube", SyncReset.tenantDesdeArchivo("softmyc_database.db"));
    }

    @Test
    public void elArchivoDeOtroNegocioSeDerivaDeSuNombre() {
        assertEquals("fruteria", SyncReset.tenantDesdeArchivo("softmyc_fruteria.db"));
    }

    @Test
    public void ficherosQueNoSonBasesDeNegocioSeIgnoran() {
        assertNull(SyncReset.tenantDesdeArchivo("softmyc_fruteria.db-wal"));
        assertNull(SyncReset.tenantDesdeArchivo("otra_app.db"));
    }

    @Test
    public void marcarTodasPendientesDejaSinSincronizarSoloLasYaSubidas() throws Exception {
        enHilo(() -> dao.insert(new VentaLocal(1L, 1.0, "[]", true, "Efectivo")));
        enHilo(() -> dao.insert(new VentaLocal(2L, 2.0, "[]", false, "Efectivo")));

        int marcadas = enHilo(() -> SyncReset.marcarTodasPendientes(dao));

        List<VentaLocal> pendientes = enHilo(() -> dao.getUnsynced());
        assertEquals(1, marcadas);
        assertEquals(2, pendientes.size());
    }
}
