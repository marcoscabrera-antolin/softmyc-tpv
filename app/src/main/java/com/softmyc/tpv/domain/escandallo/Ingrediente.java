package com.softmyc.tpv.domain.escandallo;

import com.softmyc.tpv.domain.NombreNormalizado;

/** Materia prima con la que se preparan los productos (pollo, pan, queso…). Inmutable. */
public final class Ingrediente {
    public final long id;
    public final String nombre;
    public final UnidadIngrediente unidad;
    /** Existencias en la unidad del ingrediente; null = no se controla su stock. Puede ser negativo (descuadre). */
    public final Double stock;
    /** Precio por kilo, litro o unidad; null = desconocido. */
    public final Double precioCompra;

    public Ingrediente(long id, String nombre, UnidadIngrediente unidad, Double stock, Double precioCompra) {
        this.id = id;
        this.nombre = nombre;
        this.unidad = unidad != null ? unidad : UnidadIngrediente.G;
        this.stock = stock;
        this.precioCompra = precioCompra;
    }

    public String nombreNormalizado() {
        return NombreNormalizado.de(nombre);
    }

    public boolean controlaStock() {
        return stock != null;
    }

    /** Existencias que se enseñan: nunca por debajo de 0 (el descuadre se muestra aparte). */
    public double stockVisible() {
        return stock == null ? 0 : Math.max(0, stock);
    }

    /** Coste de una cantidad de este ingrediente, o null si no tiene precio. */
    public Double coste(double cantidad) {
        return precioCompra == null ? null : unidad.coste(cantidad, precioCompra);
    }

    public Ingrediente conStock(Double nuevoStock) {
        return new Ingrediente(id, nombre, unidad, nuevoStock, precioCompra);
    }
}
