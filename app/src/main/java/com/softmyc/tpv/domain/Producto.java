package com.softmyc.tpv.domain;

import java.util.List;

public class Producto {
    private int codigo;
    private String nombre;
    private double precio;
    private String categoria;
    private List<String> ingredientes; // ¡Importante! Debe ser lista

    private String unidadMedida = "UD";

    // Constructor vacío obligatorio para la deserialización JSON (Gson)
    public Producto() {}

    public Producto(int codigo, String nombre, double precio, String categoria, List<String> ingredientes) {
        this(codigo, nombre, precio, categoria, ingredientes, "UD");
    }

    public Producto(int codigo, String nombre, double precio, String categoria, List<String> ingredientes, String unidadMedida) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.precio = precio;
        this.categoria = categoria;
        this.ingredientes = ingredientes;
        this.unidadMedida = unidadMedida != null ? unidadMedida : "UD";
    }

    // Getters y Setters
    public int getCodigo() { return codigo; }
    public void setCodigo(int codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public double getPrecio() { return precio; }
    public void setPrecio(double precio) { this.precio = precio; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public List<String> getIngredientes() { return ingredientes; }
    public void setIngredientes(List<String> ingredientes) { this.ingredientes = ingredientes; }

    public String getUnidadMedida() { return unidadMedida != null ? unidadMedida : "UD"; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    /**
     * Unidad con la que se vende de verdad: la del producto o, si es "UD" pero su categoría es de peso u
     * horas (la misma regla que usa el motor de precios), kg u horas. Así el diálogo y el motor coinciden.
     */
    public String getUnidadEfectiva() {
        if (!"UD".equalsIgnoreCase(getUnidadMedida())) return getUnidadMedida();
        if (isWeightBased()) return "KG";
        if (isTimeBased()) return "HORA";
        return "UD";
    }

    /** Clasificación agnóstica de sector de negocio para la Fase 2. Reglas en {@link CategoriaHeuristicas}. */
    public boolean isWeightBased() {
        return CategoriaHeuristicas.esUnidadPeso(unidadMedida) || CategoriaHeuristicas.esPeso(categoria);
    }

    public boolean isTimeBased() {
        return CategoriaHeuristicas.esUnidadHoras(unidadMedida) || CategoriaHeuristicas.esHoras(categoria);
    }

    public boolean isVolumeTiered() {
        return CategoriaHeuristicas.esVolumen(categoria);
    }
}