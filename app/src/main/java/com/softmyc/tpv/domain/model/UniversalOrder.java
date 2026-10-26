package com.softmyc.tpv.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate Root del dominio comercial: Pedido/Venta Universal (UniversalOrder).
 * Desacoplado de cualquier sector: representa indistintamente un ticket de restaurante,
 * una factura de reparación de taller, una cuenta de frutería al peso o una venta retail.
 */
public class UniversalOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final long timestamp;
    private final String businessSector;
    private final String customerIdentifier;
    private final List<UniversalOrderItem> items;
    private PaymentMethod paymentMethod;
    private final TaxCalculationMode taxMode;

    public UniversalOrder(
            String id,
            long timestamp,
            String businessSector,
            String customerIdentifier,
            List<UniversalOrderItem> items,
            PaymentMethod paymentMethod,
            TaxCalculationMode taxMode
    ) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del pedido no puede estar vacío");
        }
        this.id = id.trim();
        this.timestamp = (timestamp > 0) ? timestamp : System.currentTimeMillis();
        this.businessSector = (businessSector != null && !businessSector.trim().isEmpty())
                ? businessSector.trim().toUpperCase() : "GENERAL";
        this.customerIdentifier = (customerIdentifier != null) ? customerIdentifier.trim() : "";
        this.items = (items != null) ? new ArrayList<>(items) : new ArrayList<>();
        this.paymentMethod = (paymentMethod != null) ? paymentMethod : PaymentMethod.CASH;
        this.taxMode = (taxMode != null) ? taxMode : TaxCalculationMode.INCLUSIVE;
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getBusinessSector() {
        return businessSector;
    }

    public String getCustomerIdentifier() {
        return customerIdentifier;
    }

    public List<UniversalOrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        if (paymentMethod != null) {
            this.paymentMethod = paymentMethod;
        }
    }

    public TaxCalculationMode getTaxMode() {
        return taxMode;
    }

    public void addItem(UniversalOrderItem item) {
        if (item != null) {
            items.add(item);
        }
    }

    public boolean removeItem(String lineId) {
        if (lineId == null) return false;
        return items.removeIf(it -> lineId.equals(it.getLineId()));
    }

    public void clear() {
        items.clear();
    }

    public int getItemCount() {
        return items.size();
    }

    // --- Cálculos Financieros Globales ---

    /**
     * Calcula la suma de bases imponibles (subtotal neto) de todos los artículos.
     */
    public BigDecimal calculateSubtotal() {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (UniversalOrderItem line : items) {
            subtotal = subtotal.add(line.getCalculatedPrice().getNetSubtotal());
        }
        return subtotal.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula la cuota total de impuestos (IVA) acumulada en el pedido.
     */
    public BigDecimal calculateTotalTax() {
        BigDecimal tax = BigDecimal.ZERO;
        for (UniversalOrderItem line : items) {
            tax = tax.add(line.getCalculatedPrice().getTaxAmount());
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el importe total definitivo a cobrar (PVP o total factura con impuestos).
     */
    public BigDecimal calculateGrandTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (UniversalOrderItem line : items) {
            total = total.add(line.getCalculatedPrice().getTotalAmount());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Genera un desglose fiscal agrupado por tipo impositivo.
     * Retorna un mapa: Tipo IVA -> [Base Imponible Acumulada, Cuota IVA Acumulada].
     * Cumple con los requerimientos legales de facturación y auditoría tributaria.
     */
    public Map<TaxRate, BigDecimal[]> getTaxBreakdown() {
        Map<TaxRate, BigDecimal[]> breakdown = new LinkedHashMap<>();

        for (UniversalOrderItem line : items) {
            TaxRate rate = line.getItem().getTaxRate();
            CalculatedPrice price = line.getCalculatedPrice();

            BigDecimal[] acc = breakdown.computeIfAbsent(rate, r -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(price.getNetSubtotal()).setScale(2, RoundingMode.HALF_UP);
            acc[1] = acc[1].add(price.getTaxAmount()).setScale(2, RoundingMode.HALF_UP);
        }

        return breakdown;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UniversalOrder that = (UniversalOrder) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UniversalOrder #" + id + " [" + businessSector + "] Total: " +
                calculateGrandTotal() + " € (" + items.size() + " líneas)";
    }
}
