package com.softmyc.tpv.domain.repository;

/** Venta registrada tal como la muestran el historial, las estadísticas y el arqueo (sin tipos de Room). */
public final class VentaResumen {

    public final int id;
    public final long fechaMs;
    public final double total;
    public final String metodoPago;
    public final String itemsJson;
    public final boolean sincronizada;

    public VentaResumen(int id, long fechaMs, double total, String metodoPago, String itemsJson,
                        boolean sincronizada) {
        this.id = id;
        this.fechaMs = fechaMs;
        this.total = total;
        this.metodoPago = metodoPago;
        this.itemsJson = itemsJson;
        this.sincronizada = sincronizada;
    }
}
