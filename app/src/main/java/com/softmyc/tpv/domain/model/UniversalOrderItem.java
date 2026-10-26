package com.softmyc.tpv.domain.model;

import com.softmyc.tpv.domain.pricing.PricingStrategy;
import com.softmyc.tpv.domain.pricing.PricingStrategyFactory;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Línea de pedido agnóstica universal (UniversalOrderItem).
 * Modela cualquier renglón de una venta comercial: artículos unitarios,
 * pesajes a granel, servicios con mano de obra y alquileres temporales.
 * Sustituye el modelo gastronómico restrictivo PedidoItem.
 */
public class UniversalOrderItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String lineId;
    private final CatalogItem item;
    private final BigDecimal quantity;
    private final List<ItemModifier> appliedModifiers;
    private final String notes;
    private final transient PricingStrategy pricingStrategy;
    private CalculatedPrice calculatedPrice;

    public UniversalOrderItem(
            String lineId,
            CatalogItem item,
            BigDecimal quantity,
            List<ItemModifier> appliedModifiers,
            String notes,
            PricingStrategy pricingStrategy
    ) {
        if (item == null) {
            throw new IllegalArgumentException("El artículo de catálogo es obligatorio");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser estrictamente positiva: " + quantity);
        }

        this.lineId = (lineId != null && !lineId.trim().isEmpty())
                ? lineId.trim() : item.getId() + "-" + System.nanoTime();
        this.item = item;
        this.quantity = quantity;
        this.appliedModifiers = (appliedModifiers != null)
                ? Collections.unmodifiableList(new ArrayList<>(appliedModifiers))
                : Collections.emptyList();
        this.notes = (notes != null) ? notes.trim() : "";
        this.pricingStrategy = (pricingStrategy != null)
                ? pricingStrategy
                : new PricingStrategyFactory().createStrategy(item);

        // Precalcular el precio
        recalculate();
    }

    public void recalculate() {
        this.calculatedPrice = pricingStrategy.calculate(item, quantity, appliedModifiers);
    }

    public String getLineId() {
        return lineId;
    }

    public CatalogItem getItem() {
        return item;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public List<ItemModifier> getAppliedModifiers() {
        return appliedModifiers;
    }

    public String getNotes() {
        return notes;
    }

    public PricingStrategy getPricingStrategy() {
        return pricingStrategy;
    }

    public CalculatedPrice getCalculatedPrice() {
        if (calculatedPrice == null) {
            recalculate();
        }
        return calculatedPrice;
    }

    public BigDecimal getLineTotal() {
        return getCalculatedPrice().getTotalAmount();
    }

    /**
     * Resumen formateado de modificaciones y especificaciones de la línea.
     */
    public String getFormattedModifiersSummary() {
        if (appliedModifiers.isEmpty() && notes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ItemModifier mod : appliedModifiers) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(mod.toString());
        }
        if (!notes.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("Nota: ").append(notes);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UniversalOrderItem that = (UniversalOrderItem) o;
        return Objects.equals(lineId, that.lineId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineId);
    }

    @Override
    public String toString() {
        return item.getName() + " x " + quantity + " " + item.getMeasurementUnit().getCode() +
                " = " + getLineTotal() + " €";
    }

    // --- Builder Pattern ---

    public static Builder builder(CatalogItem item, BigDecimal quantity) {
        return new Builder(item, quantity);
    }

    public static class Builder {
        private final CatalogItem item;
        private final BigDecimal quantity;
        private String lineId;
        private List<ItemModifier> modifiers = new ArrayList<>();
        private String notes = "";
        private PricingStrategy strategy;

        public Builder(CatalogItem item, BigDecimal quantity) {
            this.item = item;
            this.quantity = quantity;
        }

        public Builder lineId(String lineId) {
            this.lineId = lineId;
            return this;
        }

        public Builder addModifier(ItemModifier modifier) {
            if (modifier != null) {
                this.modifiers.add(modifier);
            }
            return this;
        }

        public Builder modifiers(List<ItemModifier> modifiers) {
            if (modifiers != null) {
                this.modifiers = new ArrayList<>(modifiers);
            }
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder strategy(PricingStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public UniversalOrderItem build() {
            return new UniversalOrderItem(lineId, item, quantity, modifiers, notes, strategy);
        }
    }
}
