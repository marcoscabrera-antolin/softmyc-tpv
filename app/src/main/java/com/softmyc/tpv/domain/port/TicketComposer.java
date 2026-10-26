package com.softmyc.tpv.domain.port;

import com.softmyc.tpv.domain.PedidoItem;

import java.util.List;
import java.util.Map;

/** Puerto que compone el texto del ticket y calcula su total con el mismo motor de precios de la pantalla. */
public interface TicketComposer {

    Composicion componer(List<PedidoItem> lineas, String nombreTienda, Map<String, Double> preciosExtras,
                         String metodoPago, int numeroPedido);

    /** Resultado de la composición: texto imprimible y total calculado. */
    final class Composicion {
        public final String texto;
        public final double total;

        public Composicion(String texto, double total) {
            this.texto = texto;
            this.total = total;
        }
    }
}
