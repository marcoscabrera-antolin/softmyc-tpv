package com.softmyc.tpv.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Semáforo de existencias: lógica real usada por StockActivity. */
public class StockLogicTest {

    @Test
    public void levelOf_minusOne_isUntracked() {
        assertEquals(StockLevel.UNTRACKED, StockLevel.of(-1));
    }

    @Test
    public void levelOf_zero_isOutOfStock() {
        assertEquals(StockLevel.OUT, StockLevel.of(0));
    }

    @Test
    public void levelOf_oneToFive_isLow() {
        assertEquals(StockLevel.LOW, StockLevel.of(1));
        assertEquals(StockLevel.LOW, StockLevel.of(StockLevel.LOW_THRESHOLD));
    }

    @Test
    public void levelOf_aboveThreshold_isOk() {
        assertEquals(StockLevel.OK, StockLevel.of(StockLevel.LOW_THRESHOLD + 1));
        assertEquals(StockLevel.OK, StockLevel.of(500));
    }

    @Test
    public void levelOf_anyNegative_isUntracked() {
        assertEquals(StockLevel.UNTRACKED, StockLevel.of(-50));
    }
}
