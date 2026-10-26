package com.softmyc.tpv.data.printer;

import java.util.ArrayList;
import java.util.List;

/**
 * Ajusta el texto de una línea al ancho del papel. Corta en espacios cuando puede y, si una palabra
 * no cabe ni en una línea entera, la parte. Cada línea lleva su sangría: la primera y las de continuación.
 */
final class AjusteLineasTicket {

    private static final int ANCHO_MINIMO = 1;

    private AjusteLineasTicket() {
    }

    /**
     * @param texto              texto completo (los espacios repetidos se colapsan)
     * @param columnas           ancho del papel en caracteres
     * @param sangriaPrimera     prefijo de la primera línea
     * @param sangriaContinuacion prefijo de las líneas siguientes
     * @return líneas ya ajustadas; vacía si el texto no tiene contenido
     */
    static List<String> envolver(String texto, int columnas, String sangriaPrimera, String sangriaContinuacion) {
        List<String> contenido = new ArrayList<>();
        String actual = "";
        for (String palabra : texto.trim().split("\\s+")) {
            if (palabra.isEmpty()) {
                continue;
            }
            String candidato = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (candidato.length() <= anchoDeLinea(contenido.size(), columnas, sangriaPrimera, sangriaContinuacion)) {
                actual = candidato;
            } else {
                if (!actual.isEmpty()) {
                    contenido.add(actual);
                }
                actual = palabra;
            }
            actual = partirMientrasNoCabe(actual, contenido, columnas, sangriaPrimera, sangriaContinuacion);
        }
        if (!actual.isEmpty()) {
            contenido.add(actual);
        }
        return conSangria(contenido, sangriaPrimera, sangriaContinuacion);
    }

    /** Añade al listado los trozos de {@code palabra} que no caben en la línea actual y devuelve el resto. */
    private static String partirMientrasNoCabe(String palabra, List<String> contenido, int columnas,
            String sangriaPrimera, String sangriaContinuacion) {
        String resto = palabra;
        while (resto.length() > anchoDeLinea(contenido.size(), columnas, sangriaPrimera, sangriaContinuacion)) {
            int corte = anchoDeLinea(contenido.size(), columnas, sangriaPrimera, sangriaContinuacion);
            contenido.add(resto.substring(0, corte));
            resto = resto.substring(corte);
        }
        return resto;
    }

    private static int anchoDeLinea(int indice, int columnas, String sangriaPrimera, String sangriaContinuacion) {
        int ancho = columnas - (indice == 0 ? sangriaPrimera : sangriaContinuacion).length();
        return Math.max(ANCHO_MINIMO, ancho);
    }

    private static List<String> conSangria(List<String> contenido, String sangriaPrimera, String sangriaContinuacion) {
        List<String> lineas = new ArrayList<>(contenido.size());
        for (int i = 0; i < contenido.size(); i++) {
            lineas.add((i == 0 ? sangriaPrimera : sangriaContinuacion) + contenido.get(i));
        }
        return lineas;
    }
}
