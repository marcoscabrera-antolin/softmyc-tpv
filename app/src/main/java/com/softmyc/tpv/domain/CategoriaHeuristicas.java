package com.softmyc.tpv.domain;

import com.softmyc.tpv.domain.model.TaxRate;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Heurísticas explícitas que deducen la estrategia de tarificación y el IVA sugerido a partir del
 * nombre de la categoría o de la unidad de medida de un Producto (ARQ-14).
 *
 * Son un parche de compatibilidad: el modelo correcto guardaría la estrategia y el IVA como datos del
 * producto. Mientras tanto, cualquier cambio de palabra clave altera precios e impuestos, así que todas
 * viven en esta clase y no repartidas por Producto y el adaptador. Las palabras se comparan en minúsculas
 * y por subcadena.
 */
public final class CategoriaHeuristicas {

    /** Categorías que se venden al peso (estrategia WEIGHT). */
    static final List<String> PALABRAS_PESO = Arrays.asList("frut", "granel", "verdur", "peso");

    /**
     * Categorías de servicio por tiempo (estrategia TIME). Incluye "servicio", así que "Servicio de mesa"
     * también cae aquí: caso discutible conocido, fijado por test.
     */
    static final List<String> PALABRAS_HORAS = Arrays.asList("taller", "mano de obra", "reparac", "servicio");

    /** Categorías con descuentos por tramos de volumen (estrategia TIERED_VOLUME). */
    static final List<String> PALABRAS_VOLUMEN = Arrays.asList("mayorista", "distribuc", "volumen");

    /** Unidades de medida que implican venta al peso. */
    static final List<String> UNIDADES_PESO = Arrays.asList("KG", "G");

    /** Unidades de medida que implican venta por tiempo. */
    static final List<String> UNIDADES_HORAS = Arrays.asList("HORA", "H", "MIN");

    /** IVA superreducido 4 %: fruta y verdura. */
    public static final TaxRate IVA_PESO = TaxRate.SUPER_REDUCED_4;

    /** IVA general 21 %: talleres y servicios profesionales. */
    public static final TaxRate IVA_HORAS = TaxRate.GENERAL_21;

    /** IVA general 21 %: mayoristas. */
    public static final TaxRate IVA_VOLUMEN = TaxRate.GENERAL_21;

    /** IVA reducido 10 %: hostelería, valor por defecto. */
    public static final TaxRate IVA_DEFECTO = TaxRate.REDUCED_10;

    private CategoriaHeuristicas() { }

    public static boolean esPeso(String categoria) {
        return contieneAlguna(categoria, PALABRAS_PESO);
    }

    public static boolean esHoras(String categoria) {
        return contieneAlguna(categoria, PALABRAS_HORAS);
    }

    public static boolean esVolumen(String categoria) {
        return contieneAlguna(categoria, PALABRAS_VOLUMEN);
    }

    public static boolean esUnidadPeso(String unidadMedida) {
        return esCodigoDe(unidadMedida, UNIDADES_PESO);
    }

    public static boolean esUnidadHoras(String unidadMedida) {
        return esCodigoDe(unidadMedida, UNIDADES_HORAS);
    }

    private static boolean contieneAlguna(String texto, List<String> palabras) {
        if (texto == null) return false;
        String normalizado = texto.toLowerCase(Locale.ROOT).trim();
        for (String palabra : palabras) {
            if (normalizado.contains(palabra)) return true;
        }
        return false;
    }

    private static boolean esCodigoDe(String unidadMedida, List<String> codigos) {
        return unidadMedida != null && codigos.contains(unidadMedida.trim().toUpperCase(Locale.ROOT));
    }
}
