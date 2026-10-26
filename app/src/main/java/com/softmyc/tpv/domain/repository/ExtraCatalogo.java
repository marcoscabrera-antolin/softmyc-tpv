package com.softmyc.tpv.domain.repository;

/** Extra del catálogo con su precio, tal como lo necesitan las pantallas (sin tipos de Room). */
public final class ExtraCatalogo {
    public final String nombre;
    public final double precio;

    public ExtraCatalogo(String nombre, double precio) {
        this.nombre = nombre;
        this.precio = precio;
    }
}
