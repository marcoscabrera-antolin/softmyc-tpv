package com.softmyc.tpv.data.sync;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

/**
 * Programa el reintento periódico de la subida de ventas y del catálogo pendiente. Usa la API de plataforma
 * ({@link JobScheduler}), sin dependencias externas. Programar de nuevo con el mismo id reemplaza el trabajo.
 */
public final class SyncScheduler {

    public static final int JOB_ID = 7301;
    /** Mínimo que admite JobScheduler para trabajos periódicos. */
    public static final long INTERVALO_MS = 15 * 60 * 1000L;
    private static final long BACKOFF_MS = 60 * 1000L;

    private SyncScheduler() { }

    public static void programar(Context context) {
        JobScheduler planificador = planificador(context);
        if (planificador == null) return;
        JobInfo trabajo = new JobInfo.Builder(JOB_ID, new ComponentName(context, SyncJobService.class))
                .setPeriodic(INTERVALO_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        planificador.schedule(trabajo);
    }

    public static void cancelar(Context context) {
        JobScheduler planificador = planificador(context);
        if (planificador != null) planificador.cancel(JOB_ID);
    }

    private static JobScheduler planificador(Context context) {
        return (JobScheduler) context.getApplicationContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }
}
