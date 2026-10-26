package com.softmyc.tpv.data.sync;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.util.Log;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.local.TenantContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Reintento periódico en segundo plano: sube las ventas pendientes y envía el catálogo en cola. Corre fuera del hilo
 * principal y siempre llama a {@code jobFinished} una sola vez, aunque falle algo o se agote el tiempo.
 */
public class SyncJobService extends JobService {

    private static final String TAG = "SyncJobService";
    /** Margen para terminar antes de que el sistema mate el job (límite práctico de ~10 min). */
    private static final long TIEMPO_MAXIMO_MS = 4 * 60 * 1000L;
    private static final int TAREAS_EN_PARALELO = 2;

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> ejecutarCiclo(getApplicationContext(), reprogramar -> jobFinished(params, reprogramar)),
                "sync-job").start();
        return true;
    }

    /** El sistema quita el job (red, batería...): se pide reprogramación y el siguiente intento lo retomará. */
    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    /**
     * Un ciclo completo. {@code alTerminar} recibe si hay que reprogramar, y se llama exactamente una vez.
     * Sin negocio o sin sesión real no se sube nada y no se reprograma: el siguiente periódico lo volverá a mirar.
     */
    static void ejecutarCiclo(Context context, Consumer<Boolean> alTerminar) {
        AtomicBoolean terminado = new AtomicBoolean(false);
        Consumer<Boolean> terminarUnaVez = reprogramar -> {
            if (terminado.compareAndSet(false, true)) alTerminar.accept(reprogramar);
        };
        try {
            String negocio = TenantContext.read(SecurePreferencesHelper.getPrinterPrefs(context));
            AuthSession sesion = Auth.manager(context).current();
            SyncOmision omision = SyncRetryPolicy.comprobarPrecondiciones(negocio, sesion);
            if (omision != SyncOmision.NINGUNA) {
                SyncStatusStore.of(context).registrarOmision(omision);
                terminarUnaVez.accept(false);
                return;
            }
            terminarUnaVez.accept(subirTodo(context, negocio));
        } catch (RuntimeException e) {
            Log.e(TAG, "Fallo inesperado en la sincronización periódica", e);
            terminarUnaVez.accept(true);
        }
    }

    /** Sube ventas y catálogo en paralelo y espera a los dos (con tope de tiempo). Devuelve si hay que reprogramar. */
    private static boolean subirTodo(Context context, String negocio) {
        ExecutorService disco = Executors.newSingleThreadExecutor();
        CountDownLatch fin = new CountDownLatch(TAREAS_EN_PARALELO);
        AtomicInteger catalogoPendiente = new AtomicInteger();
        try {
            new CatalogOutboxSender(context, negocio).enviar((pendientes, descartadas) -> {
                catalogoPendiente.set(pendientes);
                fin.countDown();
            });
            new VentaUploader(context, disco, rechazadas -> { }).subirPendientes(fin::countDown);
            boolean acabo = fin.await(TIEMPO_MAXIMO_MS, TimeUnit.MILLISECONDS);
            if (!acabo) Log.w(TAG, "La sincronización periódica no terminó a tiempo; se reprograma");
            SyncOmision omisionVentas = SyncStatusStore.of(context).leer().getOmision();
            return !acabo || SyncRetryPolicy.debeReprogramar(omisionVentas, catalogoPendiente.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        } finally {
            disco.shutdown();
        }
    }
}
