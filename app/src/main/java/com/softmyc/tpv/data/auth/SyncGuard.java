package com.softmyc.tpv.data.auth;

import com.softmyc.tpv.data.local.TenantContext;

/**
 * El servidor archiva las ventas que recibe bajo el negocio de la sesión, no bajo el que diga el cuerpo de la petición.
 * Por eso no se suben ventas de una base local de un negocio distinto del de la sesión: acabarían en el negocio equivocado.
 */
public final class SyncGuard {

    private SyncGuard() { }

    /**
     * Sin sesión o con una de demostración se sigue el camino heredado (clave de API); con sesión real, el negocio
     * local debe ser válido y coincidir con el de la sesión.
     */
    public static boolean canUploadFor(AuthSession session, String localTenant) {
        if (session == null || session.demo) return true;
        String sessionTenant = TenantContext.canonicalId(session.restaurantId);
        String localCanonical = TenantContext.canonicalId(localTenant);
        return sessionTenant != null && sessionTenant.equals(localCanonical);
    }
}
