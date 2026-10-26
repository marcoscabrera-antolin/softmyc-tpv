package com.softmyc.tpv.domain.port;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AjustesImpresoraTest {

    @Test
    public void puertoFueraDeRango_usaElPuertoPorDefecto() {
        assertEquals(AjustesImpresora.PUERTO_POR_DEFECTO, AjustesImpresora.of(0, 70_000).puerto);
        assertEquals(AjustesImpresora.PUERTO_POR_DEFECTO, AjustesImpresora.of(0, 0).puerto);
    }

    @Test
    public void puertoValido_seConserva() {
        assertEquals(8080, AjustesImpresora.of(0, 8080).puerto);
    }

    @Test
    public void letraPequena_tieneVeinticuatroColumnasYLaNormalCuarentaYOcho() {
        assertEquals(24, AjustesImpresora.of(2, 9100).columnas());
        assertEquals(48, AjustesImpresora.of(0, 9100).columnas());
        assertEquals(48, AjustesImpresora.of(1, 9100).columnas());
    }
}
