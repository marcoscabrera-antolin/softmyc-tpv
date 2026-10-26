package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** El ciclo del job debe terminar siempre, con una sola llamada a jobFinished, aunque no haya nada que hacer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SyncJobCicloTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private final Context context = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(context);
        prefs.edit().remove("current_tenant").remove("restaurant_id").commit();
        Auth.store(context).clear();
    }

    @After
    public void tearDown() {
        Auth.store(context).clear();
    }

    /** Ejecuta el ciclo y devuelve los valores con los que se llamó a jobFinished (una sola vez). */
    private List<Boolean> ejecutar() throws InterruptedException {
        List<Boolean> llamadas = new ArrayList<>();
        CountDownLatch fin = new CountDownLatch(1);
        SyncJobService.ejecutarCiclo(context, reprogramar -> {
            synchronized (llamadas) {
                llamadas.add(reprogramar);
            }
            fin.countDown();
        });
        assertTrue("el ciclo no terminó", fin.await(10, TimeUnit.SECONDS));
        synchronized (llamadas) {
            return new ArrayList<>(llamadas);
        }
    }

    @Test
    public void sinNegocioActivoTerminaSinReprogramarYDejaElMotivoVisible() throws InterruptedException {
        List<Boolean> llamadas = ejecutar();

        assertEquals(List.of(false), llamadas);
        assertEquals(SyncOmision.SIN_NEGOCIO, SyncStatusStore.of(context).leer().getOmision());
    }

    @Test
    public void sinSesionTerminaSinSubirNada() throws InterruptedException {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putString("current_tenant", "fruteria_v2").commit();

        List<Boolean> llamadas = ejecutar();

        assertEquals(List.of(false), llamadas);
        assertEquals(SyncOmision.SIN_SESION, SyncStatusStore.of(context).leer().getOmision());
    }

    @Test
    public void sesionDeDemostracionNoSincronizaEnSegundoPlano() throws InterruptedException {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putString("current_tenant", "fruteria_v2").commit();
        Auth.store(context).save(AuthSession.demo("fruteria", "tpv_operator", "ana", System.currentTimeMillis()));

        List<Boolean> llamadas = ejecutar();

        assertFalse(llamadas.get(0));
        assertEquals(1, llamadas.size());
    }
}
