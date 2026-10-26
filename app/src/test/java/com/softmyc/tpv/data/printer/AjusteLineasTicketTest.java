package com.softmyc.tpv.data.printer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class AjusteLineasTicketTest {

    @Test
    public void textoCortoSeDevuelveEnUnaSolaLinea() {
        List<String> lineas = AjusteLineasTicket.envolver("Café con leche", 48, "", "    ");

        assertEquals(java.util.Collections.singletonList("Café con leche"), lineas);
    }

    @Test
    public void cortaEnElEspacioAntesDeSuperarElAncho() {
        List<String> lineas = AjusteLineasTicket.envolver("uno dos tres cuatro", 10, "", "  ");

        assertEquals(java.util.Arrays.asList("uno dos", "  tres", "  cuatro"), lineas);
    }

    @Test
    public void lineasDeContinuacionRespetanLaSangriaYNoSuperanElAncho() {
        String nombre = "Bocadillo de jamón serrano con tomate natural y aceite de oliva virgen extra";

        List<String> lineas = AjusteLineasTicket.envolver("1 x " + nombre, 24, "", "    ");

        for (String linea : lineas) {
            assertTrue("línea de " + linea.length() + " columnas: " + linea, linea.length() <= 24);
        }
        assertTrue(lineas.get(1).startsWith("    "));
        assertEquals("1 x " + nombre, unir(lineas).replaceAll("\\s+", " "));
    }

    @Test
    public void primeraLineaUsaSuPropiaSangria() {
        List<String> lineas = AjusteLineasTicket.envolver("- jamón extra con queso", 12, "    ", "      ");

        assertEquals(java.util.Arrays.asList("    - jamón", "      extra", "      con", "      queso"), lineas);
    }

    @Test
    public void palabraMasLargaQueLaLineaSePartePorElAncho() {
        List<String> lineas = AjusteLineasTicket.envolver("abcdefghijklmnopqrstuvwxyz", 10, "", "");

        assertEquals(java.util.Arrays.asList("abcdefghij", "klmnopqrst", "uvwxyz"), lineas);
    }

    @Test
    public void textoVacioNoDevuelveLineas() {
        assertTrue(AjusteLineasTicket.envolver("   ", 10, "", "").isEmpty());
    }

    private static String unir(List<String> lineas) {
        StringBuilder sb = new StringBuilder();
        for (String linea : lineas) {
            sb.append(linea).append(' ');
        }
        return sb.toString().trim();
    }
}
