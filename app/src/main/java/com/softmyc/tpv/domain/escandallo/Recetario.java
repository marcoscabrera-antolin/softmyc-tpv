package com.softmyc.tpv.domain.escandallo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Foto inmutable del escandallo de un negocio: ingredientes, receta de cada producto, lo que gasta cada extra
 * y los gastos añadidos (luz, gas, personal…) por unidad de cada producto.
 */
public final class Recetario {

    public static final Recetario VACIO = new Recetario(Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyMap());

    private final Map<Long, Ingrediente> ingredientes;
    private final Map<Integer, List<LineaReceta>> recetas;
    private final Map<String, List<ConsumoExtra>> extras;
    private final Map<Integer, Double> gastosAnadidos;

    public Recetario(List<Ingrediente> ingredientes, List<LineaReceta> lineas, List<ConsumoExtra> consumosExtra,
                     Map<Integer, Double> gastosAnadidos) {
        Map<Long, Ingrediente> ing = new TreeMap<>();
        for (Ingrediente i : ingredientes) ing.put(i.id, i);
        Map<Integer, List<LineaReceta>> rec = new LinkedHashMap<>();
        for (LineaReceta l : lineas) rec.computeIfAbsent(l.productoCodigo, k -> new ArrayList<>()).add(l);
        Map<String, List<ConsumoExtra>> ext = new LinkedHashMap<>();
        for (ConsumoExtra c : consumosExtra) ext.computeIfAbsent(c.extraNormalizado(), k -> new ArrayList<>()).add(c);
        this.ingredientes = Collections.unmodifiableMap(ing);
        this.recetas = Collections.unmodifiableMap(rec);
        this.extras = Collections.unmodifiableMap(ext);
        this.gastosAnadidos = Collections.unmodifiableMap(new LinkedHashMap<>(gastosAnadidos));
    }

    public Map<Long, Ingrediente> ingredientes() { return ingredientes; }

    public Ingrediente ingrediente(long id) { return ingredientes.get(id); }

    /** Receta del producto; lista vacía si no tiene. */
    public List<LineaReceta> receta(int codigo) {
        List<LineaReceta> r = recetas.get(codigo);
        return r != null ? Collections.unmodifiableList(r) : Collections.emptyList();
    }

    public boolean tieneReceta(int codigo) {
        return recetas.containsKey(codigo);
    }

    /** Lo que gasta un extra (por nombre normalizado); lista vacía si no está enlazado a ningún ingrediente. */
    public List<ConsumoExtra> consumoExtra(String extraNormalizado) {
        List<ConsumoExtra> c = extras.get(extraNormalizado);
        return c != null ? Collections.unmodifiableList(c) : Collections.emptyList();
    }

    public Map<String, List<ConsumoExtra>> extras() { return extras; }

    /** Gastos añadidos por unidad del producto, o null si no están definidos. */
    public Double gastosAnadidos(int codigo) {
        return gastosAnadidos.get(codigo);
    }
}
