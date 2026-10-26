package com.softmyc.tpv.data.local;

import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.usecase.StockAdvisor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Guarda una venta y ajusta el stock de sus líneas en una única transacción de Room. */
public final class VentaStockRegistrar {

    private VentaStockRegistrar() { }

    /**
     * @param descontarStock false para guardar sin tocar el stock.
     * @return el id de la venta guardada.
     */
    public static long registrar(AppDatabase db, VentaLocal venta, List<PedidoItem> lista, boolean descontarStock) {
        return db.runInTransaction(() -> {
            long id = db.ventaDao().insert(venta);
            if (descontarStock) {
                aplicarDiferencias(db, cantidadesPorProducto(lista), new LinkedHashMap<>());
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
            if (previas == null) {
                // Venta anterior sin códigos: no consta qué stock se descontó, así que la nueva versión tampoco
                // lo declara (si lo hiciera, la siguiente edición descontaría o devolvería de más).
                venta.itemsJson = SyncManager.sinCodigos(venta.itemsJson);
            }
            db.ventaDao().insert(venta);
            if (previas != null) aplicarDiferencias(db, cantidadesPorProducto(lista), previas);
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
