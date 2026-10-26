package com.softmyc.tpv.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Unidad de Medida comercial agnóstica del sector.
 * Permite modelar ventas por unidades discretas (hostelería, retail),
 * por peso continuo (frutería, carnicería), por tiempo (talleres, alquileres)
 * o por longitud/volumen.
 */
public class MeasurementUnit implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final MeasurementUnit UNIT = new MeasurementUnit("UD", "Unidades", false, 0);
    public static final MeasurementUnit WEIGHT_KG = new MeasurementUnit("KG", "Kilogramos", true, 3);
    public static final MeasurementUnit WEIGHT_G = new MeasurementUnit("G", "Gramos", true, 1);
    public static final MeasurementUnit VOLUME_L = new MeasurementUnit("L", "Litros", true, 2);
    public static final MeasurementUnit TIME_HOUR = new MeasurementUnit("H", "Horas", true, 2);
    public static final MeasurementUnit TIME_MINUTE = new MeasurementUnit("MIN", "Minutos", false, 0);
    public static final MeasurementUnit LENGTH_M = new MeasurementUnit("M", "Metros", true, 2);
    public static final MeasurementUnit AREA_M2 = new MeasurementUnit("M2", "Metros cuadrados", true, 2);

    private final String code;
    private final String displayName;
    private final boolean fractionalAllowed;
    private final int decimalPlaces;

    public MeasurementUnit(String code, String displayName, boolean fractionalAllowed, int decimalPlaces) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("El código de unidad no puede estar vacío");
        }
        this.code = code.trim().toUpperCase();
        this.displayName = displayName != null ? displayName : this.code;
        this.fractionalAllowed = fractionalAllowed;
        this.decimalPlaces = Math.max(0, decimalPlaces);
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isFractionalAllowed() {
        return fractionalAllowed;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    /**
     * Valida si una cantidad numérica es legal para esta unidad de medida.
     * Si no permite fraccionamiento, la cantidad no puede tener parte decimal.
     */
    public void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser estrictamente positiva: " + quantity);
        }
        if (!fractionalAllowed) {
            BigDecimal remainder = quantity.remainder(BigDecimal.ONE);
            if (remainder.compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException(
                        "La unidad '" + code + "' es indivisible y no admite cantidades fraccionarias: " + quantity
                );
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MeasurementUnit that = (MeasurementUnit) o;
        return code.equalsIgnoreCase(that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code.toUpperCase());
    }

    @Override
    public String toString() {
        return code;
    }
}
