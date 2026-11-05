package com.softmyc.tpv.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class EscandalloDao {

    @Query("SELECT * FROM ingredientes ORDER BY nombre COLLATE NOCASE")
    public abstract List<IngredienteLocal> getIngredientes();

    @Query("SELECT * FROM ingredientes WHERE id = :id")
    public abstract IngredienteLocal getIngrediente(long id);

    @Query("SELECT * FROM ingredientes WHERE nombreNormalizado = :nombreNormalizado")
    public abstract IngredienteLocal getIngredientePorNombre(String nombreNormalizado);

    @Query("SELECT COUNT(*) FROM ingredientes")
    public abstract int contarIngredientes();

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract long insertarIngrediente(IngredienteLocal ingrediente);

    /** Edita nombre, unidad y precio; el stock va aparte para no pisar una venta en curso. */
    @Query("UPDATE ingredientes SET nombre = :nombre, nombreNormalizado = :nombreNormalizado, unidad = :unidad, "
            + "precioCompra = :precioCompra WHERE id = :id")
    public abstract int actualizarIngrediente(long id, String nombre, String nombreNormalizado, String unidad, Double precioCompra);

    @Query("DELETE FROM ingredientes WHERE id = :id")
    public abstract int eliminarIngrediente(long id);

    /** Suma (o resta) existencias solo si el ingrediente tiene control de stock. Sin recorte a 0: ver NEG-12. */
    @Query("UPDATE ingredientes SET stock = ROUND(stock + :delta, 3) WHERE id = :id AND stock IS NOT NULL")
    public abstract int ajustarStock(long id, double delta);

    /** Recuento: fija las existencias (null = dejar de controlar). */
    @Query("UPDATE ingredientes SET stock = :stock WHERE id = :id")
    public abstract int setStock(long id, Double stock);

    @Query("SELECT * FROM receta_lineas ORDER BY productoCodigo, id")
    public abstract List<RecetaLineaLocal> getRecetaLineas();

    @Query("SELECT * FROM receta_lineas WHERE productoCodigo = :codigo ORDER BY id")
    public abstract List<RecetaLineaLocal> getReceta(int codigo);

    @Query("DELETE FROM receta_lineas WHERE productoCodigo = :codigo")
    abstract void borrarReceta(int codigo);

    @Insert
    abstract void insertarLineas(List<RecetaLineaLocal> lineas);

    @Transaction
    public void reemplazarReceta(int codigo, List<RecetaLineaLocal> lineas) {
        borrarReceta(codigo);
        for (RecetaLineaLocal l : lineas) l.productoCodigo = codigo;
        if (!lineas.isEmpty()) insertarLineas(lineas);
    }

    @Query("SELECT * FROM extra_consumos ORDER BY extraNormalizado, id")
    public abstract List<ExtraConsumoLocal> getExtraConsumos();

    @Query("DELETE FROM extra_consumos WHERE extraNormalizado = :extraNormalizado")
    abstract void borrarExtraConsumo(String extraNormalizado);

    @Insert
    abstract void insertarExtraConsumos(List<ExtraConsumoLocal> consumos);

    @Transaction
    public void reemplazarExtraConsumo(String extraNormalizado, List<ExtraConsumoLocal> consumos) {
        borrarExtraConsumo(extraNormalizado);
        if (!consumos.isEmpty()) insertarExtraConsumos(consumos);
    }
}
