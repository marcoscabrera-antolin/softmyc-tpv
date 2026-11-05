package com.softmyc.tpv.data.local;

import com.softmyc.tpv.domain.escandallo.LineaConsumo;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.usecase.ConsumoIngredientes;

import java.util.List;
import java.util.Map;

/**
 * Descuenta del stock de ingredientes lo que gasta una venta (o, al editarla, solo la diferencia).
 * Se llama dentro de la transacción que guarda la venta: o se guardan las dos cosas o ninguna.
 */
public final class IngredienteStockRegistrar {

    private IngredienteStockRegistrar() { }

    public static void aplicar(AppDatabase db, Recetario recetario, List<LineaConsumo> nuevas, List<LineaConsumo> previas) {
        Map<Long, Double> delta = ConsumoIngredientes.diferencia(
                ConsumoIngredientes.calcular(nuevas, recetario),
                ConsumoIngredientes.calcular(previas, recetario));
        // Orden fijo por id (TreeMap): dos transacciones nunca bloquean los ingredientes en orden distinto.
        for (Map.Entry<Long, Double> e : delta.entrySet()) {
            db.escandalloDao().ajustarStock(e.getKey(), -e.getValue());
        }
    }
}
