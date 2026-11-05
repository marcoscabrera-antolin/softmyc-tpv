package com.softmyc.tpv.data.local;

import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.escandallo.LineaConsumo;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.usecase.StockAdvisor;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Guarda una venta y, en la misma transacción de Room, ajusta el stock de sus productos y de los ingredientes
 * que gasta (escandallo) y anota en cada línea su coste del momento.
 */
public final class VentaStockRegistrar {

    private VentaStockRegistrar() { }

    /**
     * @param descontarStock false para guardar sin tocar el stock.
     * @return el id de la venta guardada.
     */
    public static long registrar(AppDatabase db, VentaLocal venta, List<PedidoItem> lista, boolean descontarStock) {
        return db.runInTransaction(() -> {
            Recetario recetario = RecetarioRoom.cargar(db);
            venta.itemsJson = CostesVenta.anotar(venta.itemsJson, recetario);
            long id = db.ventaDao().insert(venta);
            if (descontarStock) {
                aplicarDiferencias(db, cantidadesPorProducto(lista), new LinkedHashMap<>());
                List<LineaConsumo> nuevas = CostesVenta.lineasConsumo(venta.itemsJson);
                if (nuevas != null) IngredienteStockRegistrar.aplicar(db, recetario, nuevas, Collections.emptyList());
            }
            return id;
        });
    }

    /**
     * Guarda la edición de una venta ya registrada y ajusta el stock solo con la diferencia entre las
     * líneas anteriores y las nuevas. Si la versión anterior no guardó el código de producto (ventas
     * antiguas), el stock no se toca: no se puede saber qué se descontó entonces.
     */
    public static long registrarEdicion(AppDatabase db, VentaLocal venta, List<PedidoItem> lista) {
        return db.runInTransaction(() -> {
            VentaLocal anterior = db.ventaDao().getById(venta.id);
            Map<Integer, Integer> previas = anterior != null ? SyncManager.cantidadesPorProducto(anterior.itemsJson) : null;
            Recetario recetario = RecetarioRoom.cargar(db);
            if (previas == null) {
                // Venta anterior sin códigos: no consta qué stock se descontó, así que la nueva versión tampoco
                // lo declara (si lo hiciera, la siguiente edición descontaría o devolvería de más).
                venta.itemsJson = SyncManager.sinCodigos(venta.itemsJson);
            }
            venta.itemsJson = CostesVenta.anotar(venta.itemsJson, recetario);
            db.ventaDao().insert(venta);
            if (previas != null) {
                aplicarDiferencias(db, cantidadesPorProducto(lista), previas);
                List<LineaConsumo> nuevas = CostesVenta.lineasConsumo(venta.itemsJson);
                List<LineaConsumo> anteriores = CostesVenta.lineasConsumo(anterior.itemsJson);
                if (nuevas != null && anteriores != null) {
                    // Con la receta actual: si cambió entre la venta y su edición, la corrección es aproximada.
                    IngredienteStockRegistrar.aplicar(db, recetario, nuevas, anteriores);
                }
            }
            return (long) venta.id;
        });
    }

    private static void aplicarDiferencias(AppDatabase db, Map<Integer, Integer> nuevas, Map<Integer, Integer> previas) {
        Set<Integer> codigos = new HashSet<>(nuevas.keySet());
        codigos.addAll(previas.keySet());
        for (int codigo : codigos) {
            int delta = nuevas.getOrDefault(codigo, 0) - previas.getOrDefault(codigo, 0);
            if (delta > 0) db.productoDao().decrementarStock(codigo, delta);
            else if (delta < 0) db.productoDao().incrementarStock(codigo, -delta);
        }
    }

    /**
     * Unidades a descontar por producto. Solo las líneas en unidades discretas (UD) cuentan: las líneas al
     * peso o por horas no descuentan stock (NEG-09). Coherente con SyncManager.cantidadesPorProducto.
     */
    private static Map<Integer, Integer> cantidadesPorProducto(List<PedidoItem> lista) {
        Map<Integer, Integer> cantidades = new LinkedHashMap<>();
        for (PedidoItem item : lista) {
            if (item.getId() > 0 && item.getCantidad() > 0 && StockAdvisor.controlaStock(item.getUnidadMedida())) {
                cantidades.merge(item.getId(), item.getCantidad(), Integer::sum);
            }
        }
        return cantidades;
    }
}
