package com.softmyc.tpv.domain.repository;

import java.util.List;

/**
 * Contrato del catálogo local de productos. La capa domain define QUÉ se necesita; la capa data implementa CÓMO.
 * Los métodos devuelven modelos simples, nunca entidades de Room.
 */
public interface ProductoRepository {

    /** Todos los productos, ordenados por nombre. */
    List<ProductoCatalogo> listarTodos();

    /** Producto con ese código, o null si no existe. */
    ProductoCatalogo buscarPorCodigo(int codigo);

    /** Inserta (o sustituye si ya existe el código) un producto. */
    void guardar(ProductoCatalogo producto);

    /**
     * Actualiza nombre, precio, categoría e ingredientes de un producto existente.
     * @return número de filas afectadas (0 si el producto ya no existe).
     */
    int actualizarDatos(ProductoCatalogo producto);

    /** Actualiza solo el stock de un producto. */
    void actualizarStock(int codigo, int stock);

    /** Elimina el producto con ese código si existe. */
    void eliminar(int codigo);

    /** Nombres de las salsas no vacíos, ordenados por nombre. */
    List<String> listarNombresSalsas();
}
