package com.softmyc.tpv.domain.model;

/**
 * Tipos de modificadores comerciales agnósticos.
 * Permiten expresar recargos fijos o porcentuales, descuentos,
 * taras de peso, exclusiones de componentes y especificaciones cualitativas.
 */
public enum ModifierType {
    /** Recargo fijo monetario (ej: +1.00 € por extra o suplemento) */
    SURCHARGE_FIXED,

    /** Recargo porcentual sobre el precio base (ej: +10% nocturnidad o urgencia) */
    SURCHARGE_PERCENTAGE,

    /** Descuento fijo monetario (ej: -2.00 € cupón o promoción) */
    DISCOUNT_FIXED,

    /** Descuento porcentual (ej: -15% tarifa mayorista) */
    DISCOUNT_PERCENTAGE,

    /** Deducción de tara de peso (ej: -0.150 kg envase reutilizable del cliente) */
    TARE_DEDUCTION,

    /** Exclusión de un componente base (ej: 'Sin cebolla', 'Sin recubrimiento') */
    EXCLUSION,

    /** Especificación o nota cualitativa (ej: 'Poco hecho', 'Eje delantero') */
    QUALITATIVE_NOTE
}
