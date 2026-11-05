package com.softmyc.tpv.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class CalculadoraRentabilidadTest {

    /** Nube del documento de costes: PVP 5,50; ingredientes 3,50; gastos 1,69 → 0,31 € y 6,0 %. */
    @Test
    public void bruto_neto_y_margen_como_el_documento_de_costes() {
        CalculadoraRentabilidad.Resumen r = CalculadoraRentabilidad.calcular(5.50,
                Collections.singletonList(new CalculadoraRentabilidad.Linea("Nube", 1, 5.50, 3.50, 1.69)));

        assertEquals(5.50, r.ventas, 1e-9);
        assertEquals(2.00, r.beneficioBruto, 1e-9);
        assertEquals(0.31, r.beneficioNeto, 1e-9);
        assertEquals(0.060, r.margen, 0.0005);
    }

    @Test
    public void las_lineas_sin_coste_no_cuentan_como_beneficio_y_se_informan() {
        CalculadoraRentabilidad.Resumen r = CalculadoraRentabilidad.calcular(12.0, Arrays.asList(
                new CalculadoraRentabilidad.Linea("Nube", 2, 11.0, 7.0, 3.38),
                new CalculadoraRentabilidad.Linea("Tigger", 1, 1.0, null, null),
                new CalculadoraRentabilidad.Linea("Raro", 1, 0.0, 1.0, null)));

        assertEquals(12.0, r.ventas, 1e-9);
        assertEquals(11.0, r.ventasConCoste, 1e-9);
        assertEquals(4.0, r.beneficioBruto, 1e-9);
        assertEquals(0.62, r.beneficioNeto, 1e-9);
        assertEquals(2.0, r.unidadesSinCoste, 1e-9);
    }

    @Test
    public void ranking_por_beneficio_neto_y_los_sin_coste_al_final_por_importe() {
        CalculadoraRentabilidad.Resumen r = CalculadoraRentabilidad.calcular(30.0, Arrays.asList(
                new CalculadoraRentabilidad.Linea("Nube", 1, 5.5, 3.5, 1.69),
                new CalculadoraRentabilidad.Linea("Jamoncito", 1, 6.0, 1.84, 1.69),
                new CalculadoraRentabilidad.Linea("Sin coste A", 1, 3.0, null, null),
                new CalculadoraRentabilidad.Linea("Sin coste B", 1, 9.0, null, null),
                new CalculadoraRentabilidad.Linea("Nube", 1, 5.5, 3.5, 1.69)));

        assertEquals("Jamoncito", r.ranking.get(0).producto);
        assertEquals("Nube", r.ranking.get(1).producto);
        assertEquals(2.0, r.ranking.get(1).unidades, 1e-9);
        assertEquals(0.62, r.ranking.get(1).beneficioNeto, 1e-9);
        assertEquals("Sin coste B", r.ranking.get(2).producto);
        assertNull(r.ranking.get(3).beneficioNeto);
    }

    @Test
    public void periodo_sin_ventas_no_divide_entre_cero() {
        CalculadoraRentabilidad.Resumen r = CalculadoraRentabilidad.calcular(0, null);

        assertEquals(0.0, r.beneficioNeto, 1e-9);
        assertNull(r.margen);
        assertEquals(0, r.ranking.size());
    }

    @Test
    public void rentabilidad_unitaria() {
        CalculadoraRentabilidad.Unitario u = CalculadoraRentabilidad.unitario(6.0, 1.84, 1.69);
        assertEquals(3.53, u.costeTotal, 1e-9);
        assertEquals(2.47, u.beneficio, 1e-9);
        assertEquals(0.70, u.margen, 0.001);

        assertEquals(1.84, CalculadoraRentabilidad.unitario(6.0, 1.84, null).costeTotal, 1e-9);
        assertNull(CalculadoraRentabilidad.unitario(6.0, null, 1.69));
        assertNull(CalculadoraRentabilidad.unitario(6.0, 0.0, 0.0).margen);
    }
}
