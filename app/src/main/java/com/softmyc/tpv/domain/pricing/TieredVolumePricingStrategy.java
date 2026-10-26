package com.softmyc.tpv.domain.pricing;

import com.softmyc.tpv.domain.model.CalculatedPrice;
import com.softmyc.tpv.domain.model.CatalogItem;
import com.softmyc.tpv.domain.model.ItemModifier;
import com.softmyc.tpv.domain.model.TaxCalculationMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Estrategia de tarificación por tramos de volumen (Wholesale, Cash & Carry, Venta a gran escala).
 * Aplica precios unitarios decrecientes a medida que aumenta el volumen de compra.
 */
public class TieredVolumePricingStrategy implements PricingStrategy {

    public static class Tier implements Comparable<Tier> {
        private final BigDecimal minQuantity;
        private final BigDecimal maxQuantity; // null = infinito
        private final BigDecimal unitPrice;

        public Tier(BigDecimal minQuantity, BigDecimal maxQuantity, BigDecimal unitPrice) {
            this.minQuantity = minQuantity != null ? minQuantity : BigDecimal.ZERO;
            this.maxQuantity = maxQuantity;
            this.unitPrice = PoliticaRedondeo.aCentimos(unitPrice);
        }

        public boolean matches(BigDecimal quantity) {
            if (quantity.compareTo(minQuantity) < 0) return false;
            return maxQuantity == null || quantity.compareTo(maxQuantity) <= 0;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        @Override
        public int compareTo(Tier o) {
            return this.minQuantity.compareTo(o.minQuantity);
        }
    }

    private final List<Tier> tiers;
    private final TaxCalculationMode taxMode;

    public TieredVolumePricingStrategy(List<Tier> tiers) {
        this(tiers, TaxCalculationMode.INCLUSIVE);
    }

    public TieredVolumePricingStrategy(List<Tier> tiers, TaxCalculationMode taxMode) {
        this.tiers = (tiers != null) ? new ArrayList<>(tiers) : new ArrayList<>();
        Collections.sort(this.tiers);
        this.taxMode = (taxMode != null) ? taxMode : TaxCalculationMode.INCLUSIVE;
    }

    @Override
    public CalculatedPrice calculate(CatalogItem item, BigDecimal quantity, List<ItemModifier> modifiers) {
        if (item == null) {
            throw new IllegalArgumentException("El artículo no puede ser nulo");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser estrictamente positiva: " + quantity);
        }

        List<String> auditLog = new ArrayList<>();
        auditLog.add("Estrategia: Tarificación Escalonada por Volumen [" + taxMode + "]");
        auditLog.add("Artículo: " + item.getName() + " | Cantidad solicitada: " + quantity);

        BigDecimal effectiveUnitPrice = item.getBasePrice();
        boolean tierFound = false;

        for (Tier tier : tiers) {
            if (tier.matches(quantity)) {
                effectiveUnitPrice = tier.getUnitPrice();
                tierFound = true;
                auditLog.add("Tramo de volumen aplicado: desde " + tier.minQuantity +
                        " hasta " + (tier.maxQuantity != null ? tier.maxQuantity : "∞") +
                        " -> Precio bonificado: " + effectiveUnitPrice + " €/ud");
                break;
            }
        }

        if (!tierFound) {
            auditLog.add("Sin tramo especial. Aplica precio base: " + effectiveUnitPrice + " €/ud");
        }

        BigDecimal subtotalBase = effectiveUnitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        auditLog.add("Subtotal base (" + quantity + " x " + effectiveUnitPrice + "): " + subtotalBase + " €");

        // Recargos y descuentos por unidad al precio del tramo aplicado.
        BigDecimal totalModifiers = ModifierDeltas.totalPorUnidadFacturada(modifiers, effectiveUnitPrice, quantity);
        auditLog.add("Modificadores (por unidad x cantidad) -> Delta total: " + totalModifiers + " €");

        BigDecimal netTotal = subtotalBase.add(totalModifiers);
        if (netTotal.compareTo(BigDecimal.ZERO) < 0) {
            netTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal netSubtotal;
        BigDecimal taxAmount;
        BigDecimal totalFinal;

        if (taxMode == TaxCalculationMode.INCLUSIVE) {
            totalFinal = netTotal.setScale(2, RoundingMode.HALF_UP);
            netSubtotal = item.getTaxRate().extractBaseFromGross(totalFinal);
            taxAmount = totalFinal.subtract(netSubtotal).setScale(2, RoundingMode.HALF_UP);
        } else {
            netSubtotal = netTotal.setScale(2, RoundingMode.HALF_UP);
            taxAmount = item.getTaxRate().calculateTaxFromBase(netSubtotal);
            totalFinal = netSubtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
        }

        return new CalculatedPrice(
                quantity,
                quantity,
                effectiveUnitPrice,
                subtotalBase,
                totalModifiers,
                netSubtotal,
                taxAmount,
                totalFinal,
                auditLog
        );
    }
}
