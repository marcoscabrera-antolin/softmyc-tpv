package com.softmyc.tpv.domain.pricing;

import com.softmyc.tpv.domain.model.CalculatedPrice;
import com.softmyc.tpv.domain.model.CatalogItem;
import com.softmyc.tpv.domain.model.ItemModifier;
import com.softmyc.tpv.domain.model.TaxCalculationMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Estrategia de tarificación estándar para ventas por unidades discretas
 * (comercio minorista, hostelería, piezas de recambio).
 */
public class StandardUnitPricingStrategy implements PricingStrategy {

    private final TaxCalculationMode taxMode;

    public StandardUnitPricingStrategy() {
        this(TaxCalculationMode.INCLUSIVE);
    }

    public StandardUnitPricingStrategy(TaxCalculationMode taxMode) {
        this.taxMode = (taxMode != null) ? taxMode : TaxCalculationMode.INCLUSIVE;
    }

    @Override
    public CalculatedPrice calculate(CatalogItem item, BigDecimal quantity, List<ItemModifier> modifiers) {
        if (item == null) {
            throw new IllegalArgumentException("El artículo no puede ser nulo");
        }
        if (quantity == null) {
            quantity = BigDecimal.ONE;
        }

        // Validación de unidad de medida
        item.getMeasurementUnit().validateQuantity(quantity);

        List<String> auditLog = new ArrayList<>();
        auditLog.add("Estrategia: Tarificación Unitaria Estándar [" + taxMode + "]");
        auditLog.add("Artículo: " + item.getName() + " | Precio base: " + item.getBasePrice() + " €");
        auditLog.add("Cantidad: " + quantity + " " + item.getMeasurementUnit().getCode());

        BigDecimal basePrice = item.getBasePrice();
        BigDecimal subtotalBase = basePrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        auditLog.add("Subtotal base bruto: " + subtotalBase + " €");

        BigDecimal totalModifiersDelta = ModifierDeltas.totalPorUnidadFacturada(modifiers, basePrice, quantity);
        auditLog.add("Modificadores (por unidad x cantidad) -> Delta total: " + totalModifiersDelta + " €");

        BigDecimal netTotal = subtotalBase.add(totalModifiersDelta);
        if (netTotal.compareTo(BigDecimal.ZERO) < 0) {
            auditLog.add("Aviso: Descuentos superan el total. Se ajusta a 0.00 €");
            netTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal netSubtotal;
        BigDecimal taxAmount;
        BigDecimal totalFinal;

        if (taxMode == TaxCalculationMode.INCLUSIVE) {
            // El importe neto ya incluye IVA
            totalFinal = netTotal.setScale(2, RoundingMode.HALF_UP);
            netSubtotal = item.getTaxRate().extractBaseFromGross(totalFinal);
            taxAmount = totalFinal.subtract(netSubtotal).setScale(2, RoundingMode.HALF_UP);
            auditLog.add("Desglose IVA (" + item.getTaxRate().getName() + "): Base imponible = " +
                    netSubtotal + " €, Cuota IVA = " + taxAmount + " €, Total PVP = " + totalFinal + " €");
        } else {
            // B2B: El precio es la base imponible y el IVA se añade encima
            netSubtotal = netTotal.setScale(2, RoundingMode.HALF_UP);
            taxAmount = item.getTaxRate().calculateTaxFromBase(netSubtotal);
            totalFinal = netSubtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
            auditLog.add("Cálculo B2B: Base = " + netSubtotal + " €, Cuota IVA (" +
                    item.getTaxRate().getName() + ") = " + taxAmount + " €, Total = " + totalFinal + " €");
        }

        return new CalculatedPrice(
                quantity,
                quantity,
                basePrice,
                subtotalBase,
                totalModifiersDelta,
                netSubtotal,
                taxAmount,
                totalFinal,
                auditLog
        );
    }
}
