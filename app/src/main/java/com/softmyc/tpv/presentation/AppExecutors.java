package com.softmyc.tpv.presentation;

import android.app.Activity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ejecutor compartido de disco para la capa de presentación. Un único hilo con nombre: las escrituras
 * encoladas en orden se ejecutan en orden, y no se crea un hilo nuevo por cada acción.
 */
public final class AppExecutors {

    private static final String NOMBRE_HILO = "softmyc-disco";

    private static final ExecutorService DISCO = Executors.newSingleThreadExecutor(runnable -> {
        Thread hilo = new Thread(runnable, NOMBRE_HILO);
        hilo.setDaemon(true);
        final Thread.UncaughtExceptionHandler porDefecto = Thread.getDefaultUncaughtExceptionHandler();
        hilo.setUncaughtExceptionHandler((t, e) -> {
            if (e instanceof com.softmyc.tpv.data.local.TenantContext.NoActiveTenantException) {
                // Sin negocio activo no hay base a la que acceder: BaseActivity devuelve al inicio de sesión.
                android.util.Log.w("AppExecutors", "Tarea de disco omitida: " + e.getMessage());
            } else if (porDefecto != null) {
                porDefecto.uncaughtException(t, e);
            }
        });
        return hilo;
    });

    private AppExecutors() {
    }

    public static ExecutorService disco() {
        return DISCO;
    }

    /** Decide si una Activity puede seguir actualizando su UI (no terminando ni destruida). */
    public static boolean puedeActualizarUi(boolean terminando, boolean destruida) {
        return !terminando && !destruida;
    }

    /**
     * Ejecuta la acción en el hilo principal solo si la Activity sigue viva, tanto al encolarla
     * como al ejecutarla (la Activity puede destruirse mientras esperaba el hilo principal).
     */
    public static void enUiSiViva(Activity activity, Runnable accion) {
        if (activity == null || !estaViva(activity)) return;
        activity.runOnUiThread(() -> {
            if (estaViva(activity)) {
                accion.run();
            }
        });
    }

    private static boolean estaViva(Activity activity) {
        return puedeActualizarUi(activity.isFinishing(), activity.isDestroyed());
    }
}
