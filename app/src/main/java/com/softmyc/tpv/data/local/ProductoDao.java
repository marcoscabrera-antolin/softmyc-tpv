package com.softmyc.tpv.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ProductoDao {
    @Query("SELECT * FROM productos ORDER BY nombre ASC")
    List<ProductoLocal> getAll();

    @Query("SELECT * FROM productos WHERE codigo = :codigo LIMIT 1")
    ProductoLocal getByCodigo(int codigo);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ProductoLocal producto);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ProductoLocal> productos);

    @Update
    void update(ProductoLocal producto);

    /** Edita solo los datos del formulario; stock y unidad no se tocan, así que no hay carrera con una venta. */
    @Query("UPDATE productos SET nombre = :nombre, precio = :precio, categoria = :categoria, ingredientes = :ingredientes WHERE codigo = :codigo")
    int actualizarDatosCatalogo(int codigo, String nombre, double precio, String categoria, List<String> ingredientes);

    @Delete
    void delete(ProductoLocal producto);

    /** Descuenta stock solo en productos con control activo (stock >= 0) y nunca por debajo de cero. */
    @Query("UPDATE productos SET stock = MAX(0, stock - :cantidad) WHERE codigo = :codigo AND stock >= 0")
    int decrementarStock(int codigo, int cantidad);

    /** Devuelve stock (p. ej. al quitar líneas de una venta editada) solo en productos con control activo. */
    @Query("UPDATE productos SET stock = stock + :cantidad WHERE codigo = :codigo AND stock >= 0")
    int incrementarStock(int codigo, int cantidad);

    @Query("UPDATE productos SET stock = :stock WHERE codigo = :codigo")
    int setStock(int codigo, int stock);

    @Query("DELETE FROM productos")
    void deleteAll();
}
