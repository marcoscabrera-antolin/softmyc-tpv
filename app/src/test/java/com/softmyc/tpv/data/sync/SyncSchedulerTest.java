package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SyncSchedulerTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private final Context context = ApplicationProvider.getApplicationContext();

    private JobScheduler planificador() {
        return (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    @Test
    public void programarDejaUnTrabajoPeriodicoConRedDisponible() {
        SyncScheduler.programar(context);

        JobInfo trabajo = planificador().getPendingJob(SyncScheduler.JOB_ID);

        assertNotNull(trabajo);
        assertTrue(trabajo.isPeriodic());
        assertEquals(SyncScheduler.INTERVALO_MS, trabajo.getIntervalMillis());
        assertEquals(JobInfo.NETWORK_TYPE_ANY, trabajo.getNetworkType());
        assertEquals(SyncJobService.class.getName(), trabajo.getService().getClassName());
    }

    @Test
    public void programarDosVecesNoDuplicaElTrabajo() {
        SyncScheduler.programar(context);
        SyncScheduler.programar(context);

        assertEquals(1, planificador().getAllPendingJobs().size());
    }

    @Test
    public void cancelarQuitaElTrabajo() {
        SyncScheduler.programar(context);

        SyncScheduler.cancelar(context);

        assertNull(planificador().getPendingJob(SyncScheduler.JOB_ID));
        assertFalse(planificador().getAllPendingJobs().stream().anyMatch(j -> j.getId() == SyncScheduler.JOB_ID));
    }
}
