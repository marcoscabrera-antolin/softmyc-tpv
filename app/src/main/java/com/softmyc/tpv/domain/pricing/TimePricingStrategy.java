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
 * Estrategia de tarificación para servicios basados en tiempo o alquileres
 * (talleres mecánicos, alquiler de maquinaria/salas, soporte técnico, consultoría).
 * Permite redondeo por fracciones mínimas facturables (ej: tramos de 15 minutos = 0.25h).
 */
public class TimePricingStrategy implements PricingStrategy {

    private final TaxCalculationMode taxMode;
    private final BigDecimal minimumFractionHours; // Ej: 0.25h (15 min), 0.50h (30 min), o 0 (tiempo exacto)

    public TimePricingStrategy() {
        this(TaxCalculationMode.EXCLUSIVE, BigDecimal.ZERO);
    }

    public TimePricingStrategy(TaxCalculationMode taxMode, BigDecimal minimumFractionHours) {
        this.taxMode = (taxMode != null) ? taxMode : TaxCalculationMode.EXCLUSIVE;
        this.minimumFractionHours = (minimumFractionHours != null && minimumFractionHours.compareTo(BigDecimal.ZERO) > 0)
                ? minimumFractionHours : BigDecimal.ZERO;
    }

    @Override
    public CalculatedPrice calculate(CatalogItem item, BigDecimal quantity, List<ItemModifier> modifiers) {
        if (item == null) {
            throw new IllegalArgumentException("El artículo o servicio no puede ser nulo");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El tiempo registrado debe ser estrictamente positivo: " + quantity);
        }

        List<String> auditLog = new ArrayList<>();
        auditLog.add("Estrategia: Tarificación por Tiempo de Servicio [" + taxMode + "]");
        auditLog.add("Servicio: " + item.getName() + " | Tarifa horaria: " + item.getBasePrice() + " €/h");
        auditLog.add("Tiempo real empleado: " + quantity + " " + item.getMeasurementUnit().getCode());

        BigDecimal billableTime = quantity;
        if (minimumFractionHours.compareTo(BigDecimal.ZERO) > 0) {
            // Redondeo hacia arriba al siguiente múltiplo de minimumFractionHours
            BigDecimal remainder = quantity.remainder(minimumFractionHours);
            if (remainder.compareTo(BigDecimal.ZERO) > 0) {
                billableTime = quantity.subtract(remainder).add(minimumFractionHours);
                auditLog.add("Ajuste por fracción mínima (" + minimumFractionHours + " h): Facturable = " + billableTime + " h");
            }
        }
        billableTime = billableTime.setScale(2, RoundingMode.HALF_UP);

        BigDecimal hourlyRate = item.getBasePrice();
        BigDecimal subtotalBase = hourlyRate.multiply(billableTime).setScale(2, RoundingMode.HALF_UP);
        auditLog.add("Subtotal base (" + billableTime + " h x " + hourlyRate + " €/h): " + subtotalBase + " €");

        // Recargos y descuentos por hora facturable (mismo criterio que el resto de estrategias).
        BigDecimal totalMonetaryModifiers = ModifierDeltas.totalPorUnidadFacturada(modifiers, hourlyRate, billableTime);
        auditLog.add("Modificadores (por hora facturable) -> Delta total: " + totalMonetaryModifiers + " €");

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
            // Frecuente en talleres y servicios B2B: tarifa horaria + 21% IVA
            netSubtotal = netTotal.setScale(2, RoundingMode.HALF_UP);
            taxAmount = item.getTaxRate().calculateTaxFromBase(netSubtotal);
            totalFinal = netSubtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
            auditLog.add("Cálculo Taller/B2B: Base Imponible = " + netSubtotal + " €, Cuota IVA (" +
                    item.getTaxRate().getName() + ") = " + taxAmount + " €, Total Factura = " + totalFinal + " €");
        }

        return new CalculatedPrice(
                quantity,
                billableTime,
                hourlyRate,
                subtotalBase,
                totalMonetaryModifiers,
                netSubtotal,
                taxAmount,
                totalFinal,
                auditLog
        );
    }
}
