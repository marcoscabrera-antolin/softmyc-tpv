package com.softmyc.tpv.domain.usecase;

import com.softmyc.tpv.util.CategoryUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Arqueo de cocina (centro de costes de cocina): ingresos brutos de las líneas de venta cuyo
 * producto pertenece a una categoría marcada como "cocina", con desglose por forma de pago y producto.
 */
public final class ArqueoCocinaCalculator {

    /** Categorías de cocina por defecto cuando el negocio aún no ha configurado ninguna. */
    public static final Set<String> CATEGORIAS_POR_DEFECTO = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("Bocadillos", "Hamburguesas", "Sandwiches")));

    private ArqueoCocinaCalculator() { }

    /** Venta reducida a lo necesario para el arqueo. */
    public static final class Venta {
        final String metodoPago;
        final String itemsJson;

        public Venta(String metodoPago, String itemsJson) {
            this.metodoPago = metodoPago;
            this.itemsJson = itemsJson;
        }
    }

    public static final class LineaProducto {
        public final String nombre;
        public final int unidades;
        public final double importe;

        LineaProducto(String nombre, int unidades, double importe) {
            this.nombre = nombre;
            this.unidades = unidades;
            this.importe = importe;
        }
    }

    public static final class Resultado {
        public final double totalCocina;
        public final double efectivo;
        public final double tarjeta;
        public final int tickets;
        public final List<LineaProducto> productos;

        Resultado(double totalCocina, double efectivo, double tarjeta, int tickets, List<LineaProducto> productos) {
            this.totalCocina = totalCocina;
            this.efectivo = efectivo;
            this.tarjeta = tarjeta;
            this.tickets = tickets;
            this.productos = productos;
        }
    }

    /**
     * @param categoriasCocina       categorías que cuentan como cocina (se comparan normalizadas).
     * @param categoriaPorNombreMin  categoría del catálogo por nombre de producto en minúsculas,
     *                               usada cuando la línea guardada no incluye categoría (ventas antiguas).
     */
    public static Resultado calcular(List<Venta> ventas, Set<String> categoriasCocina,
                                     Map<String, String> categoriaPorNombreMin) {
        Set<String> cocina = normalizar(categoriasCocina);
        double efectivo = 0;
        double tarjeta = 0;
        int tickets = 0;
        Map<String, double[]> porProducto = new LinkedHashMap<>(); // nombre -> {unidades, importe}

        for (Venta venta : ventas) {
            double cocinaDeLaVenta = 0;
            boolean hayCocina = false;
            JSONArray lineas = parsear(venta.itemsJson);
            for (int i = 0; i < lineas.length(); i++) {
                JSONObject linea = lineas.optJSONObject(i);
                if (linea == null) continue;
                String nombre = linea.optString("nombre", "");
                if (!esCocina(linea, nombre, cocina, categoriaPorNombreMin)) continue;

                int cantidad = linea.optInt("cantidad", 0);
                double importe = linea.has("importe_linea")
                        ? linea.optDouble("importe_linea", 0.0)
                        : cantidad * linea.optDouble("precio_unitario", 0.0);
                hayCocina = true;
                cocinaDeLaVenta += importe;
                double[] acumulado = porProducto.computeIfAbsent(nombre, k -> new double[2]);
                acumulado[0] += cantidad;
                acumulado[1] += importe;
            }
            if (hayCocina) {
                tickets++;
                if ("Tarjeta".equalsIgnoreCase(venta.metodoPago)) tarjeta += cocinaDeLaVenta;
                else efectivo += cocinaDeLaVenta;
            }
        }

        List<LineaProducto> productos = new ArrayList<>();
        for (Map.Entry<String, double[]> e : porProducto.entrySet()) {
            productos.add(new LineaProducto(e.getKey(), (int) e.getValue()[0], e.getValue()[1]));
        }
        productos.sort((a, b) -> Double.compare(b.importe, a.importe));
        return new Resultado(efectivo + tarjeta, efectivo, tarjeta, tickets, productos);
    }

    private static boolean esCocina(JSONObject linea, String nombre, Set<String> cocina,
                                    Map<String, String> categoriaPorNombreMin) {
        if (cocina.isEmpty()) return false;
        String categoria = linea.optString("categoria", "");
        if (categoria.isEmpty() && categoriaPorNombreMin != null) {
            String delCatalogo = categoriaPorNombreMin.get(nombre.toLowerCase(Locale.ROOT));
            categoria = delCatalogo != null ? delCatalogo : "";
        }
        if (categoria.isEmpty()) return false;
        return cocina.contains(CategoryUtils.capitalizeCategory(categoria).toLowerCase(Locale.ROOT));
    }

    private static Set<String> normalizar(Set<String> categorias) {
        Set<String> result = new HashSet<>();
        if (categorias != null) {
            for (String c : categorias) {
                result.add(CategoryUtils.capitalizeCategory(c).toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static JSONArray parsear(String json) {
        try {
            return new JSONArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** Texto ESC/POS del arqueo de cocina. */
    public static String formatearTicket(Resultado r, String establecimiento, String periodo,
                                         String fechaEmision, int cols) {
        String divider = repetir("-", cols);
        StringBuilder sb = new StringBuilder();
        sb.append(divider).append("\n");
        sb.append(centrar("ARQUEO DE COCINA", cols)).append("\n");
        sb.append(divider).append("\n");
        sb.append("FECHA EMISION: ").append(fechaEmision).append("\n");
        sb.append("ESTABLECIMIENTO: ").append(establecimiento.toUpperCase(Locale.ROOT)).append("\n");
        sb.append("PERIODO: ").append(periodo).append("\n");
        sb.append(divider).append("\n\n");
        sb.append("TICKETS CON COCINA: ").append(r.tickets).append("\n");
        sb.append(String.format(Locale.US, "TOTAL COCINA: %.2f EUR%n%n", r.totalCocina));
        sb.append("DESGLOSE DE PAGO:\n");
        sb.append(String.format(Locale.US, "- EFECTIVO: %.2f EUR%n", r.efectivo));
        sb.append(String.format(Locale.US, "- TARJETA:  %.2f EUR%n", r.tarjeta));
        sb.append(divider).append("\n");
        sb.append("PRODUCTOS DE COCINA:\n");
        for (LineaProducto p : r.productos) {
            sb.append(String.format(Locale.US, "%dx %s  %.2f EUR%n", p.unidades, p.nombre, p.importe));
        }
        sb.append(divider).append("\n\n");
        return sb.toString();
    }

    private static String centrar(String text, int width) {
        int pad = (width - text.length()) / 2;
        return pad <= 0 ? text : repetir(" ", pad) + text;
    }

    private static String repetir(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}
