package com.softmyc.tpv.domain.usecase;

import com.softmyc.tpv.domain.Producto;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Búsqueda de productos en la pantalla de pedido.
 *
 * Un producto coincide con el texto buscado si:
 *  - el texto es un número y es su código (ID rápido), o
 *  - su nombre contiene el texto, o
 *  - alguno de sus ingredientes contiene el texto (p. ej. "pollo" encuentra todo lo que lleva pollo).
 *
 * No distingue mayúsculas ni acentos ("cafe" encuentra "Café"). Si el texto tiene varias palabras, todas
 * deben aparecer en el nombre o en los ingredientes ("pollo queso" encuentra lo que lleva ambos).
 */
public final class BuscadorProductos {

    private BuscadorProductos() { }

    /** Devuelve, en el mismo orden, los productos que coinciden. Texto vacío o nulo: todos. */
    public static List<Producto> filtrar(List<Producto> productos, String texto) {
        List<Producto> resultado = new ArrayList<>();
        if (productos == null) return resultado;
        String consulta = normalizar(texto);
        for (Producto p : productos) {
            if (consulta.isEmpty() || coincide(p, consulta)) resultado.add(p);
        }
        return resultado;
    }

    private static boolean coincide(Producto p, String consulta) {
        if (esNumero(consulta)) {
            try {
                if (p.getCodigo() == Integer.parseInt(consulta)) return true;
            } catch (NumberFormatException ignorado) {
                // Número demasiado largo para ser un código: se sigue buscando como texto.
            }
        }
        StringBuilder texto = new StringBuilder(normalizar(p.getNombre()));
        if (p.getIngredientes() != null) {
            for (String ingrediente : p.getIngredientes()) {
                texto.append(' ').append(normalizar(ingrediente));
            }
        }
        String donde = texto.toString();
        for (String palabra : consulta.split("\\s+")) {
            if (!donde.contains(palabra)) return false;
        }
        return true;
    }

    private static boolean esNumero(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return !s.isEmpty();
    }

    /** Minúsculas, sin acentos y sin espacios sobrantes. */
    static String normalizar(String s) {
        if (s == null) return "";
        String sinAcentos = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
