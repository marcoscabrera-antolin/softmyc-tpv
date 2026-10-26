package com.softmyc.tpv.domain;

import static org.junit.Assert.assertEquals;

import com.softmyc.tpv.domain.pricing.VolumeTierSchedule;
import com.softmyc.tpv.domain.usecase.CalcularTotalUseCase;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Collections;

public class VolumePricingTest {

    private static final double DELTA = 0.001;

    private static PedidoItem wholesale(int qty) {
        Producto p = new Producto(401, "Aceite caja 5L", 45.00, "Mayorista", Collections.emptyList());
        return new PedidoItem(p, qty);
    }

    @Test
    public void unitPrice_belowFirstTier_isBasePrice() {
        assertEquals(new BigDecimal("45.00"), VolumeTierSchedule.unitPriceFor(new BigDecimal("45.00"), new BigDecimal("9")));
    }

    @Test
    public void unitPrice_tenToFortyNine_isTenPercentOff() {
        assertEquals(new BigDecimal("40.50"), VolumeTierSchedule.unitPriceFor(new BigDecimal("45.00"), new BigDecimal("10")));
        assertEquals(new BigDecimal("40.50"), VolumeTierSchedule.unitPriceFor(new BigDecimal("45.00"), new BigDecimal("49")));
    }

    @Test
    public void unitPrice_fiftyOrMore_isTwentyPercentOff() {
        assertEquals(new BigDecimal("36.00"), VolumeTierSchedule.unitPriceFor(new BigDecimal("45.00"), new BigDecimal("50")));
    }

    @Test
    public void unitPrice_scalesWithAnyBasePrice() {
        assertEquals(new BigDecimal("25.65"), VolumeTierSchedule.unitPriceFor(new BigDecimal("28.50"), new BigDecimal("10")));
    }

    @Test
    public void total_forFiftyUnits_usesTheTierPrice() {
        double total = new CalcularTotalUseCase().calcularTotal(Collections.singletonList(wholesale(50)), Collections.emptyMap());

        assertEquals(36.00 * 50, total, DELTA);
    }

    @Test
    public void total_forTenUnits_usesTheTierPrice() {
        double total = new CalcularTotalUseCase().calcularTotal(Collections.singletonList(wholesale(10)), Collections.emptyMap());

        assertEquals(40.50 * 10, total, DELTA);
    }

    @Test
    public void total_forFewUnits_usesBasePrice() {
        double total = new CalcularTotalUseCase().calcularTotal(Collections.singletonList(wholesale(3)), Collections.emptyMap());

        assertEquals(45.00 * 3, total, DELTA);
    }
}
