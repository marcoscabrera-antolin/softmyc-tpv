package com.softmyc.tpv.domain.usecase;

import com.softmyc.tpv.domain.PedidoItem;

import java.util.List;

/**
 * Comprueba si una cantidad supera las existencias de un producto (NEG-10). No bloquea la venta: solo
 * expone el dato para que la UI avise.
 *
 * Reglas (NEG-09 y NEG-10):
 *  - Solo las líneas en unidades discretas (UD, o unidad vacía de ventas antiguas) controlan stock.
 *    Las líneas al peso (KG) o por tiempo (HORA) no se comparan con el stock ni lo descuentan.
 *  - Stock negativo = sin control de inventario: nunca avisa.
 *  - Stock 0 o positivo: avisa si la cantidad solicitada es mayor que el stock.
 */
public final class StockAdvisor {

    private static final String UNIDAD_DISCRETA = "UD";

    private StockAdvisor() { }

    /** Resultado de la comprobación. Inmutable. */
    public static final class Aviso {
        private final int stockDisponible;
        private final int cantidadSolicitada;
        private final boolean controlado;

        private Aviso(int stockDisponible, int cantidadSolicitada, boolean controlado) {
            this.stockDisponible = stockDisponible;
            this.cantidadSolicitada = cantidadSolicitada;
            this.controlado = controlado;
        }

        /** True si la línea controla stock y la cantidad supera las existencias. */
        public boolean superaStock() {
            return controlado && cantidadSolicitada > existenciasNoNegativas();
        }

        /** Unidades que superan las existencias; 0 si no hay aviso. */
        public int getExcedente() {
            return superaStock() ? cantidadSolicitada - existenciasNoNegativas() : 0;
        }

        public int getStockDisponible() {
            return stockDisponible;
        }

        public int getCantidadSolicitada() {
            return cantidadSolicitada;
        }

        /** Texto para la UI; cadena vacía si no hay aviso. */
        public String mensaje() {
            if (!superaStock()) return "";
            return "Stock insuficiente: quedan " + existenciasNoNegativas() + " uds y se piden "
                    + cantidadSolicitada + " (exceso de " + getExcedente() + ")";
        }

        private int existenciasNoNegativas() {
            return Math.max(stockDisponible, 0);
        }
    }

    /**
     * @param stockDisponible    existencias del producto (-1 = sin control).
     * @param unidadMedida       unidad de la línea ("UD", "KG", "HORA"...).
     * @param cantidadSolicitada cantidad total que quedaría en el pedido para ese producto.
     */
    public static Aviso evaluar(int stockDisponible, String unidadMedida, int cantidadSolicitada) {
        boolean controlado = stockDisponible >= 0 && controlaStock(unidadMedida);
        return new Aviso(stockDisponible, cantidadSolicitada, controlado);
    }

    /** True si una línea con esta unidad descuenta y se compara con el stock. */
    public static boolean controlaStock(String unidadMedida) {
        return unidadMedida == null
                || unidadMedida.trim().isEmpty()
                || UNIDAD_DISCRETA.equalsIgnoreCase(unidadMedida.trim());
    }

    /** Suma las unidades discretas del pedido para un producto (las líneas al peso o por tiempo no cuentan). */
    public static int cantidadUdEnPedido(List<PedidoItem> pedido, int codigoProducto) {
        if (pedido == null) return 0;
        int total = 0;
        for (PedidoItem item : pedido) {
            if (item != null && item.getId() == codigoProducto && controlaStock(item.getUnidadMedida())) {
                total += item.getCantidad();
            }
        }
        return total;
    }
}
