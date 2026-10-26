package com.softmyc.tpv.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StockEditTest {

    @Test
    public void increment_fromUntracked_startsControlAtOne() {
        assertEquals(1, StockEdit.increment(-1));
    }

    @Test
    public void increment_addsOne() {
        assertEquals(8, StockEdit.increment(7));
    }

    @Test
    public void decrement_subtractsOneButStopsAtZero() {
        assertEquals(6, StockEdit.decrement(7));
        assertEquals(0, StockEdit.decrement(0));
    }

    @Test
    public void decrement_leavesUntrackedUntouched() {
        assertEquals(-1, StockEdit.decrement(-1));
    }

    @Test
    public void parse_emptyOrBlankMeansNoControl() {
        assertEquals(-1, StockEdit.parse(null));
        assertEquals(-1, StockEdit.parse("   "));
    }

    @Test
    public void parse_validNumber() {
        assertEquals(12, StockEdit.parse(" 12 "));
    }

    @Test
    public void parse_invalidOrNegativeFallsBackToNoControl() {
        assertEquals(-1, StockEdit.parse("abc"));
        assertEquals(-1, StockEdit.parse("-5"));
        assertEquals(-1, StockEdit.parse("99999999999"));
    }
}
