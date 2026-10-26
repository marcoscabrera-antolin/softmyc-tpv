package com.softmyc.tpv.domain.model;

/**
 * Modo de aplicación del tipo impositivo.
 * - INCLUSIVE: PVP minorista (B2C, hostelería, comercio al por menor). El precio fijado ya incluye IVA.
 * - EXCLUSIVE: Tarifa profesional (B2B, talleres mecánicos, venta mayorista). El IVA se añade sobre la base.
 */
public enum TaxCalculationMode {
    /** Precio con IVA incluido (PVP B2C estándar) */
    INCLUSIVE,

    /** Precio en Base Imponible al que se suma la cuota de IVA (B2B / Facturación profesional) */
    EXCLUSIVE
}
