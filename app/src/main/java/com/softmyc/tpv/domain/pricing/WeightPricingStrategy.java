package com.softmyc.tpv.domain.pricing;

import com.softmyc.tpv.domain.model.CalculatedPrice;
import com.softmyc.tpv.domain.model.CatalogItem;
import com.softmyc.tpv.domain.model.ItemModifier;
import com.softmyc.tpv.domain.model.ModifierType;
import com.softmyc.tpv.domain.model.TaxCalculationMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Estrategia de tarificación para comercio por peso continuo
 * (fruterías, carnicerías, pescaderías, venta de frutos secos a granel).
 * Soporta deducción automática de taras (envases de clientes, bandejas).
 */
public class WeightPricingStrategy implements PricingStrategy {

    private final TaxCalculationMode taxMode;

    public WeightPricingStrategy() {
        this(TaxCalculationMode.INCLUSIVE);
    }

    public WeightPricingStrategy(TaxCalculationMode taxMode) {
        this.taxMode = (taxMode != null) ? taxMode : TaxCalculationMode.INCLUSIVE;
    }

    @Override
    public CalculatedPrice calculate(CatalogItem item, BigDecimal quantity, List<ItemModifier> modifiers) {
        if (item == null) {
            throw new IllegalArgumentException("El artículo no puede ser nulo");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El peso bruto debe ser estrictamente positivo: " + quantity);
        }

        List<String> auditLog = new ArrayList<>();
        auditLog.add("Estrategia: Tarificación por Peso Continuo [" + taxMode + "]");
        auditLog.add("Artículo: " + item.getName() + " | Precio base: " + item.getBasePrice() + " € / " + item.getMeasurementUnit().getCode());
        auditLog.add("Peso bruto registrado: " + quantity + " " + item.getMeasurementUnit().getCode());

        // Deducción de taras
        BigDecimal totalTare = BigDecimal.ZERO;

        if (modifiers != null) {
            for (ItemModifier mod : modifiers) {
                if (mod.getType() == ModifierType.TARE_DEDUCTION) {
                    BigDecimal tare = mod.getValue();
                    totalTare = totalTare.add(tare);
                    auditLog.add("Deducción de Tara: '" + mod.getName() + "' -> -" + tare + " " + item.getMeasurementUnit().getCode());
                }
            }
        }

        BigDecimal netWeight = quantity.subtract(totalTare);
        if (netWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "El peso neto resultante no puede ser menor o igual a cero. Bruto: " + quantity + ", Tara: " + totalTare
            );
        }
        auditLog.add("Peso neto facturable: " + netWeight + " " + item.getMeasurementUnit().getCode());

        BigDecimal basePrice = item.getBasePrice();
        BigDecimal subtotalBase = basePrice.multiply(netWeight).setScale(2, RoundingMode.HALF_UP);
        auditLog.add("Subtotal base (" + netWeight + " x " + basePrice + "): " + subtotalBase + " €");

        // Recargos y descuentos por kilo neto (la tara ya está descontada de netWeight).
        BigDecimal totalMonetaryModifiers = ModifierDeltas.totalPorUnidadFacturada(modifiers, basePrice, netWeight);
        auditLog.add("Modificadores (por kg neto) -> Delta total: " + totalMonetaryModifiers + " €");

        BigDecimal netTotal = subtotalBase.add(totalMonetaryModifiers);
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
            auditLog.add("Desglose IVA (" + item.getTaxRate().getName() + "): Base = " +
                    netSubtotal + " €, IVA = " + taxAmount + " €, Total = " + totalFinal + " €");
        } else {
            netSubtotal = netTotal.setScale(2, RoundingMode.HALF_UP);
            taxAmount = item.getTaxRate().calculateTaxFromBase(netSubtotal);
            totalFinal = netSubtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
            auditLog.add("Cálculo B2B: Base = " + netSubtotal + " €, IVA (" +
                    item.getTaxRate().getName() + ") = " + taxAmount + " €, Total = " + totalFinal + " €");
        }

        return new CalculatedPrice(
                quantity,
                netWeight,
                basePrice,
                subtotalBase,
                totalMonetaryModifiers,
                netSubtotal,
                taxAmount,
                totalFinal,
                auditLog
        );
    }
}
