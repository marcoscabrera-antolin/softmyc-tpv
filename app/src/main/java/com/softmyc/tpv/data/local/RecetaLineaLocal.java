package com.softmyc.tpv.data.local;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Línea de receta. Sin clave foránea a productos a propósito: la sincronización del catálogo borra y vuelve a
 * insertar los productos, y una cascada se llevaría por delante todas las recetas.
 */
@Entity(tableName = "receta_lineas",
        foreignKeys = @ForeignKey(entity = IngredienteLocal.class, parentColumns = "id", childColumns = "ingredienteId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("productoCodigo"), @Index("ingredienteId")})
public class RecetaLineaLocal {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public int productoCodigo;
    public long ingredienteId;
    public double cantidad;
    public String nombreEnCarta;
}
