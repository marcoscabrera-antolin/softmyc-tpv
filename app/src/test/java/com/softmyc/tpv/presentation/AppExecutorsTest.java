package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppExecutorsTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    @Test
    public void puedeActualizarUi_soloCuandoLaActivityNoTerminaNiEstaDestruida() {
        assertTrue(AppExecutors.puedeActualizarUi(false, false));
    }

    @Test
    public void puedeActualizarUi_faltaSiTerminaOSiEstaDestruida() {
        assertFalse(AppExecutors.puedeActualizarUi(true, false));
        assertFalse(AppExecutors.puedeActualizarUi(false, true));
        assertFalse(AppExecutors.puedeActualizarUi(true, true));
    }

    @Test
    public void disco_devuelveSiempreElMismoEjecutor() {
        assertNotNull(AppExecutors.disco());
        assertSame(AppExecutors.disco(), AppExecutors.disco());
    }

    @Test
    public void disco_unaTareaSinNegocioActivoNoImpideEjecutarLasSiguientes() throws Exception {
        java.util.concurrent.CountDownLatch segunda = new java.util.concurrent.CountDownLatch(1);

        AppExecutors.disco().execute(() -> {
            throw new com.softmyc.tpv.data.local.TenantContext.NoActiveTenantException("sin negocio");
        });
        AppExecutors.disco().execute(segunda::countDown);

        assertTrue("la tarea siguiente debe ejecutarse", segunda.await(5, java.util.concurrent.TimeUnit.SECONDS));
    }
}
