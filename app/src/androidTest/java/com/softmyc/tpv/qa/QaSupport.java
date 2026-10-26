package com.softmyc.tpv.qa;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Environment;

import androidx.test.platform.app.InstrumentationRegistry;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.local.AppDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Preparación común de las pruebas de interfaz en la tablet: sesión local de demostración (sin red), impresora
 * apuntando a un puerto cerrado (no se imprime papel) y servidor inexistente (no se sube nada). Al terminar se
 * restauran las preferencias y la sesión que tenía la tablet.
 */
final class QaSupport {

    /** Negocio sin datos reales en la tablet: lo crea y siembra la propia prueba. */
    static final String NEGOCIO_PRUEBA = "paqueteria";

    final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private Map<String, ?> prefsOriginales;
    private AuthSession sesionOriginal;
    private String negocioOriginal;

    void preparar(String negocio, String rol) {
        SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(ctx);
        prefsOriginales = new HashMap<>(prefs.getAll());
        sesionOriginal = Auth.store(ctx).load();
        negocioOriginal = AppDatabase.getActiveTenantId(ctx);

        long ahora = System.currentTimeMillis();
        long unAnio = 365L * 24 * 60 * 60 * 1000;
        Auth.store(ctx).save(new AuthSession("local_demo_token_" + negocio, "local_demo_refresh_" + negocio, ahora + unAnio, ahora + unAnio,
                "qa_tester", rol, negocio, true));
        AppDatabase.setActiveTenantId(ctx, negocio);
        prefs.edit()
                .putString("user_role", rol)
                .putString("user_name", "qa_tester")
                .putString("impresora_ip", "127.0.0.1")
                .putInt("impresora_puerto", 9)                       // puerto cerrado: la conexión falla al instante
                .putString("backend_base_url", "http://127.0.0.1:9") // servidor inexistente
                .remove("backend_api_key")
                .commit();
    }

    void restaurar() {
        SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(ctx);
        SharedPreferences.Editor e = prefs.edit().clear();
        if (prefsOriginales != null) {
            for (Map.Entry<String, ?> kv : prefsOriginales.entrySet()) {
                Object v = kv.getValue();
                if (v instanceof String) e.putString(kv.getKey(), (String) v);
                else if (v instanceof Integer) e.putInt(kv.getKey(), (Integer) v);
                else if (v instanceof Boolean) e.putBoolean(kv.getKey(), (Boolean) v);
                else if (v instanceof Long) e.putLong(kv.getKey(), (Long) v);
                else if (v instanceof Float) e.putFloat(kv.getKey(), (Float) v);
            }
        }
        e.commit();
        if (sesionOriginal != null) Auth.store(ctx).save(sesionOriginal);
        else Auth.store(ctx).clear();
        if (negocioOriginal != null) AppDatabase.setActiveTenantId(ctx, negocioOriginal);
    }

    /** Ejecuta en un hilo aparte (la base no admite consultas en el hilo principal) y devuelve el resultado. */
    <T> T enDb(Callable<T> tarea) throws Exception {
        return Executors.newSingleThreadExecutor().submit(tarea).get(20, TimeUnit.SECONDS);
    }

    interface Condicion {
        boolean cumple() throws Exception;
    }

    /** Espera activa (los hilos de fondo de la app no se sincronizan con Espresso). */
    static boolean esperar(long maxMs, Condicion c) throws Exception {
        long fin = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < fin) {
            if (c.cumple()) return true;
            Thread.sleep(100);
        }
        return c.cumple();
    }

    /** Guarda una captura de pantalla como evidencia: /sdcard/Android/data/com.softmyc.tpv/files/Pictures/qa/<nombre>.png */
    void captura(String nombre) {
        try {
            Bitmap bmp = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            if (bmp == null) return;
            File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "qa");
            if (!dir.exists() && !dir.mkdirs()) return;
            try (FileOutputStream out = new FileOutputStream(new File(dir, nombre + ".png"))) {
                bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
            }
        } catch (Exception ignored) {
            // la captura es solo evidencia: nunca debe hacer fallar la prueba
        }
    }

    static boolean estaViva(Activity a) {
        return a != null && !a.isFinishing() && !a.isDestroyed();
    }
}
