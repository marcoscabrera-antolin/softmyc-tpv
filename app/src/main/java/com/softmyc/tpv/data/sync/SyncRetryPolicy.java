package com.softmyc.tpv.data.sync;

import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.auth.SyncGuard;

/** Decisiones puras del reintento periódico en segundo plano. */
public final class SyncRetryPolicy {

    private SyncRetryPolicy() { }

    /**
     * Antes de sincronizar en segundo plano: hace falta un negocio activo y una sesión real (no de demostración)
     * del mismo negocio. Devuelve {@link SyncOmision#NINGUNA} si se puede sincronizar.
     */
    public static SyncOmision comprobarPrecondiciones(String negocioLocal, AuthSession sesion) {
        if (negocioLocal == null) return SyncOmision.SIN_NEGOCIO;
        if (sesion == null || sesion.demo) return SyncOmision.SIN_SESION;
        if (!SyncGuard.canUploadFor(sesion, negocioLocal)) return SyncOmision.SESION_OTRO_NEGOCIO;
        return SyncOmision.NINGUNA;
    }

    /** ¿Hay que volver a programar el job? Si quedó algo pendiente por un fallo transitorio, o cambios de catálogo sin enviar. */
    public static boolean debeReprogramar(SyncOmision omisionVentas, int pendientesCatalogo) {
        return omisionVentas.reintentable() || pendientesCatalogo > 0;
    }
}
