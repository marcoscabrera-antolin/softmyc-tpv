package com.softmyc.tpv.domain.escandallo;

import java.util.Locale;

/**
 * Unidad en la que se mide un ingrediente. Gramos y mililitros se compran por kilo o litro; las unidades
 * (pan, huevo, raciones ya preparadas) se compran por pieza.
 */
public enum UnidadIngrediente {
    G("g", 1000.0),
    ML("ml", 1000.0),
    UD("ud", 1.0);

    private final String simbolo;
    /** Cuántas unidades de medida hay en la unidad de compra (1 kg = 1000 g; 1 ud = 1 ud). */
    private final double porUnidadCompra;

    UnidadIngrediente(String simbolo, double porUnidadCompra) {
        this.simbolo = simbolo;
        this.porUnidadCompra = porUnidadCompra;
    }

    public String simbolo() { return simbolo; }

    /** Coste de una cantidad sabiendo el precio por kilo, litro o unidad. */
    public double coste(double cantidad, double precioPorUnidadCompra) {
        return cantidad * precioPorUnidadCompra / porUnidadCompra;
    }

    /** "g", "G", "gramos"… → G. Desconocido o vacío → G (lo habitual en cocina). */
    public static UnidadIngrediente desde(String texto) {
        if (texto == null) return G;
        String t = texto.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("ML") || t.startsWith("L")) return ML;
        if (t.startsWith("U") || t.startsWith("RAC") || t.startsWith("PZ")) return UD;
        return G;
    }
}
