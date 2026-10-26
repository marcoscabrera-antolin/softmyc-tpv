package com.softmyc.tpv.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Entidad agnóstica que modela un artículo o servicio del catálogo comercial.
 * Válido para cualquier sector: restauración, talleres de automoción,
 * comercio a granel, servicios profesionales, etc.
 */
public class CatalogItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String sku;
    private final String name;
    private final String category;
    private final BigDecimal basePrice;
    private final MeasurementUnit measurementUnit;
    private final TaxRate taxRate;
    private final PricingStrategyType strategyType;
    private final List<ItemModifier> availableModifiers;

    public CatalogItem(
            String id,
            String sku,
            String name,
            String category,
            BigDecimal basePrice,
            MeasurementUnit measurementUnit,
            TaxRate taxRate,
            PricingStrategyType strategyType,
            List<ItemModifier> availableModifiers
    ) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del artículo no puede estar vacío");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del artículo no puede estar vacío");
        }
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio base no puede ser negativo: " + basePrice);
        }

        this.id = id.trim();
        this.sku = (sku != null) ? sku.trim() : this.id;
        this.name = name.trim();
        this.category = (category != null && !category.trim().isEmpty()) ? category.trim() : "GENERAL";
        this.basePrice = basePrice.setScale(2, RoundingMode.HALF_UP);
        this.measurementUnit = (measurementUnit != null) ? measurementUnit : MeasurementUnit.UNIT;
        this.taxRate = (taxRate != null) ? taxRate : TaxRate.GENERAL_21;
        this.strategyType = (strategyType != null) ? strategyType : PricingStrategyType.UNIT;
        this.availableModifiers = (availableModifiers != null)
                ? Collections.unmodifiableList(new ArrayList<>(availableModifiers))
                : Collections.emptyList();
    }

    public String getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public MeasurementUnit getMeasurementUnit() {
        return measurementUnit;
    }

    public TaxRate getTaxRate() {
        return taxRate;
    }

    public PricingStrategyType getStrategyType() {
        return strategyType;
    }

    public List<ItemModifier> getAvailableModifiers() {
        return availableModifiers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CatalogItem that = (CatalogItem) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name + " (" + basePrice + " € / " + measurementUnit.getCode() + ")";
    }

    // --- Builder Pattern para construcción limpia ---

    public static Builder builder(String id, String name, BigDecimal basePrice) {
        return new Builder(id, name, basePrice);
    }

    public static class Builder {
        private final String id;
        private final String name;
        private final BigDecimal basePrice;
        private String sku;
        private String category = "GENERAL";
        private MeasurementUnit measurementUnit = MeasurementUnit.UNIT;
        private TaxRate taxRate = TaxRate.GENERAL_21;
        private PricingStrategyType strategyType = PricingStrategyType.UNIT;
        private List<ItemModifier> availableModifiers = new ArrayList<>();

        public Builder(String id, String name, BigDecimal basePrice) {
            this.id = id;
            this.name = name;
            this.basePrice = basePrice;
        }

        public Builder sku(String sku) {
            this.sku = sku;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder unit(MeasurementUnit unit) {
            this.measurementUnit = unit;
            return this;
        }

        public Builder tax(TaxRate tax) {
            this.taxRate = tax;
            return this;
        }

        public Builder strategy(PricingStrategyType strategy) {
            this.strategyType = strategy;
            return this;
        }

        public Builder addModifier(ItemModifier modifier) {
            if (modifier != null) {
                this.availableModifiers.add(modifier);
            }
            return this;
        }

        public Builder modifiers(List<ItemModifier> modifiers) {
            if (modifiers != null) {
                this.availableModifiers = new ArrayList<>(modifiers);
            }
            return this;
        }

        public CatalogItem build() {
            return new CatalogItem(
                    id, sku, name, category, basePrice, measurementUnit,
                    taxRate, strategyType, availableModifiers
            );
        }
    }
}
