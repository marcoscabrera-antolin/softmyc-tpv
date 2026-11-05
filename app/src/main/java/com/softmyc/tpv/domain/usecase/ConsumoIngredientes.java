package com.softmyc.tpv.domain.usecase;

import com.softmyc.tpv.domain.NombreNormalizado;
import com.softmyc.tpv.domain.escandallo.ConsumoExtra;
import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.LineaConsumo;
import com.softmyc.tpv.domain.escandallo.LineaReceta;
import com.softmyc.tpv.domain.escandallo.Recetario;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Escandallo: qué ingredientes gasta una venta y cuánto cuestan.
 *
 * Reglas:
 *  - Cada unidad vendida gasta la receta de su producto.
 *  - Un ingrediente quitado ("sin tomate") no se gasta. Casa con el texto de la carta o con el nombre del
 *    ingrediente, sin distinguir mayúsculas ni acentos.
 *  - Cada extra ("+ queso") gasta lo que tenga enlazado, una vez por unidad vendida.
 *  - NEG-11: solo las líneas en unidades (UD) con código de producto gastan ingredientes. Las líneas al peso
 *    o por horas no tienen receta por unidad (igual que no descuentan stock de producto, NEG-09).
 */
public final class ConsumoIngredientes {

    /** Por debajo de esto una diferencia se considera ruido de coma flotante. */
    private static final double EPSILON = 1e-6;

    private ConsumoIngredientes() { }

    /** Cantidad gastada por ingrediente (id → cantidad en su unidad), ordenado por id. */
    public static Map<Long, Double> calcular(List<LineaConsumo> lineas, Recetario recetario) {
        Map<Long, Double> total = new TreeMap<>();
        if (lineas == null || recetario == null) return total;
        for (LineaConsumo linea : lineas) {
            if (!gastaIngredientes(linea)) continue;
            sumarLinea(linea, recetario, total);
        }
        for (Map.Entry<Long, Double> e : total.entrySet()) e.setValue(redondear(e.getValue()));
        return total;
    }

    /** nuevo − anterior por ingrediente, sin las diferencias nulas: lo que hay que descontar al editar una venta. */
    public static Map<Long, Double> diferencia(Map<Long, Double> nuevo, Map<Long, Double> anterior) {
        Map<Long, Double> d = new TreeMap<>();
        Set<Long> ids = new HashSet<>(nuevo.keySet());
        ids.addAll(anterior.keySet());
        for (Long id : ids) {
            double delta = redondear(nuevo.getOrDefault(id, 0.0) - anterior.getOrDefault(id, 0.0));
            if (Math.abs(delta) > EPSILON) d.put(id, delta);
        }
        return d;
    }

    /**
     * Coste de ingredientes de una línea (con quitados y extras), o null si no se puede saber: producto sin
     * receta, línea que no gasta ingredientes o algún ingrediente sin precio.
     */
    public static Double coste(LineaConsumo linea, Recetario recetario) {
        if (linea == null || recetario == null || !gastaIngredientes(linea) || !recetario.tieneReceta(linea.codigo)) {
            return null;
        }
        Map<Long, Double> gasto = new TreeMap<>();
        sumarLinea(linea, recetario, gasto);
        double coste = 0;
        for (Map.Entry<Long, Double> e : gasto.entrySet()) {
            Ingrediente ing = recetario.ingrediente(e.getKey());
            Double c = ing != null ? ing.coste(e.getValue()) : null;
            if (c == null) return null;
            coste += c;
        }
        return redondear(coste);
    }

    /** Coste de ingredientes de una unidad del producto tal como sale en la carta (sin quitados ni extras). */
    public static Double costeUnitario(int codigo, Recetario recetario) {
        return coste(new LineaConsumo(codigo, 1, "UD", null, null), recetario);
    }

    /** Gastos añadidos de una línea (por unidad × unidades), o null si el producto no los tiene definidos. */
    public static Double gastosAnadidos(LineaConsumo linea, Recetario recetario) {
        if (linea == null || recetario == null || !gastaIngredientes(linea)) return null;
        Double porUnidad = recetario.gastosAnadidos(linea.codigo);
        return porUnidad == null ? null : redondear(porUnidad * linea.cantidad);
    }

    static boolean gastaIngredientes(LineaConsumo linea) {
        return linea.codigo > 0 && linea.cantidad > 0 && StockAdvisor.controlaStock(linea.unidad);
    }

    private static void sumarLinea(LineaConsumo linea, Recetario recetario, Map<Long, Double> total) {
        Set<String> quitados = new HashSet<>();
        for (String q : linea.quitados) quitados.add(NombreNormalizado.de(q));

        for (LineaReceta r : recetario.receta(linea.codigo)) {
            if (estaQuitado(r, recetario.ingrediente(r.ingredienteId), quitados)) continue;
            total.merge(r.ingredienteId, r.cantidad * linea.cantidad, Double::sum);
        }
        for (String extra : linea.extras) {
            for (ConsumoExtra c : recetario.consumoExtra(NombreNormalizado.de(extra))) {
                total.merge(c.ingredienteId, c.cantidad * linea.cantidad, Double::sum);
            }
        }
    }

    private static boolean estaQuitado(LineaReceta r, Ingrediente ing, Set<String> quitados) {
        if (quitados.isEmpty()) return false;
        if (r.nombreEnCarta != null && quitados.contains(NombreNormalizado.de(r.nombreEnCarta))) return true;
        return ing != null && quitados.contains(ing.nombreNormalizado());
    }

    /** 3 decimales: gramos con precisión de miligramo, euros con precisión de décima de céntimo. */
    static double redondear(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
