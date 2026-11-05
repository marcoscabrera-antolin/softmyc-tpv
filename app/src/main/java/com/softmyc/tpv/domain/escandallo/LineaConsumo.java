package com.softmyc.tpv.domain.escandallo;

import com.softmyc.tpv.domain.PedidoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lo mínimo de una línea de venta que hace falta para saber qué ingredientes gasta. */
public final class LineaConsumo {
    public final int codigo;
    public final int cantidad;
    public final String unidad;
    public final List<String> quitados;
    public final List<String> extras;

    public LineaConsumo(int codigo, int cantidad, String unidad, List<String> quitados, List<String> extras) {
        this.codigo = codigo;
        this.cantidad = cantidad;
        this.unidad = unidad;
        this.quitados = quitados != null ? Collections.unmodifiableList(new ArrayList<>(quitados)) : Collections.emptyList();
        this.extras = extras != null ? Collections.unmodifiableList(new ArrayList<>(extras)) : Collections.emptyList();
    }

    public static LineaConsumo de(PedidoItem item) {
        return new LineaConsumo(item.getId(), item.getCantidad(), item.getUnidadMedida(),
                item.getIngredientesQuitados(), item.getIngredientesExtra());
    }
}
