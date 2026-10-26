package com.softmyc.tpv.presentation;

import com.softmyc.tpv.data.auth.AuthSession;

/**
 * Regla de validez de la sesión, basada en la caducidad de los tokens y no en el día del inicio de sesión.
 * Función pura: recibe la sesión, la hora y si hay red.
 */
public final class SessionPolicy {

    /**
     * VALID: se puede usar tal cual. NEEDS_REFRESH: hay red y el acceso ha caducado o está a punto: renovarlo.
     * EXPIRED: el refresco ha caducado, hay que volver a introducir la contraseña. NO_SESSION: no hay sesión.
     */
    public enum State { VALID, NEEDS_REFRESH, EXPIRED, NO_SESSION }

    /** Margen antes de la caducidad del acceso para renovarlo con antelación. */
    static final long REFRESH_MARGIN_MS = 30_000L;

    private SessionPolicy() { }

    /** Una sesión real sin negocio (administrador de plataforma que aún no ha elegido) no puede entrar al TPV. */
    public static boolean needsBusiness(AuthSession session) {
        return session != null && !session.demo && session.restaurantId == null;
    }

    public static State evaluate(AuthSession session, long nowMs, boolean online) {
        if (session == null) return State.NO_SESSION;
        if (nowMs >= session.refreshExpiresAtMs) return State.EXPIRED;
        if (session.demo || nowMs < session.accessExpiresAtMs - REFRESH_MARGIN_MS) return State.VALID;
        // Sin red se sigue vendiendo con la sesión local hasta que caduque el refresco; con red se renueva.
        return online ? State.NEEDS_REFRESH : State.VALID;
    }
}
