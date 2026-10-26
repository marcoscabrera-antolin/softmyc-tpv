package com.softmyc.tpv.domain.model;

/**
 * Identifica la estrategia de tarificación aplicable a un artículo de catálogo.
 */
public enum PricingStrategyType {
    /** Venta estándar por unidad discreta (Hostelería, Retail, Repuestos) */
    UNIT,

    /** Venta continua por peso con posible deducción de tara (Frutería, Carnicería, Granel) */
    WEIGHT,

    /** Venta por tiempo de servicio o alquiler (Talleres mecánicos, Alquileres, Consultoría) */
    TIME,

    /** Venta por tramos de volumen o mayorista (Descuentos escalonados por cantidad) */
    TIERED_VOLUME,

    /** Tarificación personalizada o compuesta */
    CUSTOM
}
