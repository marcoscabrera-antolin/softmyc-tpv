package com.softmyc.tpv.domain.port;

import com.softmyc.tpv.domain.PedidoItem;

import java.util.List;

/** Datos de una venta lista para guardar. {@code ventaId} es null cuando es una venta nueva. */
public final class VentaCobro {

    public final Integer ventaId;
    public final double total;
    public final String itemsJson;
    public final String metodoPago;
    public final List<PedidoItem> lineas;
    public final long ahoraMs;

    public VentaCobro(Integer ventaId, double total, String itemsJson, String metodoPago,
                      List<PedidoItem> lineas, long ahoraMs) {
        this.ventaId = ventaId;
        this.total = total;
        this.itemsJson = itemsJson;
        this.metodoPago = metodoPago;
        this.lineas = lineas;
        this.ahoraMs = ahoraMs;
    }
}
