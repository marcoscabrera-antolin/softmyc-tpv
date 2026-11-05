package com.softmyc.tpv.domain.usecase;

import com.softmyc.tpv.domain.escandallo.Ingrediente;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NEG-12: avisa de los ingredientes que no llegan para un pedido. Nunca bloquea la venta (igual que NEG-10
 * con el stock de producto): las existencias apuntadas pueden estar desfasadas y la cocina manda.
 */
public final class AvisoIngredientes {

    private AvisoIngredientes() { }

    /** Un ingrediente que falta. Inmutable. */
    public static final class Falta {
        public final String nombre;
        public final double disponible;
        public final double necesario;
        public final String unidad;

        Falta(String nombre, double disponible, double necesario, String unidad) {
            this.nombre = nombre;
            this.disponible = disponible;
            this.necesario = necesario;
            this.unidad = unidad;
        }

        /** "Pollo: quedan 120 g, se necesitan 333 g". */
        public String mensaje() {
            return String.format(Locale.getDefault(), "%s: quedan %s %s, se necesitan %s %s",
                    nombre, cantidad(disponible), unidad, cantidad(necesario), unidad);
        }
    }

    /**
     * @param necesario lo que gasta el pedido (id → cantidad), de {@link ConsumoIngredientes#calcular}
     * @return los ingredientes con control de stock que no llegan, en orden de id
     */
    public static List<Falta> evaluar(Map<Long, Double> necesario, Map<Long, Ingrediente> ingredientes) {
        List<Falta> faltas = new ArrayList<>();
        if (necesario == null || ingredientes == null) return faltas;
        for (Map.Entry<Long, Double> e : necesario.entrySet()) {
            Ingrediente ing = ingredientes.get(e.getKey());
            if (ing == null || !ing.controlaStock()) continue;
            double disponible = ing.stockVisible();
            if (e.getValue() > disponible + 1e-6) {
                faltas.add(new Falta(ing.nombre, disponible, e.getValue(), ing.unidad.simbolo()));
            }
        }
        return faltas;
    }

    /** Texto para el aviso: como mucho {@code maximo} ingredientes y "y N más". Vacío si no falta nada. */
    public static String resumen(List<Falta> faltas, int maximo) {
        if (faltas == null || faltas.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(maximo, faltas.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append('\n');
            sb.append(faltas.get(i).mensaje());
        }
        if (faltas.size() > n) sb.append("\ny ").append(faltas.size() - n).append(" más");
        return sb.toString();
    }

    /** Cantidad sin decimales si es entera ("120"), con uno si no ("12,5"). */
    static String cantidad(double v) {
        double redondo = Math.round(v * 10) / 10.0;
        return redondo == Math.rint(redondo)
                ? String.format(Locale.getDefault(), "%.0f", redondo)
                : String.format(Locale.getDefault(), "%.1f", redondo);
    }
}
