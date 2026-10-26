package com.softmyc.tpv.domain.service;

import com.softmyc.tpv.domain.model.CalculatedPrice;
import com.softmyc.tpv.domain.model.ItemModifier;
import com.softmyc.tpv.domain.model.TaxRate;
import com.softmyc.tpv.domain.model.UniversalOrder;
import com.softmyc.tpv.domain.model.UniversalOrderItem;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Formateador de recibos y facturas simplificadas agnóstico para terminales térmicos (ESC/POS).
 * Totalmente desacoplado de Android (utilizable en cualquier entorno JVM).
 */
public class TextReceiptFormatter implements UniversalPrinterService {

    private static final int COLS = 42; // Ancho estándar papel térmico 58mm / 80mm
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

    @Override
    public String formatTicket(UniversalOrder order, String storeName) {
        if (order == null) return "";

        StringBuilder sb = new StringBuilder();
        String divider = repeat('-', COLS);
        String doubleDivider = repeat('=', COLS);

        // Encabezado
        sb.append(center(storeName != null ? storeName : "SOFTMYC TPV", COLS)).append("\n");
        sb.append(center("Sector: " + order.getBusinessSector(), COLS)).append("\n");
        sb.append(divider).append("\n");

        sb.append(padRight("Ticket / Factura: #" + order.getId(), COLS)).append("\n");
        sb.append(padRight("Fecha: " + DATE_FORMAT.format(new Date(order.getTimestamp())), COLS)).append("\n");

        if (!order.getCustomerIdentifier().isEmpty()) {
            sb.append(padRight("Cliente: " + order.getCustomerIdentifier(), COLS)).append("\n");
        }
        sb.append(divider).append("\n");

        // Líneas de detalle
        for (UniversalOrderItem line : order.getItems()) {
            CalculatedPrice cp = line.getCalculatedPrice();
            String qtyUnit = cp.getNetQuantity() + " " + line.getItem().getMeasurementUnit().getCode();
            String name = line.getItem().getName();
            String totalStr = cp.getTotalAmount() + " EUR";

            // Formato: "1.450 KG Manzana Golden       3.19 EUR"
            String leftPart = qtyUnit + " " + name;
            sb.append(formatTwoColumns(leftPart, totalStr, COLS)).append("\n");

            // Desglose de modificadores si existen
            for (ItemModifier mod : line.getAppliedModifiers()) {
                String modStr = "  > " + mod.toString();
                sb.append(padRight(modStr, COLS)).append("\n");
            }
            if (!line.getNotes().isEmpty()) {
                sb.append(padRight("  * Nota: " + line.getNotes(), COLS)).append("\n");
            }
        }

        sb.append(divider).append("\n");

        // Totales financieros
        sb.append(formatTwoColumns("Base Imponible:", order.calculateSubtotal() + " EUR", COLS)).append("\n");

        // Desglose de impuestos por tipo
        for (Map.Entry<TaxRate, BigDecimal[]> entry : order.getTaxBreakdown().entrySet()) {
            TaxRate rate = entry.getKey();
            BigDecimal base = entry.getValue()[0];
            BigDecimal quota = entry.getValue()[1];
            String taxLabel = "  " + rate.getName() + " (Base: " + base + "):";
            sb.append(formatTwoColumns(taxLabel, quota + " EUR", COLS)).append("\n");
        }

        sb.append(doubleDivider).append("\n");
        sb.append(formatTwoColumns("TOTAL A PAGAR:", order.calculateGrandTotal() + " EUR", COLS)).append("\n");
        sb.append(doubleDivider).append("\n");

        sb.append(padRight("Forma de Pago: " + order.getPaymentMethod().getDisplayName(), COLS)).append("\n");
        sb.append(center("¡Gracias por su visita!", COLS)).append("\n\n\n");

        return sb.toString();
    }

    @Override
    public void printTicket(UniversalOrder order, String storeName) {
        // En implementación de producción en dispositivo, se envía al driver ESC/POS.
        // Aquí valida que el formato se genera limpiamente sin excepciones.
        String ticket = formatTicket(order, storeName);
        if (ticket.isEmpty()) {
            throw new IllegalStateException("El ticket generado está vacío");
        }
    }

    private static String center(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text.substring(0, width);
        int pad = (width - text.length()) / 2;
        return repeat(' ', pad) + text;
    }

    private static String padRight(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text.substring(0, width);
        return text + repeat(' ', width - text.length());
    }

    private static String formatTwoColumns(String left, String right, int width) {
        if (left == null) left = "";
        if (right == null) right = "";
        int availableForLeft = width - right.length() - 1;
        if (availableForLeft > 0 && left.length() > availableForLeft) {
            left = left.substring(0, availableForLeft);
        }
        int spaces = Math.max(1, width - left.length() - right.length());
        return left + repeat(' ', spaces) + right;
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(c);
        return sb.toString();
    }
}
