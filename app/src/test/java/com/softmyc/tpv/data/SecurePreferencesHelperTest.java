package com.softmyc.tpv.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SecurePreferencesHelperTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private final Context context = ApplicationProvider.getApplicationContext();

    @After
    public void restoreRealFactory() {
        SecurePreferencesHelper.setFactoryForTests(null);
    }

    @Test
    public void values_persistAcrossCalls() {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putString("restaurant_id", "fruteria").commit();

        assertEquals("fruteria", SecurePreferencesHelper.getPrinterPrefs(context).getString("restaurant_id", null));
    }

    @Test
    public void secrets_areNeverWrittenToPlaintextFallbackFiles() {
        SharedPreferences session = SecurePreferencesHelper.getSessionPrefs(context);
        session.edit().putString("auth_token", "secreto").commit();
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putString("backend_api_key", "clave").commit();

        File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        for (String plain : new String[]{"SesionTPV.xml", "ConfigImpresora.xml", "pedido_prefs.xml"}) {
            assertFalse("No debe existir " + plain, new File(prefsDir, plain).exists());
        }
    }

    /** Fábrica de pruebas que cuenta cuántas veces se hace la creación cara (en producción, EncryptedSharedPreferences). */
    private static AtomicInteger installCountingFactory() {
        AtomicInteger creaciones = new AtomicInteger();
        SecurePreferencesHelper.setFactoryForTests((c, nombre) -> {
            creaciones.incrementAndGet();
            return new InMemorySharedPreferences();
        });
        return creaciones;
    }

    @Test
    public void repeatedCalls_createTheEncryptedStoreOnlyOncePerFile() {
        AtomicInteger creaciones = installCountingFactory();

        for (int i = 0; i < 20; i++) {
            SecurePreferencesHelper.getPrinterPrefs(context);
            SecurePreferencesHelper.getSessionPrefs(context);
        }

        assertEquals("Una creación por fichero, no una por llamada", 2, creaciones.get());
    }

    @Test
    public void concurrentFirstUse_createsASingleInstance() throws Exception {
        AtomicInteger creaciones = installCountingFactory();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<SharedPreferences>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                Callable<SharedPreferences> tarea = () -> SecurePreferencesHelper.getCounterPrefs(context);
                futures.add(pool.submit(tarea));
            }
            SharedPreferences primera = futures.get(0).get();
            for (Future<SharedPreferences> f : futures) assertSame(primera, f.get());
            assertEquals(1, creaciones.get());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    public void keystoreFailure_isNotCached_soTheStoreCanRecover() {
        AtomicInteger intentos = new AtomicInteger();
        SecurePreferencesHelper.setFactoryForTests((c, nombre) -> {
            if (intentos.incrementAndGet() <= 2) throw new java.security.GeneralSecurityException("Keystore no disponible");
            return new InMemorySharedPreferences();
        });

        SharedPreferences fallback1 = SecurePreferencesHelper.getPrinterPrefs(context);
        SharedPreferences fallback2 = SecurePreferencesHelper.getPrinterPrefs(context);
        SharedPreferences recuperada = SecurePreferencesHelper.getPrinterPrefs(context);
        SharedPreferences cacheada = SecurePreferencesHelper.getPrinterPrefs(context);

        assertSame("El almacén en memoria de emergencia se reutiliza", fallback1, fallback2);
        assertNotSame("Al recuperarse el Keystore se vuelve al almacén normal", fallback1, recuperada);
        assertSame("Una vez creado, se cachea", recuperada, cacheada);
        assertEquals("Solo se intentó crear 3 veces", 3, intentos.get());
    }

    @Test
    public void differentFiles_areDifferentInstances() {
        installCountingFactory();

        assertNotSame(SecurePreferencesHelper.getPrinterPrefs(context), SecurePreferencesHelper.getSessionPrefs(context));
        assertNotSame(SecurePreferencesHelper.getSessionPrefs(context), SecurePreferencesHelper.getCounterPrefs(context));
    }

    @Test
    public void keystoreFailure_isReportedAsDegradedWhileTheMemoryFallbackWorks() {
        SecurePreferencesHelper.setFactoryForTests((ctx, name) -> {
            throw new java.security.GeneralSecurityException("Keystore no disponible");
        });

        SharedPreferences prefs = SecurePreferencesHelper.getSessionPrefs(context);
        prefs.edit().putString("auth_token", "secreto").commit();

        assertTrue(SecurePreferencesHelper.isDegraded());
        assertEquals("secreto", SecurePreferencesHelper.getSessionPrefs(context).getString("auth_token", null));
    }

    @Test
    public void recoveredKeystore_clearsTheDegradedFlag() {
        SecurePreferencesHelper.setFactoryForTests((ctx, name) -> {
            throw new java.security.GeneralSecurityException("Keystore no disponible");
        });
        SecurePreferencesHelper.getSessionPrefs(context);
        assertTrue(SecurePreferencesHelper.isDegraded());

        SecurePreferencesHelper.setFactoryForTests((ctx, name) -> new InMemorySharedPreferences());
        SecurePreferencesHelper.getSessionPrefs(context);

        assertFalse(SecurePreferencesHelper.isDegraded());
    }
}
