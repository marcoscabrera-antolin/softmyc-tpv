package com.softmyc.tpv.domain;

import static org.junit.Assert.assertEquals;

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

/**
 * NEG-08: semántica única de recargos. Un recargo fijo es por unidad facturada (unidad, kg neto
 * o hora facturable) y se multiplica por la cantidad; un porcentaje se aplica sobre el precio unitario.
 */
public class ExtrasPorUnidadTest {

    private static final TaxCalculationMode EXCLUSIVO = TaxCalculationMode.EXCLUSIVE;

    @Test
    public void standard_recargoFijoSeMultiplicaPorUnidades() {
        CatalogItem servicioMesa = CatalogItem.builder("SERV-MESA", "Servicio de mesa", new BigDecimal("1.00"))
                .unit(MeasurementUnit.UNIT)
                .tax(TaxRate.REDUCED_10)
                .build();

        CalculatedPrice r = new StandardUnitPricingStrategy(EXCLUSIVO).calculate(
                servicioMesa, new BigDecimal("3"),
                Collections.singletonList(ItemModifier.fixedSurcharge("Extra", new BigDecimal("0.50"))));

        assertEquals(new BigDecimal("1.50"), r.getModifiersDelta());
    }

    @Test
    public void weight_recargoFijoSeMultiplicaPorKilos() {
        CatalogItem fruta = CatalogItem.builder("FRU-01", "Manzanas", new BigDecimal("2.40"))
                .unit(MeasurementUnit.WEIGHT_KG)
                .tax(TaxRate.SUPER_REDUCED_4)
                .build();

        CalculatedPrice r = new WeightPricingStrategy(EXCLUSIVO).calculate(
                fruta, new BigDecimal("2.000"),
                Collections.singletonList(ItemModifier.fixedSurcharge("Envasado", new BigDecimal("0.50"))));

        assertEquals(new BigDecimal("1.00"), r.getModifiersDelta());
    }

    @Test
    public void weight_recargoFijoUsaElPesoNetoTrasLaTara() {
        CatalogItem fruta = CatalogItem.builder("FRU-01", "Manzanas", new BigDecimal("2.40"))
                .unit(MeasurementUnit.WEIGHT_KG)
                .tax(TaxRate.SUPER_REDUCED_4)
                .build();

        CalculatedPrice r = new WeightPricingStrategy(EXCLUSIVO).calculate(
                fruta, new BigDecimal("2.000"),
                Arrays.asList(
                        ItemModifier.tareDeduction("Tara envase", new BigDecimal("0.200")),
                        ItemModifier.fixedSurcharge("Envasado", new BigDecimal("0.50"))));

        // Neto 1.800 kg x 0.50 €
        assertEquals(new BigDecimal("0.90"), r.getModifiersDelta());
    }

    @Test
    public void weight_porcentajeSeAplicaSobrePrecioPorKiloPorNeto() {
        CatalogItem fruta = CatalogItem.builder("FRU-01", "Manzanas", new BigDecimal("2.40"))
                .unit(MeasurementUnit.WEIGHT_KG)
                .tax(TaxRate.SUPER_REDUCED_4)
                .build();

        CalculatedPrice r = new WeightPricingStrategy(EXCLUSIVO).calculate(
                fruta, new BigDecimal("2.000"),
                Collections.singletonList(ItemModifier.percentageSurcharge("Urgente", new BigDecimal("10"))));

        // 10 % de 2.40 €/kg = 0.24 €/kg x 2 kg
        assertEquals(new BigDecimal("0.48"), r.getModifiersDelta());
    }

    @Test
    public void time_recargoFijoSeMultiplicaPorHorasFacturables() {
        CatalogItem mano = CatalogItem.builder("SERV-01", "Mano de obra", new BigDecimal("40.00"))
                .unit(MeasurementUnit.TIME_HOUR)
                .tax(TaxRate.GENERAL_21)
                .build();

        CalculatedPrice r = new TimePricingStrategy(EXCLUSIVO, BigDecimal.ZERO).calculate(
                mano, new BigDecimal("1.50"),
                Collections.singletonList(ItemModifier.fixedSurcharge("Tasa residuos", new BigDecimal("5.00"))));

        assertEquals(new BigDecimal("7.50"), r.getModifiersDelta());
    }

    @Test
    public void tiered_recargoFijoSeMultiplicaPorUnidades() {
        CatalogItem caja = CatalogItem.builder("MAY-01", "Caja mayorista", new BigDecimal("2.00"))
                .unit(MeasurementUnit.UNIT)
                .tax(TaxRate.GENERAL_21)
                .build();

        CalculatedPrice r = new TieredVolumePricingStrategy(Collections.emptyList(), EXCLUSIVO).calculate(
                caja, new BigDecimal("3"),
                Collections.singletonList(ItemModifier.fixedSurcharge("Etiqueta", new BigDecimal("0.50"))));

        assertEquals(new BigDecimal("1.50"), r.getModifiersDelta());
    }
}
