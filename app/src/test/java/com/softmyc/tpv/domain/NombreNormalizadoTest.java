package com.softmyc.tpv.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NombreNormalizadoTest {

    @Test
    public void quita_acentos_mayusculas_y_espacios_sobrantes() {
        assertEquals("jamon", NombreNormalizado.de("Jamón"));
        assertEquals("tomate natural", NombreNormalizado.de("  Tomate   Natural "));
        assertEquals("esparragos", NombreNormalizado.de("ESPÁRRAGOS"));
        assertEquals("nata", NombreNormalizado.de("Ñata"));
        assertEquals("", NombreNormalizado.de(null));
        assertEquals("", NombreNormalizado.de("   "));
    }
}
