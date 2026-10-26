package com.softmyc.tpv.domain.repository;

import java.util.List;

/** Producto del catálogo local tal como lo necesitan las pantallas (sin tipos de Room). */
public final class ProductoCatalogo {

    public final int codigo;
    public final String nombre;
    public final double precio;
    public final String categoria;
    public final List<String> ingredientes;
    public final String unidadMedida;
    /** Unidades en stock; -1 = sin control de stock. */
    public final int stock;

    public ProductoCatalogo(int codigo, String nombre, double precio, String categoria, List<String> ingredientes,
                            String unidadMedida, int stock) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.precio = precio;
        this.categoria = categoria;
        this.ingredientes = ingredientes;
        this.unidadMedida = unidadMedida;
        this.stock = stock;
    }

    /** Copia del producto con otro stock (el objeto no cambia). */
    public ProductoCatalogo conStock(int nuevoStock) {
        return new ProductoCatalogo(codigo, nombre, precio, categoria, ingredientes, unidadMedida, nuevoStock);
    }
}
