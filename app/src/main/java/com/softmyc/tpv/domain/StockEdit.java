package com.softmyc.tpv.domain;

/** Operaciones puras de edición manual de stock usadas por StockActivity. */
public final class StockEdit {

    private StockEdit() { }

    /** Sumar una unidad; desde "sin control" se activa el control con 1 unidad. */
    public static int increment(int stock) {
        return stock < 0 ? 1 : stock + 1;
    }

    /** Restar una unidad sin bajar de cero; "sin control" no cambia. */
    public static int decrement(int stock) {
        if (stock < 0) return StockLevel.NO_CONTROL;
        return Math.max(0, stock - 1);
    }

    /** Texto vacío o inválido significa "sin control" (-1). */
    public static int parse(String text) {
        if (text == null || text.trim().isEmpty()) return StockLevel.NO_CONTROL;
        try {
            int value = Integer.parseInt(text.trim());
            return value < 0 ? StockLevel.NO_CONTROL : value;
        } catch (NumberFormatException e) {
            return StockLevel.NO_CONTROL;
        }
    }
}
