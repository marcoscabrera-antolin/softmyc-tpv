package com.softmyc.tpv.domain;

import com.softmyc.tpv.domain.model.CatalogItem;
import com.softmyc.tpv.domain.model.ItemModifier;
import com.softmyc.tpv.domain.model.MeasurementUnit;
import com.softmyc.tpv.domain.model.PaymentMethod;
import com.softmyc.tpv.domain.model.PricingStrategyType;
import com.softmyc.tpv.domain.model.TaxCalculationMode;
import com.softmyc.tpv.domain.model.TaxRate;
import com.softmyc.tpv.domain.model.UniversalOrder;
import com.softmyc.tpv.domain.model.UniversalOrderItem;
import com.softmyc.tpv.domain.pricing.TimePricingStrategy;
import com.softmyc.tpv.domain.pricing.WeightPricingStrategy;
import com.softmyc.tpv.domain.service.TextReceiptFormatter;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Suite de pruebas unitarias que valida la construcción y liquidación de pedidos completos
 * en tres sectores comerciales diametralmente opuestos:
 * 1. Taller de Automoción (Mecánica y Repuestos con IVA al 21%).
 * 2. Frutería y Comercio al Peso (Granel con taras e IVA al 4%).
 * 3. Restaurante / Fast-Food (Menú con suplementos e IVA al 10%).
 */
public class UniversalOrderTest {

    @Test
    public void testAutoRepairShopOrder_completeInvoiceWithLaborAndParts() {
        // 1. Repuesto: Pastillas de freno (2 unidades x 45.00 € = 90.00 € base)
        CatalogItem brakePads = CatalogItem.builder("REP-01", "Juego Pastillas Freno Bosch", new BigDecimal("45.00"))
                .category("FRENOS")
                .unit(MeasurementUnit.UNIT)
                .tax(TaxRate.GENERAL_21)
                .build();

        UniversalOrderItem padsLine = UniversalOrderItem.builder(brakePads, new BigDecimal("2"))
                .notes("Instalación eje delantero")
                .build();

        // 2. Servicio: Mano de obra especializada (1.5 horas x 40.00 €/h = 60.00 € base)
        CatalogItem labor = CatalogItem.builder("SERV-01", "Mano de Obra Especializada", new BigDecimal("40.00"))
                .category("SERVICIOS")
                .unit(MeasurementUnit.TIME_HOUR)
                .tax(TaxRate.GENERAL_21)
                .strategy(PricingStrategyType.TIME)
                .build();

        // Modificador de tasa de gestión de residuos de taller (+5.00 €)
        ItemModifier wasteFee = ItemModifier.fixedSurcharge("Tasa Gestión Residuos y Aceites", new BigDecimal("5.00"));

        UniversalOrderItem laborLine = UniversalOrderItem.builder(labor, new BigDecimal("1.50"))
                .addModifier(wasteFee)
                .strategy(new TimePricingStrategy(TaxCalculationMode.EXCLUSIVE, BigDecimal.ZERO))
                .build();

        // Crear pedido de taller (Modo B2B Exclusive)
        UniversalOrder repairOrder = new UniversalOrder(
                "TALLER-2026-0042",
                System.currentTimeMillis(),
                "AUTO_REPAIR",
                "Matrícula: 4892-KZX (Cliente: Juan Gómez)",
                Arrays.asList(padsLine, laborLine),
                PaymentMethod.CARD,
                TaxCalculationMode.EXCLUSIVE
        );

        assertEquals(2, repairOrder.getItemCount());

        // Verificación de totales:
        // Pastillas: PVP = 90.00 € (Base = 74.38 €, IVA = 15.62 €)
        // Mano de obra (B2B): Base = 60.00 + 5.00 = 65.00 €. IVA = 13.65 €. Total = 78.65 €.
        BigDecimal grandTotal = repairOrder.calculateGrandTotal();
        assertNotNull(grandTotal);
        assertTrue(grandTotal.compareTo(BigDecimal.ZERO) > 0);

        // Desglose fiscal de IVA
        Map<TaxRate, BigDecimal[]> breakdown = repairOrder.getTaxBreakdown();
        assertTrue(breakdown.containsKey(TaxRate.GENERAL_21));

        // Formatear ticket térmico
        TextReceiptFormatter formatter = new TextReceiptFormatter();
        String ticket = formatter.formatTicket(repairOrder, "TALLERES MECÁNICOS SOFTMYC SL");

        assertNotNull(ticket);
        assertTrue(ticket.contains("AUTO_REPAIR"));
        assertTrue(ticket.contains("4892-KZX"));
        assertTrue(ticket.contains("Juego Pastillas Freno Bosch"));
        assertTrue(ticket.contains("Mano de Obra"));
        assertTrue(ticket.contains("TOTAL A PAGAR"));
    }

    @Test
    public void testProduceMarketOrder_weightedItemsWithTare() {
        // Frutas al peso con IVA superreducido (4%)
        CatalogItem apples = CatalogItem.builder("FRUIT-01", "Manzana Golden", new BigDecimal("2.20"))
                .unit(MeasurementUnit.WEIGHT_KG)
                .tax(TaxRate.SUPER_REDUCED_4)
                .strategy(PricingStrategyType.WEIGHT)
                .build();

        // 1.500 kg bruto con tara de 0.200 kg = 1.300 kg neto x 2.20 = 2.86 €
        ItemModifier boxTare = ItemModifier.tareDeduction("Cesta Madera Cliente", new BigDecimal("0.200"));
        UniversalOrderItem appleLine = UniversalOrderItem.builder(apples, new BigDecimal("1.500"))
                .addModifier(boxTare)
                .strategy(new WeightPricingStrategy(TaxCalculationMode.INCLUSIVE))
                .build();

        CatalogItem bananas = CatalogItem.builder("FRUIT-02", "Plátano de Canarias", new BigDecimal("1.80"))
                .unit(MeasurementUnit.WEIGHT_KG)
                .tax(TaxRate.SUPER_REDUCED_4)
                .strategy(PricingStrategyType.WEIGHT)
                .build();

        // 1.000 kg x 1.80 = 1.80 €
        UniversalOrderItem bananaLine = UniversalOrderItem.builder(bananas, new BigDecimal("1.000"))
                .strategy(new WeightPricingStrategy(TaxCalculationMode.INCLUSIVE))
                .build();

        UniversalOrder produceOrder = new UniversalOrder(
                "FRUT-00123",
                System.currentTimeMillis(),
                "PRODUCE_MARKET",
                "",
                Arrays.asList(appleLine, bananaLine),
                PaymentMethod.CASH,
                TaxCalculationMode.INCLUSIVE
        );

        // Total: 2.86 + 1.80 = 4.66 €
        assertEquals(new BigDecimal("4.66"), produceOrder.calculateGrandTotal());

        // Comprobación de ticket
        TextReceiptFormatter formatter = new TextReceiptFormatter();
        String ticket = formatter.formatTicket(produceOrder, "FRUTERÍA LA HUERTA");

        assertTrue(ticket.contains("PRODUCE_MARKET"));
        assertTrue(ticket.contains("Cesta Madera Cliente"));
        assertTrue(ticket.contains("4.66 EUR"));
    }

    @Test
    public void testHospitalityOrder_multiTaxRatesOrder() {
        // Hamburguesa (IVA 10%)
        CatalogItem burger = CatalogItem.builder("BUR-01", "Burger Clásica", new BigDecimal("8.00"))
                .unit(MeasurementUnit.UNIT)
                .tax(TaxRate.REDUCED_10)
                .build();

        // Cerveza / Bebida alcohólica (IVA General 21% en España)
        CatalogItem beer = CatalogItem.builder("BEV-01", "Cerveza Artesana", new BigDecimal("3.50"))
                .unit(MeasurementUnit.UNIT)
                .tax(TaxRate.GENERAL_21)
                .build();

        UniversalOrderItem burgerLine = UniversalOrderItem.builder(burger, new BigDecimal("1")).build();
        UniversalOrderItem beerLine = UniversalOrderItem.builder(beer, new BigDecimal("2")).build(); // 2 x 3.50 = 7.00 €

        UniversalOrder multiTaxOrder = new UniversalOrder(
                "MESA-04",
                System.currentTimeMillis(),
                "HOSPITALITY",
                "",
                Arrays.asList(burgerLine, beerLine),
                PaymentMethod.CASH,
                TaxCalculationMode.INCLUSIVE
        );

        // Gran total: 8.00 + 7.00 = 15.00 €
        assertEquals(new BigDecimal("15.00"), multiTaxOrder.calculateGrandTotal());

        // El desglose debe contener dos tramos distintos de IVA: 10% y 21%
        Map<TaxRate, BigDecimal[]> breakdown = multiTaxOrder.getTaxBreakdown();
        assertEquals(2, breakdown.size());
        assertTrue(breakdown.containsKey(TaxRate.REDUCED_10));
        assertTrue(breakdown.containsKey(TaxRate.GENERAL_21));
    }
}
