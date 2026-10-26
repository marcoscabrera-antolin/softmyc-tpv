package com.softmyc.tpv.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object para modelar tipos impositivos (IVA / Tax Rates).
 * Inmutable y seguro para cálculos financieros de cualquier sector.
 */
public class TaxRate implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final TaxRate GENERAL_21 = new TaxRate("IVA_21", "IVA General (21%)", new BigDecimal("21.00"));
    public static final TaxRate REDUCED_10 = new TaxRate("IVA_10", "IVA Reducido (10%)", new BigDecimal("10.00"));
    public static final TaxRate SUPER_REDUCED_4 = new TaxRate("IVA_4", "IVA Superreducido (4%)", new BigDecimal("4.00"));
    public static final TaxRate EXEMPT_0 = new TaxRate("EXENTO", "Exento de IVA (0%)", BigDecimal.ZERO);

    private final String code;
    private final String name;
    private final BigDecimal percentage; // Ej: 21.00

    public TaxRate(String code, String name, BigDecimal percentage) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("El código del tipo impositivo no puede estar vacío");
        }
        if (percentage == null || percentage.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El porcentaje de impuesto no puede ser negativo: " + percentage);
        }
        this.code = code.trim();
        this.name = name != null ? name : code;
        this.percentage = percentage.setScale(2, RoundingMode.HALF_UP);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    /**
     * Calcula la cuota de impuesto correspondiente a una base imponible.
     * Cuota = Base * (Porcentaje / 100)
     */
    public BigDecimal calculateTaxFromBase(BigDecimal baseAmount) {
        if (baseAmount == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return baseAmount.multiply(percentage)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Extrae la base imponible a partir de un importe con IVA incluido.
     * Base = TotalConIva / (1 + Porcentaje / 100)
     */
    public BigDecimal extractBaseFromGross(BigDecimal grossAmount) {
        if (grossAmount == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal divisor = BigDecimal.ONE.add(
                percentage.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)
        );
        return grossAmount.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaxRate taxRate = (TaxRate) o;
        return code.equalsIgnoreCase(taxRate.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code.toUpperCase());
    }

    @Override
    public String toString() {
        return name + " [" + percentage + "%]";
    }
}
