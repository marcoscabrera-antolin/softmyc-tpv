package com.softmyc.tpv.domain.model;

import com.softmyc.tpv.domain.pricing.PoliticaRedondeo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Value Object inmutable que encapsula el resultado financiero y desglose
 * del cálculo de precio de un artículo o línea de pedido.
 * Incluye trazabilidad de auditoría (audit trail) para explicar paso a paso
 * cómo se llegó al importe final.
 */
public class CalculatedPrice implements Serializable {
    private static final long serialVersionUID = 1L;

    private final BigDecimal grossQuantity;
    private final BigDecimal netQuantity;
    private final BigDecimal unitPrice;
    private final BigDecimal subtotalBeforeModifiers;
    private final BigDecimal modifiersDelta;
    private final BigDecimal netSubtotal;
    private final BigDecimal taxAmount;
    private final BigDecimal totalAmount;
    private final List<String> auditLog;

    public CalculatedPrice(
            BigDecimal grossQuantity,
            BigDecimal netQuantity,
            BigDecimal unitPrice,
            BigDecimal subtotalBeforeModifiers,
            BigDecimal modifiersDelta,
            BigDecimal netSubtotal,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            List<String> auditLog
    ) {
        this.grossQuantity = (grossQuantity != null) ? grossQuantity : BigDecimal.ONE;
        this.netQuantity = (netQuantity != null) ? netQuantity : this.grossQuantity;
        this.unitPrice = PoliticaRedondeo.aCentimos(unitPrice);
        this.subtotalBeforeModifiers = (subtotalBeforeModifiers != null)
                ? subtotalBeforeModifiers.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.modifiersDelta = (modifiersDelta != null)
                ? modifiersDelta.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.netSubtotal = (netSubtotal != null)
                ? netSubtotal.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.taxAmount = (taxAmount != null)
                ? taxAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.totalAmount = (totalAmount != null)
                ? totalAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.auditLog = (auditLog != null)
                ? Collections.unmodifiableList(new ArrayList<>(auditLog))
                : Collections.emptyList();
    }

    public BigDecimal getGrossQuantity() {
        return grossQuantity;
    }

    public BigDecimal getNetQuantity() {
        return netQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getSubtotalBeforeModifiers() {
        return subtotalBeforeModifiers;
    }

    public BigDecimal getModifiersDelta() {
        return modifiersDelta;
    }

    public BigDecimal getNetSubtotal() {
        return netSubtotal;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<String> getAuditLog() {
        return auditLog;
    }

    @Override
    public String toString() {
        return "CalculatedPrice{total=" + totalAmount + " €, netSubtotal=" + netSubtotal +
                " €, tax=" + taxAmount + " €, qty=" + netQuantity + "}";
    }
}
