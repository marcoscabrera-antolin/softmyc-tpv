package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.data.auth.AuthSession;

import org.junit.Test;

public class SyncRetryPolicyTest {

    private static AuthSession sesionReal(String negocio) {
        return new AuthSession("a", "r", 1L, 2L, "ana", "tpv_operator", negocio, false);
    }

    @Test
    public void sinNegocioActivoNoSeSincroniza() {
        assertEquals(SyncOmision.SIN_NEGOCIO, SyncRetryPolicy.comprobarPrecondiciones(null, sesionReal("lanube")));
    }

    @Test
    public void sinSesionNoSeSincroniza() {
        assertEquals(SyncOmision.SIN_SESION, SyncRetryPolicy.comprobarPrecondiciones("lanube", null));
    }

    @Test
    public void sesionDeDemostracionNoSeSincronizaEnSegundoPlano() {
        AuthSession demo = AuthSession.demo("lanube", "tpv_operator", "ana", 1L);

        assertEquals(SyncOmision.SIN_SESION, SyncRetryPolicy.comprobarPrecondiciones("lanube", demo));
    }

    @Test
    public void sesionDeOtroNegocioNoSeSincroniza() {
        assertEquals(SyncOmision.SESION_OTRO_NEGOCIO,
                SyncRetryPolicy.comprobarPrecondiciones("fruteria", sesionReal("lanube")));
    }

    @Test
    public void sesionYNegocioCoincidentesPermitenSincronizar() {
        assertEquals(SyncOmision.NINGUNA, SyncRetryPolicy.comprobarPrecondiciones("lanube", sesionReal("lanube")));
    }

    @Test
    public void sinRedOErrorDeServidorSeReprogramaAunqueNoHayaCatalogoPendiente() {
        assertTrue(SyncRetryPolicy.debeReprogramar(SyncOmision.SIN_RED, 0));
        assertTrue(SyncRetryPolicy.debeReprogramar(SyncOmision.SERVIDOR_ERROR, 0));
    }

    @Test
    public void cambiosDeCatalogoPendientesSeReprogramanAunqueLasVentasEstenBien() {
        assertTrue(SyncRetryPolicy.debeReprogramar(SyncOmision.NINGUNA, 2));
    }

    @Test
    public void sinMotivoReintentableNiCatalogoPendienteNoSeReprograma() {
        assertFalse(SyncRetryPolicy.debeReprogramar(SyncOmision.NINGUNA, 0));
        assertFalse(SyncRetryPolicy.debeReprogramar(SyncOmision.SIN_CREDENCIALES, 0));
    }
}
