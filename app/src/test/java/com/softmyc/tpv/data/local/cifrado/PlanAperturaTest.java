package com.softmyc.tpv.data.local.cifrado;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlanAperturaTest {

    @Test
    public void baseAusenteSeCreaCifradaConOSinClave() {
        assertEquals(PlanApertura.Accion.CREAR_CIFRADA,
                PlanApertura.decidir(EstadoBase.Estado.AUSENTE, false));
        assertEquals(PlanApertura.Accion.CREAR_CIFRADA,
                PlanApertura.decidir(EstadoBase.Estado.AUSENTE, true));
    }

    @Test
    public void baseEnClaroSeMigraAunqueLaClaveYaExistaPorUnaMigracionInterrumpida() {
        assertEquals(PlanApertura.Accion.MIGRAR_A_CIFRADA,
                PlanApertura.decidir(EstadoBase.Estado.EN_CLARO, false));
        assertEquals(PlanApertura.Accion.MIGRAR_A_CIFRADA,
                PlanApertura.decidir(EstadoBase.Estado.EN_CLARO, true));
    }

    @Test
    public void baseCifradaConClaveSeAbre() {
        assertEquals(PlanApertura.Accion.ABRIR_CIFRADA,
                PlanApertura.decidir(EstadoBase.Estado.CIFRADO, true));
    }

    @Test
    public void baseCifradaSinClaveNuncaSeRegeneraLaClave() {
        assertEquals(PlanApertura.Accion.CLAVE_AUSENTE,
                PlanApertura.decidir(EstadoBase.Estado.CIFRADO, false));
    }
}
