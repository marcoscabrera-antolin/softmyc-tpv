package com.softmyc.tpv.domain.escandallo;

/** Cuánto lleva un producto de un ingrediente, por unidad vendida. */
public final class LineaReceta {
    public final int productoCodigo;
    public final long ingredienteId;
    public final double cantidad;
    /**
     * Texto con el que aparece en la carta (p. ej. "Tomate Nat"). Es lo que llega como "quitado" cuando el
     * cliente pide un producto sin ese ingrediente; null si la línea no sale en la carta (el pan, por ejemplo).
     */
    public final String nombreEnCarta;

    public LineaReceta(int productoCodigo, long ingredienteId, double cantidad, String nombreEnCarta) {
        this.productoCodigo = productoCodigo;
        this.ingredienteId = ingredienteId;
        this.cantidad = cantidad;
        this.nombreEnCarta = nombreEnCarta;
    }
}
