package com.softmyc.tpv.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FalloImpresionTest {

    @Test
    public void mensajeDialogo_incluyeNumeroDeTicketYDetalle() {
        FalloImpresion fallo = new FalloImpresion(42, "timeout");

        String mensaje = fallo.getMensajeDialogo();

        assertTrue(mensaje.contains("Ticket n.º 42"));
        assertTrue(mensaje.contains("Detalle: timeout"));
    }

    @Test
    public void titulo_usaNumeroDeTicketCuandoSeConoce() {
        assertEquals("Ticket n.º 7 sin imprimir", new FalloImpresion(7, null).getTitulo());
    }

    @Test
    public void mensajeDialogo_sinTicketConocidoNoMuestraNumeroNiDetalleVacio() {
        FalloImpresion fallo = new FalloImpresion(FalloImpresion.TICKET_DESCONOCIDO, "  ");

        String mensaje = fallo.getMensajeDialogo();

        assertFalse(mensaje.contains("n.º"));
        assertFalse(mensaje.contains("Detalle"));
        assertEquals("Ticket sin imprimir", fallo.getTitulo());
    }
}
