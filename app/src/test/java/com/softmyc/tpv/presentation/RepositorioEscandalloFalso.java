package com.softmyc.tpv.presentation;

import com.softmyc.tpv.domain.NombreNormalizado;
import com.softmyc.tpv.domain.escandallo.ConsumoExtra;
import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.InformeImportacion;
import com.softmyc.tpv.domain.escandallo.LineaReceta;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.escandallo.UnidadIngrediente;
import com.softmyc.tpv.domain.repository.EscandalloRepository;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Escandallo en memoria para probar ViewModel y pantallas sin Room. */
public class RepositorioEscandalloFalso implements EscandalloRepository {
    public final Map<Long, Ingrediente> ingredientes = new LinkedHashMap<>();
    public final List<LineaReceta> lineas = new ArrayList<>();
    public final List<ConsumoExtra> extras = new ArrayList<>();
    public final Map<Integer, Double> gastos = new HashMap<>();
    public final List<ProductoCatalogo> productos = new ArrayList<>();
    public final List<String> nombresExtras = new ArrayList<>(Arrays.asList("Queso", "Bacon"));
    public RuntimeException fallo;
    public InformeImportacion informe = new InformeImportacion(2, 1, 1, 1, Collections.singletonList("REC;x (malo)"));
    private long siguiente = 100;

    public static RepositorioEscandalloFalso conNube() {
        RepositorioEscandalloFalso r = new RepositorioEscandalloFalso();
        r.ingredientes.put(1L, new Ingrediente(1, "Pollo", UnidadIngrediente.G, 1000.0, 6.99));
        r.ingredientes.put(2L, new Ingrediente(2, "Pan", UnidadIngrediente.UD, -2.0, 0.35));
        r.ingredientes.put(3L, new Ingrediente(3, "Roquefort", UnidadIngrediente.G, null, null));
        r.lineas.add(new LineaReceta(1, 1, 333.333, "Pollo"));
        r.lineas.add(new LineaReceta(1, 2, 1, null));
        r.extras.add(new ConsumoExtra("Queso", 1, 50));
        r.gastos.put(1, 1.69);
        r.productos.add(new ProductoCatalogo(1, "Nube", 5.5, "Bocadillos", Arrays.asList("Pollo", "Salsa"), "UD", -1));
        r.productos.add(new ProductoCatalogo(9, "Tigger", 4.5, "Bocadillos", Arrays.asList("Lomo", "Roquefort"), "UD", -1));
        return r;
    }

    @Override public Recetario recetario() {
        if (fallo != null) throw fallo;
        return new Recetario(new ArrayList<>(ingredientes.values()), lineas, extras, gastos);
    }
    @Override public List<ProductoCatalogo> productos() { return productos; }
    @Override public List<String> nombresExtras() { return nombresExtras; }

    @Override public long guardarIngrediente(Ingrediente i) {
        for (Ingrediente otro : ingredientes.values()) {
            if (otro.id != i.id && otro.nombreNormalizado().equals(NombreNormalizado.de(i.nombre))) {
                throw new IllegalArgumentException("Ya existe un ingrediente llamado " + otro.nombre);
            }
        }
        long id = i.id == 0 ? siguiente++ : i.id;
        Ingrediente previo = ingredientes.get(id);
        ingredientes.put(id, new Ingrediente(id, i.nombre, i.unidad, previo != null ? previo.stock : i.stock, i.precioCompra));
        return id;
    }
    @Override public void eliminarIngrediente(long id) { ingredientes.remove(id); lineas.removeIf(l -> l.ingredienteId == id); }
    @Override public void fijarStock(long id, Double stock) { ingredientes.put(id, ingredientes.get(id).conStock(stock)); }
    @Override public void guardarReceta(int codigo, List<LineaReceta> nuevas) {
        lineas.removeIf(l -> l.productoCodigo == codigo);
        lineas.addAll(nuevas);
    }
    @Override public void guardarConsumoExtra(String extra, List<ConsumoExtra> consumos) {
        extras.removeIf(c -> c.extraNormalizado().equals(NombreNormalizado.de(extra)));
        extras.addAll(consumos);
    }
    @Override public void fijarGastosAnadidos(int codigo, Double g) {
        if (g == null) gastos.remove(codigo); else gastos.put(codigo, g);
    }
    @Override public InformeImportacion importar(InputStream csv) { return informe; }
}
