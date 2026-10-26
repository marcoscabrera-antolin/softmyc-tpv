package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SyncBannerTest {

    private static final long SIN_EXITO = 0L;

    @Test
    public void sinPendientesElBannerEstaOculto() {
        SyncBanner.Aviso aviso = SyncBanner.describir(0, new SyncStatus(SyncOmision.SIN_RED, 0, 0L));

        assertFalse(aviso.visible());
    }

    @Test
    public void unaVentaPendienteUsaSingular() {
        SyncBanner.Aviso aviso = SyncBanner.describir(1, new SyncStatus(SyncOmision.NINGUNA, 0, SIN_EXITO));

        assertTrue(aviso.visible());
        assertEquals("1 venta sin sincronizar · pendiente de envío", aviso.texto());
    }

    @Test
    public void elMotivoDelUltimoIntentoSeMuestraConElNumeroDeVentas() {
        SyncBanner.Aviso aviso = SyncBanner.describir(3, new SyncStatus(SyncOmision.SIN_RED, 0, SIN_EXITO));

        assertEquals("3 ventas sin sincronizar · sin conexión a internet", aviso.texto());
    }

    @Test
    public void sinMotivoPeroConRechazadasSeIndicaElRechazo() {
        SyncBanner.Aviso aviso = SyncBanner.describir(2, new SyncStatus(SyncOmision.NINGUNA, 1, SIN_EXITO));

        assertEquals("2 ventas sin sincronizar · 1 rechazada por el servidor", aviso.texto());
    }

    @Test
    public void incluyeLaHoraDelUltimoExitoCuandoExiste() {
        long hace = 1_700_000_000_000L;

        SyncBanner.Aviso aviso = SyncBanner.describir(1, new SyncStatus(SyncOmision.SERVIDOR_ERROR, 0, hace));

        assertTrue(aviso.texto().startsWith("1 venta sin sincronizar · el servidor devolvió un error · última subida "));
    }
}
