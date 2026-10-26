package com.softmyc.tpv.domain;

import com.softmyc.tpv.domain.model.CalculatedPrice;
import com.softmyc.tpv.domain.model.CatalogItem;
import com.softmyc.tpv.domain.model.ItemModifier;
import com.softmyc.tpv.domain.model.MeasurementUnit;
import com.softmyc.tpv.domain.model.TaxCalculationMode;
import com.softmyc.tpv.domain.model.TaxRate;
import com.softmyc.tpv.domain.pricing.StandardUnitPricingStrategy;
import com.softmyc.tpv.domain.pricing.TieredVolumePricingStrategy;
import com.softmyc.tpv.domain.pricing.TimePricingStrategy;
import com.softmyc.tpv.domain.pricing.WeightPricingStrategy;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Suite de pruebas unitarias que valida exhaustivamente el motor de tarificación
 * universal (Strategy Pattern) para diferentes sectores comerciales.
 */
public class UniversalPricingStrategyTest {

    // --- 1. SECTOR HOSTELERÍA / RETAIL: Tarificación Unitaria Estándar ---

    @Test
    public void testStandardUnitPricing_withExtraSurchargeAndExclusion() {
        CatalogItem burger = CatalogItem.builder("BURG-01", "Hamburguesa Buey Especial", new BigDecimal("9.50"))
                .unit(MeasurementUnit.UNIT)
                .tax(TaxRate.REDUCED_10) // 10% IVA hostelería
                .build();

        StandardUnitPricingStrategy strategy = new StandardUnitPricingStrategy(TaxCalculationMode.INCLUSIVE);

        List<ItemModifier> modifiers = Arrays.asList(
                ItemModifier.fixedSurcharge("Extra Bacon", new BigDecimal("1.20")),
                ItemModifier.exclusion("Cebolla")
        );

        // Pedido de 2 hamburguesas
        BigDecimal qty = new BigDecimal("2");
        CalculatedPrice result = strategy.calculate(burger, qty, modifiers);

        assertNotNull(result);
        assertEquals(qty, result.getGrossQuantity());
        assertEquals(qty, result.getNetQuantity());

        // Base: 2 x 9.50 = 19.00 €. Modificadores: 2 x 1.20 = 2.40 €. Total = 21.40 € PVP.
        assertEquals(new BigDecimal("21.40"), result.getTotalAmount());

        // Desglose IVA al 10%: Base = 21.40 / 1.10 = 19.45 €. IVA = 21.40 - 19.45 = 1.95 €.
        assertEquals(new BigDecimal("19.45"), result.getNetSubtotal());
        assertEquals(new BigDecimal("1.95"), result.getTaxAmount());

        // Verificar trazabilidad de auditoría
        assertTrue(result.getAuditLog().size() > 3);
    }

    @Test
    public void testStandardUnitPricing_throwsOnFractionalQuantityForDiscreteUnit() {
        CatalogItem burger = CatalogItem.builder("BURG-01", "Hamburguesa", new BigDecimal("8.00"))
                .unit(MeasurementUnit.UNIT)
                .build();

        StandardUnitPricingStrategy strategy = new StandardUnitPricingStrategy();

        // 1.5 unidades de hamburguesa debe lanzar excepción de validación
        assertThrows(IllegalArgumentException.class, () ->
                strategy.calculate(burger, new BigDecimal("1.5"), Collections.emptyList())
        );
    }

    // --- 2. SECTOR FRUTERÍA / PESO CONTINUO: Tarificación al Peso con Tara ---

    @Test
    public void testWeightPricing_withTareDeduction() {
        CatalogItem apples = CatalogItem.builder("FRUIT-01", "Manzana Golden", new BigDecimal("2.20"))
                .unit(MeasurementUnit.WEIGHT_KG)
                .tax(TaxRate.SUPER_REDUCED_4) // 4% IVA superreducido fruta
                .build();

        WeightPricingStrategy strategy = new WeightPricingStrategy(TaxCalculationMode.INCLUSIVE);

        // Peso bruto registrado en báscula: 1.450 kg
        BigDecimal grossWeight = new BigDecimal("1.450");

        // Tara de envase reutilizable del cliente: 0.150 kg
        List<ItemModifier> modifiers = Collections.singletonList(
                ItemModifier.tareDeduction("Envase Tupper Cliente", new BigDecimal("0.150"))
        );

        CalculatedPrice result = strategy.calculate(apples, grossWeight, modifiers);

        // Peso neto: 1.450 - 0.150 = 1.300 kg
        assertEquals(new BigDecimal("1.300"), result.getNetQuantity());

        // Total: 1.300 kg x 2.20 €/kg = 2.86 €
        assertEquals(new BigDecimal("2.86"), result.getTotalAmount());

        // Desglose IVA 4%: Base = 2.86 / 1.04 = 2.75 €. IVA = 0.11 €
        assertEquals(new BigDecimal("2.75"), result.getNetSubtotal());
        assertEquals(new BigDecimal("0.11"), result.getTaxAmount());
    }

    @Test
    public void testWeightPricing_throwsWhenTareExceedsGrossWeight() {
        CatalogItem apples = CatalogItem.builder("FRUIT-01", "Manzana", new BigDecimal("2.20"))
                .unit(MeasurementUnit.WEIGHT_KG)
                .build();

        WeightPricingStrategy strategy = new WeightPricingStrategy();

        // Bruto: 0.100 kg, Tara: 0.150 kg -> Imposible
        List<ItemModifier> modifiers = Collections.singletonList(
                ItemModifier.tareDeduction("Caja Pesada", new BigDecimal("0.150"))
        );

        assertThrows(IllegalArgumentException.class, () ->
                strategy.calculate(apples, new BigDecimal("0.100"), modifiers)
        );
    }

    // --- 3. SECTOR TALLER / SERVICIOS: Tarificación por Horas (B2B Tax Exclusive) ---

    @Test
    public void testTimePricing_mechanicWorkshopLabor_withUrgencySurcharge() {
        CatalogItem labor = CatalogItem.builder("SERV-01", "Mano de Obra Mecánico", new BigDecimal("40.00"))
                .unit(MeasurementUnit.TIME_HOUR)
                .tax(TaxRate.GENERAL_21) // 21% IVA General
                .build();

        // Taller B2B: Precios base + IVA (TaxCalculationMode.EXCLUSIVE)
        TimePricingStrategy strategy = new TimePricingStrategy(TaxCalculationMode.EXCLUSIVE, new BigDecimal("0.25"));

        // 1.5 horas de trabajo mecánico con recargo de urgencia del 20%
        BigDecimal hours = new BigDecimal("1.50");
        List<ItemModifier> modifiers = Collections.singletonList(
                ItemModifier.percentageSurcharge("Servicio Urgente", new BigDecimal("20.00"))
        );

        CalculatedPrice result = strategy.calculate(labor, hours, modifiers);

        // Subtotal base: 1.5 h x 40.00 €/h = 60.00 €
        assertEquals(new BigDecimal("60.00"), result.getSubtotalBeforeModifiers());

        // Recargo urgencia: +20% sobre 60.00 € = +12.00 €
        assertEquals(new BigDecimal("12.00"), result.getModifiersDelta());

        // Base Imponible: 60.00 + 12.00 = 72.00 €
        assertEquals(new BigDecimal("72.00"), result.getNetSubtotal());

        // Cuota IVA 21%: 72.00 x 0.21 = 15.12 €
        assertEquals(new BigDecimal("15.12"), result.getTaxAmount());

        // Total Factura: 72.00 + 15.12 = 87.12 €
        assertEquals(new BigDecimal("87.12"), result.getTotalAmount());
    }

    // --- 4. SECTOR MAYORISTA / VOLUMEN: Tarificación por Tramos ---

    @Test
    public void testTieredVolumePricing_bulkDiscount() {
        CatalogItem part = CatalogItem.builder("PART-01", "Filtro Aceite Estándar", new BigDecimal("10.00"))
                .unit(MeasurementUnit.UNIT)
                .tax(TaxRate.GENERAL_21)
                .build();

        List<TieredVolumePricingStrategy.Tier> tiers = Arrays.asList(
                new TieredVolumePricingStrategy.Tier(new BigDecimal("1"), new BigDecimal("9"), new BigDecimal("10.00")),
                new TieredVolumePricingStrategy.Tier(new BigDecimal("10"), new BigDecimal("49"), new BigDecimal("8.00")),
                new TieredVolumePricingStrategy.Tier(new BigDecimal("50"), null, new BigDecimal("6.00"))
        );

        TieredVolumePricingStrategy strategy = new TieredVolumePricingStrategy(tiers, TaxCalculationMode.INCLUSIVE);

        // Compra de 25 unidades -> aplica tramo de 8.00 €/ud
        CalculatedPrice result = strategy.calculate(part, new BigDecimal("25"), Collections.emptyList());

        assertEquals(new BigDecimal("8.00"), result.getUnitPrice());
        // 25 x 8.00 = 200.00 €
        assertEquals(new BigDecimal("200.00"), result.getTotalAmount());
    }
}
