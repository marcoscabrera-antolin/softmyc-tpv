package com.softmyc.tpv.util;

/**
 * Fallo de impresión de una venta que ya está guardada. Se muestra en un diálogo persistente
 * con la opción de reintentar la impresión del ticket indicado.
 */
public final class FalloImpresion {

    /** Valor de ticketId cuando no se conoce el número de ticket. */
    public static final int TICKET_DESCONOCIDO = -1;

    private final int ticketId;
    private final String detalle;

    public FalloImpresion(int ticketId, String detalle) {
        this.ticketId = ticketId;
        this.detalle = detalle;
    }

    public int getTicketId() {
        return ticketId;
    }

    public String getDetalle() {
        return detalle;
    }

    /** Título del diálogo con el número de ticket cuando se conoce. */
    public String getTitulo() {
        return tieneTicket() ? "Ticket n.º " + ticketId + " sin imprimir" : "Ticket sin imprimir";
    }

    /** Texto del diálogo: incluye el número de ticket para que el camarero pueda identificarlo. */
    public String getMensajeDialogo() {
        String cabecera = tieneTicket()
                ? "Ticket n.º " + ticketId + ": la venta está guardada, pero no se pudo imprimir."
                : "La venta está guardada, pero no se pudo imprimir.";
        String causa = detalle == null || detalle.trim().isEmpty() ? "" : "\n\nDetalle: " + detalle.trim();
        return cabecera + "\n\nRevisa la impresora y pulsa \"Reintentar impresión\"." + causa;
    }

    private boolean tieneTicket() {
        return ticketId > 0;
    }
}
