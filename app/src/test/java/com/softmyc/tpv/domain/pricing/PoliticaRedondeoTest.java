package com.softmyc.tpv.domain.pricing;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Test;

public class PoliticaRedondeoTest {

    @Test
    public void precioUnitarioSeRedondeaACentimosHaciaArriba() {
        assertEquals(new BigDecimal("0.33"), PoliticaRedondeo.aCentimos(0.333));
        assertEquals(new BigDecimal("0.13"), PoliticaRedondeo.aCentimos(0.125));
        assertEquals(new BigDecimal("1.01"), PoliticaRedondeo.aCentimos(1.005));
    }

    @Test
    public void redondeoSobreDoubleNoDependeDelBinario() {
        assertEquals(new BigDecimal("0.30"), PoliticaRedondeo.aCentimos(0.1 + 0.2));
    }

    @Test
    public void tresUnidadesDeUnPrecioDeTresDecimalesDaElTotalDeCentimos() {
        BigDecimal unitario = PoliticaRedondeo.aCentimos(0.333);
        BigDecimal total = unitario.multiply(BigDecimal.valueOf(3));

        assertEquals(new BigDecimal("0.99"), PoliticaRedondeo.aCentimos(total));
    }

    @Test
    public void importeYaEnCentimosNoCambia() {
        assertEquals(new BigDecimal("4.50"), PoliticaRedondeo.aCentimos(new BigDecimal("4.50")));
    }

    @Test
    public void nuloSeTrataComoCero() {
        assertEquals(new BigDecimal("0.00"), PoliticaRedondeo.aCentimos((BigDecimal) null));
    }
}
