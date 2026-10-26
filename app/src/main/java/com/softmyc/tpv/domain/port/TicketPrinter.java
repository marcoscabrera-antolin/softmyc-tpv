package com.softmyc.tpv.domain.port;

/** Puerto de envío del ticket a la impresora térmica de red. */
public interface TicketPrinter {

    /** Envía el ticket; el resultado llega por {@code resultado} (puede ser desde otro hilo). */
    void imprimir(String ip, AjustesImpresora ajustes, String texto, ResultadoImpresion resultado);

    interface ResultadoImpresion {
        void onResultado(boolean ok, String error);
    }
}
