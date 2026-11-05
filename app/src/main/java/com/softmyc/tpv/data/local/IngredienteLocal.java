package com.softmyc.tpv.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Ingrediente del escandallo. stock null = sin control; puede ser negativo (descuadre entre receta y recuento). */
@Entity(tableName = "ingredientes", indices = {@Index(value = "nombreNormalizado", unique = true)})
public class IngredienteLocal {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String nombre = "";
    @NonNull
    public String nombreNormalizado = "";
    /** G, ML o UD. */
    @NonNull
    public String unidad = "G";
    public Double stock;
    /** Precio por kilo, litro o unidad. */
    public Double precioCompra;
}
