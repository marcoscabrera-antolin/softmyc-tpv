package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;

public class SyncOmisionTest {

    @Test
    public void errorDeRedSinTextoConocidoSeClasificaComoSinRed() {
        assertEquals(SyncOmision.SIN_RED, SyncOmision.clasificar(new IOException("Unable to resolve host")));
        assertEquals(SyncOmision.SIN_RED, SyncOmision.clasificar(new IOException((String) null)));
    }

    @Test
    public void falloDeCredencialesSeClasificaSinCredenciales() {
        IOException e = new IOException("Sin credenciales: inicia sesión o configura la clave de API en Ajustes");

        assertEquals(SyncOmision.SIN_CREDENCIALES, SyncOmision.clasificar(e));
    }

    @Test
    public void ausenciaDeNegocioYUrlMalConfiguradaTienenSuMotivo() {
        assertEquals(SyncOmision.SIN_NEGOCIO,
                SyncOmision.clasificar(new IOException("Sin negocio activo: vuelve a iniciar sesión")));
        assertEquals(SyncOmision.URL_INVALIDA,
                SyncOmision.clasificar(new IOException("Error interno al preparar petición: URL no válida: x")));
    }

    @Test
    public void soloLosMotivosTransitoriosSonReintentables() {
        assertTrue(SyncOmision.SIN_RED.reintentable());
        assertTrue(SyncOmision.SERVIDOR_ERROR.reintentable());
        assertFalse(SyncOmision.SIN_CREDENCIALES.reintentable());
        assertFalse(SyncOmision.SIN_NEGOCIO.reintentable());
        assertFalse(SyncOmision.SESION_OTRO_NEGOCIO.reintentable());
        assertFalse(SyncOmision.NINGUNA.reintentable());
    }

    @Test
    public void ningunaNoTieneDescripcion() {
        assertEquals("", SyncOmision.NINGUNA.descripcion());
    }
}
