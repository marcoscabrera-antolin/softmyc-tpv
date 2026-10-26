package com.softmyc.tpv.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetWeightTest {

    @Test
    public void isValid_whenGrossExceedsTare() {
        assertTrue(NetWeight.isValid(1.850, 0.120));
    }

    @Test
    public void isValid_false_whenGrossEqualsTare() {
        assertFalse(NetWeight.isValid(0.350, 0.350));
    }

    @Test
    public void isValid_false_whenGrossBelowTare() {
        assertFalse(NetWeight.isValid(0.100, 0.350));
    }

    @Test
    public void isValid_true_withoutTare() {
        assertTrue(NetWeight.isValid(0.100, 0.0));
    }

    @Test
    public void net_isRoundedToGrams() {
        assertEquals(1.730, NetWeight.net(1.850, 0.120), 0.0001);
    }
}
