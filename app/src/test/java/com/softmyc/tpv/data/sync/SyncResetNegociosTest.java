package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;
import com.softmyc.tpv.data.local.VentaDao;
import com.softmyc.tpv.data.local.VentaLocal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reinicio de sincronización sobre todas las bases de negocio del dispositivo: se localizan por el nombre de
 * fichero, se marcan pendientes sus ventas sincronizadas y el resto de ficheros se ignoran.
 * Las consultas a Room se ejecutan en un hilo propio, no en el principal.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SyncResetNegociosTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Negocios fuera de los catálogos iniciales: su base no se siembra en segundo plano. */
    private static final String ALFA = "resetalfa";
    private static final String BETA = "resetbeta";

    private final Context context = ApplicationProvider.getApplicationContext();
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();

    @Before
    public void setUp() throws Exception {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(context);
    }

    @After
    public void tearDown() throws Exception {
        hilo.shutdownNow();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(context);
    }

    @Test
    public void reinicio_marca_pendientes_las_ventas_sincronizadas_de_todos_los_negocios() throws Exception {
        VentaDao daoAlfa = AppDatabase.getDatabaseForTenant(context, ALFA).ventaDao();
        VentaDao daoBeta = AppDatabase.getDatabaseForTenant(context, BETA).ventaDao();
        enHilo(() -> daoAlfa.insert(new VentaLocal(1L, 1.0, "[]", true, "Efectivo")));
        enHilo(() -> daoAlfa.insert(new VentaLocal(2L, 2.0, "[]", true, "Efectivo")));
        enHilo(() -> daoBeta.insert(new VentaLocal(3L, 3.0, "[]", true, "Efectivo")));
        enHilo(() -> daoBeta.insert(new VentaLocal(4L, 4.0, "[]", false, "Efectivo")));

        int cambiadas = enHilo(() -> SyncReset.marcarTodasPendientesEnTodosLosNegocios(context));

        assertEquals(3, cambiadas);
        assertEquals(2, enHilo(() -> daoAlfa.getUnsynced()).size());
        assertEquals(2, enHilo(() -> daoBeta.getUnsynced()).size());
    }

    @Test
    public void sin_bases_de_negocio_el_reinicio_no_cambia_nada() throws Exception {
        int cambiadas = enHilo(() -> SyncReset.marcarTodasPendientesEnTodosLosNegocios(context));

        assertEquals(0, cambiadas);
    }

    @Test
    public void un_negocio_sin_ventas_sincronizadas_no_aporta_cambios() throws Exception {
        VentaDao dao = AppDatabase.getDatabaseForTenant(context, ALFA).ventaDao();
        enHilo(() -> dao.insert(new VentaLocal(1L, 1.0, "[]", false, "Efectivo")));

        int cambiadas = enHilo(() -> SyncReset.marcarTodasPendientesEnTodosLosNegocios(context));

        assertEquals(0, cambiadas);
        assertTrue(enHilo(() -> dao.getUnsynced()).size() == 1);
    }

    private <T> T enHilo(Callable<T> tarea) throws Exception {
        return hilo.submit(tarea).get();
    }
}
