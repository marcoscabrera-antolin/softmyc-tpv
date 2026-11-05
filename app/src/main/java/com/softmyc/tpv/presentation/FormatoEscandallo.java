package com.softmyc.tpv.presentation;

import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.UnidadIngrediente;

import java.util.Locale;

/** Formato de cantidades y euros en las pantallas del escandallo. */
final class FormatoEscandallo {

    private FormatoEscandallo() { }

    static String euros(double v) {
        return String.format(Locale.getDefault(), "%.2f €", v);
    }

    static String porcentaje(double fraccion) {
        return String.format(Locale.getDefault(), "%.1f %%", fraccion * 100);
    }

    /** "333 g", "1,5 kg", "2 ud". */
    static String cantidad(double v, UnidadIngrediente unidad) {
        if (unidad != UnidadIngrediente.UD && Math.abs(v) >= 1000) {
            return numero(v / 1000.0) + (unidad == UnidadIngrediente.G ? " kg" : " L");
        }
        return numero(v) + " " + unidad.simbolo();
    }

    static String numero(double v) {
        double r = Math.round(v * 100) / 100.0;
        if (r == Math.rint(r)) return String.format(Locale.getDefault(), "%.0f", r);
        return String.format(Locale.getDefault(), "%.2f", r).replaceAll("0$", "");
    }

    /** "6,99 €/kg", "0,35 €/ud", o null si no tiene precio. */
    static String precio(Ingrediente i) {
        if (i.precioCompra == null) return null;
        String por = i.unidad == UnidadIngrediente.G ? "kg" : i.unidad == UnidadIngrediente.ML ? "L" : "ud";
        return String.format(Locale.getDefault(), "%.2f €/%s", i.precioCompra, por);
    }

    /** Admite coma o punto; vacío o no válido → null. */
    static Double leer(String texto) {
        if (texto == null) return null;
        String t = texto.trim().replace(',', '.');
        if (t.isEmpty()) return null;
        try {
            double v = Double.parseDouble(t);
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
