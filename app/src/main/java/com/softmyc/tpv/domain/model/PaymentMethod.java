package com.softmyc.tpv.domain.model;

/**
 * Métodos de pago comerciales agnósticos.
 */
public enum PaymentMethod {
    /** Pago en efectivo en caja */
    CASH("Efectivo"),

    /** Pago con tarjeta de crédito/débito o TPV bancario */
    CARD("Tarjeta"),

    /** Transferencia bancaria o Bizum profesional */
    BANK_TRANSFER("Transferencia"),

    /** Facturación diferida / Crédito comercial cliente */
    INVOICE_CREDIT("Crédito / Cuenta Cliente"),

    /** Pago mixto o combinado */
    MIXED("Mixto");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
