package com.softmyc.tpv.domain.usecase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cuánto se gana en un periodo.
 *
 *  - Ventas: lo cobrado (suma de los tickets).
 *  - Beneficio bruto: ventas − coste de ingredientes.
 *  - Beneficio neto: bruto − gastos añadidos (luz, gas, personal, envases…).
 *  - Margen: beneficio neto ÷ coste total (la misma base que usa el negocio para fijar precios).
 *
 * El beneficio solo se calcula con las líneas cuyo coste se conoce; las demás se cuentan aparte
 * ({@link Resumen#unidadesSinCoste}) para no presentar como beneficio lo que no se sabe si lo es.
 */
public final class CalculadoraRentabilidad {

    private CalculadoraRentabilidad() { }

    /** Una línea vendida. Costes de la línea entera (no por unidad); null = desconocido. */
    public static final class Linea {
        public final String producto;
        public final double unidades;
        public final double importe;
        public final Double costeIngredientes;
        public final Double gastosAnadidos;

        public Linea(String producto, double unidades, double importe, Double costeIngredientes, Double gastosAnadidos) {
            this.producto = producto;
            this.unidades = unidades;
            this.importe = importe;
            this.costeIngredientes = costeIngredientes;
            this.gastosAnadidos = gastosAnadidos;
        }

        boolean conCoste() {
            return costeIngredientes != null && gastosAnadidos != null;
        }
    }

    /** Beneficio de un producto en el periodo. */
    public static final class PorProducto {
        public final String producto;
        public final double unidades;
        public final double importe;
        /** null si ninguna de sus líneas tiene coste. */
        public final Double beneficioNeto;

        PorProducto(String producto, double unidades, double importe, Double beneficioNeto) {
            this.producto = producto;
            this.unidades = unidades;
            this.importe = importe;
            this.beneficioNeto = beneficioNeto;
        }
    }

    public static final class Resumen {
        public final double ventas;
        /** Importe de las líneas con coste conocido: la base del beneficio. */
        public final double ventasConCoste;
        public final double costeIngredientes;
        public final double gastosAnadidos;
        public final double beneficioBruto;
        public final double beneficioNeto;
        /** Beneficio neto ÷ coste total; null si no hay coste (no se divide entre 0). */
        public final Double margen;
        public final double unidadesSinCoste;
        /** Productos de más a menos beneficio neto; los que no tienen coste, al final por importe. */
        public final List<PorProducto> ranking;

        Resumen(double ventas, double ventasConCoste, double costeIngredientes, double gastosAnadidos,
                double unidadesSinCoste, List<PorProducto> ranking) {
            this.ventas = r2(ventas);
            this.ventasConCoste = r2(ventasConCoste);
            this.costeIngredientes = r2(costeIngredientes);
            this.gastosAnadidos = r2(gastosAnadidos);
            this.beneficioBruto = r2(ventasConCoste - costeIngredientes);
            this.beneficioNeto = r2(ventasConCoste - costeIngredientes - gastosAnadidos);
            double costeTotal = costeIngredientes + gastosAnadidos;
            this.margen = costeTotal > 0 ? (ventasConCoste - costeTotal) / costeTotal : null;
            this.unidadesSinCoste = unidadesSinCoste;
            this.ranking = Collections.unmodifiableList(ranking);
        }
    }

    /**
     * @param ventas lo cobrado en el periodo (suma de los totales de los tickets)
     * @param lineas las líneas de esos tickets que se pudieron leer
     */
    public static Resumen calcular(double ventas, List<Linea> lineas) {
        double ventasConCoste = 0, costeIng = 0, gastos = 0, sinCoste = 0;
        Map<String, double[]> acumulado = new LinkedHashMap<>(); // unidades, importe, beneficio, conCoste(0/1)
        if (lineas != null) {
            for (Linea l : lineas) {
                double[] a = acumulado.computeIfAbsent(l.producto, k -> new double[4]);
                a[0] += l.unidades;
                a[1] += l.importe;
                if (l.conCoste()) {
                    ventasConCoste += l.importe;
                    costeIng += l.costeIngredientes;
                    gastos += l.gastosAnadidos;
                    a[2] += l.importe - l.costeIngredientes - l.gastosAnadidos;
                    a[3] = 1;
                } else {
                    sinCoste += l.unidades;
                }
            }
        }
        List<PorProducto> ranking = new ArrayList<>();
        for (Map.Entry<String, double[]> e : acumulado.entrySet()) {
            double[] a = e.getValue();
            ranking.add(new PorProducto(e.getKey(), a[0], r2(a[1]), a[3] > 0 ? r2(a[2]) : null));
        }
        ranking.sort((x, y) -> {
            if (x.beneficioNeto != null && y.beneficioNeto != null) return Double.compare(y.beneficioNeto, x.beneficioNeto);
            if (x.beneficioNeto != null) return -1;
            if (y.beneficioNeto != null) return 1;
            return Double.compare(y.importe, x.importe);
        });
        return new Resumen(ventas, ventasConCoste, costeIng, gastos, sinCoste, ranking);
    }

    /** Rentabilidad de una unidad de producto a su precio de carta. */
    public static final class Unitario {
        public final double costeTotal;
        public final double beneficio;
        public final Double margen;

        Unitario(double costeTotal, double beneficio, Double margen) {
            this.costeTotal = costeTotal;
            this.beneficio = beneficio;
            this.margen = margen;
        }
    }

    /** null si no se conoce el coste de ingredientes. Sin gastos añadidos definidos, cuentan como 0. */
    public static Unitario unitario(double precio, Double costeIngredientes, Double gastosAnadidos) {
        if (costeIngredientes == null) return null;
        double coste = costeIngredientes + (gastosAnadidos != null ? gastosAnadidos : 0);
        double beneficio = precio - coste;
        return new Unitario(r2(coste), r2(beneficio), coste > 0 ? beneficio / coste : null);
    }

    private static double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
