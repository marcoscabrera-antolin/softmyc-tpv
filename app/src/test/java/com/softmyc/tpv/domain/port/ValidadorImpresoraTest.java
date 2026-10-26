package com.softmyc.tpv.domain.port;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ValidadorImpresoraTest {

    @Test
    public void puertoTextoAceptaElRangoTcp() {
        assertEquals(1, ValidadorImpresora.puertoValido("1"));
        assertEquals(9100, ValidadorImpresora.puertoValido("9100"));
        assertEquals(65535, ValidadorImpresora.puertoValido("65535"));
    }

    @Test
    public void puertoTextoRechazaFueraDeRangoYNoNumericos() {
        for (String s : new String[]{"", "0", "-1", "65536", "99999999999999999999", "abc", "1.5", " ", "🍔"}) {
            assertEquals(s, ValidadorImpresora.PUERTO_INVALIDO, ValidadorImpresora.puertoValido(s));
        }
    }

    @Test
    public void direccionAceptaIPv4Valida() {
        assertTrue(ValidadorImpresora.esDireccionValida("192.168.0.20"));
        assertTrue(ValidadorImpresora.esDireccionValida("10.0.0.1"));
        assertTrue(ValidadorImpresora.esDireccionValida("255.255.255.255"));
    }

    @Test
    public void direccionRechazaIPv4Malformada() {
        assertFalse(ValidadorImpresora.esDireccionValida("999.999.1.1"));
        assertFalse(ValidadorImpresora.esDireccionValida("192.168.0"));
        assertFalse(ValidadorImpresora.esDireccionValida("192.168.0.1.5"));
        assertFalse(ValidadorImpresora.esDireccionValida("192.168.01.1-"));
        assertFalse(ValidadorImpresora.esDireccionValida("256.1.1.1"));
    }

    @Test
    public void direccionAceptaHostnameRazonable() {
        assertTrue(ValidadorImpresora.esDireccionValida("impresora"));
        assertTrue(ValidadorImpresora.esDireccionValida("cocina-1.local"));
        assertTrue(ValidadorImpresora.esDireccionValida("printer.tpv.example.com"));
    }

    @Test
    public void direccionRechazaHostnameInvalido() {
        assertFalse(ValidadorImpresora.esDireccionValida(null));
        assertFalse(ValidadorImpresora.esDireccionValida(""));
        assertFalse(ValidadorImpresora.esDireccionValida("-impresora"));
        assertFalse(ValidadorImpresora.esDireccionValida("impresora-"));
        assertFalse(ValidadorImpresora.esDireccionValida("impre sora"));
        assertFalse(ValidadorImpresora.esDireccionValida("impresora..local"));
        assertFalse(ValidadorImpresora.esDireccionValida("http://192.168.0.20"));
        assertFalse(ValidadorImpresora.esDireccionValida("192.168.0.20:9100"));
        assertFalse(ValidadorImpresora.esDireccionValida("🍔.local"));
        assertFalse(ValidadorImpresora.esDireccionValida(repetir('a', 254)));
    }

    private static String repetir(char c, int veces) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < veces; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
