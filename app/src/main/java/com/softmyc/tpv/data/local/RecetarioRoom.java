package com.softmyc.tpv.data.local;

import com.softmyc.tpv.domain.escandallo.ConsumoExtra;
import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.LineaReceta;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.escandallo.UnidadIngrediente;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Traduce las tablas del escandallo de Room al modelo de dominio. */
public final class RecetarioRoom {

    private RecetarioRoom() { }

    public static Recetario cargar(AppDatabase db) {
        EscandalloDao dao = db.escandalloDao();
        List<Ingrediente> ingredientes = new ArrayList<>();
        for (IngredienteLocal i : dao.getIngredientes()) ingredientes.add(aDominio(i));
        List<LineaReceta> lineas = new ArrayList<>();
        for (RecetaLineaLocal l : dao.getRecetaLineas()) {
            lineas.add(new LineaReceta(l.productoCodigo, l.ingredienteId, l.cantidad, l.nombreEnCarta));
        }
        List<ConsumoExtra> extras = new ArrayList<>();
        for (ExtraConsumoLocal e : dao.getExtraConsumos()) extras.add(new ConsumoExtra(e.extra, e.ingredienteId, e.cantidad));
        Map<Integer, Double> gastos = new HashMap<>();
        for (ProductoLocal p : db.productoDao().getAll()) {
            if (p.gastosAnadidos != null) gastos.put(p.codigo, p.gastosAnadidos);
        }
        return new Recetario(ingredientes, lineas, extras, gastos);
    }

    public static Ingrediente aDominio(IngredienteLocal i) {
        return new Ingrediente(i.id, i.nombre, UnidadIngrediente.desde(i.unidad), i.stock, i.precioCompra);
    }
}
