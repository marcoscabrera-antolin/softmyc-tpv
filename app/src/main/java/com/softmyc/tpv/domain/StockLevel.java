package com.softmyc.tpv.domain;

/** Nivel de existencias de un producto, usado por el semáforo de StockActivity. */
public enum StockLevel {
    /** Sin control de inventario (stock = -1). */
    UNTRACKED,
    /** Agotado (0 unidades). */
    OUT,
    /** Stock bajo (1 a {@link #LOW_THRESHOLD} unidades). */
    LOW,
    /** Existencias suficientes. */
    OK;

    public static final int NO_CONTROL = -1;
    public static final int LOW_THRESHOLD = 5;

    public static StockLevel of(int stock) {
        if (stock < 0) return UNTRACKED;
        if (stock == 0) return OUT;
        if (stock <= LOW_THRESHOLD) return LOW;
        return OK;
    }
}
