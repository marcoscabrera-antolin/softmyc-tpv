package com.softmyc.tpv.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Modificador universal aplicable a cualquier artículo comercial en cualquier sector.
 * Sustituye el antiguo modelo rígido de hostelería (IngredientOption) por un Value Object
 * desacoplado que soporta recargos, descuentos, taras de peso, exclusiones y notas de taller o servicio.
 */
public class ItemModifier implements Serializable, Comparable<ItemModifier> {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final ModifierType type;
    private final BigDecimal value; // Importe monetario, porcentaje o valor cuantitativo (tara)

    public ItemModifier(String id, String name, ModifierType type, BigDecimal value) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del modificador no puede estar vacío");
        }
        if (type == null) {
            throw new IllegalArgumentException("El tipo de modificador es obligatorio");
        }
        this.id = (id != null && !id.trim().isEmpty()) ? id.trim() : name.trim().toLowerCase().replace(" ", "_");
        this.name = name.trim();
        this.type = type;
        this.value = (value != null) ? value : BigDecimal.ZERO;
    }

    // --- Métodos factoría convenientes ---

    public static ItemModifier fixedSurcharge(String name, BigDecimal amount) {
        return new ItemModifier(null, name, ModifierType.SURCHARGE_FIXED, amount);
    }

    public static ItemModifier percentageSurcharge(String name, BigDecimal percent) {
        return new ItemModifier(null, name, ModifierType.SURCHARGE_PERCENTAGE, percent);
    }

    public static ItemModifier fixedDiscount(String name, BigDecimal amount) {
        return new ItemModifier(null, name, ModifierType.DISCOUNT_FIXED, amount);
    }

    public static ItemModifier percentageDiscount(String name, BigDecimal percent) {
        return new ItemModifier(null, name, ModifierType.DISCOUNT_PERCENTAGE, percent);
    }

    public static ItemModifier tareDeduction(String name, BigDecimal tareWeight) {
        return new ItemModifier(null, name, ModifierType.TARE_DEDUCTION, tareWeight);
    }

    public static ItemModifier exclusion(String name) {
        return new ItemModifier(null, name, ModifierType.EXCLUSION, BigDecimal.ZERO);
    }

    public static ItemModifier qualitative(String name) {
        return new ItemModifier(null, name, ModifierType.QUALITATIVE_NOTE, BigDecimal.ZERO);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ModifierType getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }

    /**
     * Devuelve el impacto monetario directo que este modificador añade a un precio base unitario.
     * Si es porcentual, se calcula sobre el precio base proporcionado.
     * Si es tara o nota cualitativa, no altera el precio unitario directamente (la tara altera la cantidad neta).
     */
    public BigDecimal calculateMonetaryDelta(BigDecimal basePrice) {
        if (basePrice == null) basePrice = BigDecimal.ZERO;

        switch (type) {
            case SURCHARGE_FIXED:
                return value.setScale(2, RoundingMode.HALF_UP);
            case SURCHARGE_PERCENTAGE:
                return basePrice.multiply(value)
                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                        .setScale(2, RoundingMode.HALF_UP);
            case DISCOUNT_FIXED:
                return value.negate().setScale(2, RoundingMode.HALF_UP);
            case DISCOUNT_PERCENTAGE:
                return basePrice.multiply(value)
                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                        .negate()
                        .setScale(2, RoundingMode.HALF_UP);
            case TARE_DEDUCTION:
            case EXCLUSION:
            case QUALITATIVE_NOTE:
            default:
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemModifier that = (ItemModifier) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                type == that.type &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, value);
    }

    @Override
    public int compareTo(ItemModifier o) {
        if (o == null) return 1;
        return this.name.compareToIgnoreCase(o.name);
    }

    @Override
    public String toString() {
        switch (type) {
            case SURCHARGE_FIXED:
                return name + " (+" + value + " €)";
            case SURCHARGE_PERCENTAGE:
                return name + " (+" + value + "%)";
            case DISCOUNT_FIXED:
                return name + " (-" + value + " €)";
            case DISCOUNT_PERCENTAGE:
                return name + " (-" + value + "%)";
            case TARE_DEDUCTION:
                return name + " (Tara: -" + value + ")";
            case EXCLUSION:
                return "Sin " + name;
            case QUALITATIVE_NOTE:
            default:
                return name;
        }
    }
}
