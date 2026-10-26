package com.softmyc.tpv.domain.usecase;

import com.softmyc.tpv.domain.model.TaxRate;
import com.softmyc.tpv.domain.model.UniversalOrder;
import com.softmyc.tpv.domain.model.UniversalOrderItem;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Caso de Uso de Dominio: Cálculo de Totales y Liquidación Fiscal Universal.
 * Totalmente desacoplado de frameworks y bases de datos.
 */
public class CalculateUniversalTotalUseCase {

    public static class OrderFinancialSummary implements Serializable {
        private static final long serialVersionUID = 1L;

        private final BigDecimal subtotal;
        private final BigDecimal totalTax;
        private final BigDecimal grandTotal;
        private final int lineCount;
        private final Map<TaxRate, BigDecimal[]> taxBreakdown;

        public OrderFinancialSummary(
                BigDecimal subtotal,
                BigDecimal totalTax,
                BigDecimal grandTotal,
                int lineCount,
                Map<TaxRate, BigDecimal[]> taxBreakdown
        ) {
            this.subtotal = subtotal;
            this.totalTax = totalTax;
            this.grandTotal = grandTotal;
            this.lineCount = lineCount;
            this.taxBreakdown = taxBreakdown;
        }

        public BigDecimal getSubtotal() {
            return subtotal;
        }

        public BigDecimal getTotalTax() {
            return totalTax;
        }

        public BigDecimal getGrandTotal() {
            return grandTotal;
        }

        public int getLineCount() {
            return lineCount;
        }

        public Map<TaxRate, BigDecimal[]> getTaxBreakdown() {
            return taxBreakdown;
        }

        @Override
        public String toString() {
            return "Total: " + grandTotal + " € (Base: " + subtotal + " €, IVA: " + totalTax + " €)";
        }
    }

    /**
     * Ejecuta el cálculo financiero completo de un pedido comercial.
     */
    public OrderFinancialSummary execute(UniversalOrder order) {
        if (order == null || order.getItems().isEmpty()) {
            return new OrderFinancialSummary(
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    0,
                    java.util.Collections.emptyMap()
            );
        }

        BigDecimal subtotal = order.calculateSubtotal();
        BigDecimal totalTax = order.calculateTotalTax();
        BigDecimal grandTotal = order.calculateGrandTotal();
        Map<TaxRate, BigDecimal[]> breakdown = order.getTaxBreakdown();

        return new OrderFinancialSummary(
                subtotal,
                totalTax,
                grandTotal,
                order.getItemCount(),
                breakdown
        );
    }
}
