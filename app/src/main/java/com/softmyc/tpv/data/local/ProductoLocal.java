package com.softmyc.tpv.data.local;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import java.util.List;

@Entity(tableName = "productos")
public class ProductoLocal {
    @PrimaryKey
    public int codigo;
    public String nombre;
    public double precio;
    public String categoria;
    public List<String> ingredientes;

    public String unidadMedida = "UD";
    public int stock = -1;

    public ProductoLocal() {}

    @Ignore
    public ProductoLocal(int codigo, String nombre, double precio, String categoria, List<String> ingredientes) {
        this(codigo, nombre, precio, categoria, ingredientes, "UD");
    }

    @Ignore
    public ProductoLocal(int codigo, String nombre, double precio, String categoria, List<String> ingredientes, String unidadMedida) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.precio = precio;
        this.categoria = categoria;
        this.ingredientes = ingredientes;
        this.unidadMedida = (unidadMedida != null && !unidadMedida.trim().isEmpty()) ? unidadMedida.trim() : "UD";
    }
}
