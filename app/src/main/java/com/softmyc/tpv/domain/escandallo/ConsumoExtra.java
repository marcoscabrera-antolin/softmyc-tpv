package com.softmyc.tpv.domain.escandallo;

import com.softmyc.tpv.domain.NombreNormalizado;

/** Qué gasta un extra de la carta ("+ Queso" = 82 g de queso). */
public final class ConsumoExtra {
    /** Nombre del extra tal como sale en la carta. */
    public final String extra;
    public final long ingredienteId;
    public final double cantidad;

    public ConsumoExtra(String extra, long ingredienteId, double cantidad) {
        this.extra = extra;
        this.ingredienteId = ingredienteId;
        this.cantidad = cantidad;
    }

    public String extraNormalizado() {
        return NombreNormalizado.de(extra);
    }
}
