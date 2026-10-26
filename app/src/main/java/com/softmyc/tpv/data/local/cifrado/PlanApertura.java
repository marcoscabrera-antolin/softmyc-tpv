package com.softmyc.tpv.data.local.cifrado;

/**
 * Decisión pura de qué hacer con una base según su estado en disco y si existe clave guardada.
 */
public final class PlanApertura {

    public enum Accion { CREAR_CIFRADA, MIGRAR_A_CIFRADA, ABRIR_CIFRADA, CLAVE_AUSENTE }

    private PlanApertura() {
    }

    public static Accion decidir(EstadoBase.Estado estado, boolean hayClaveGuardada) {
        switch (estado) {
            case EN_CLARO:
                return Accion.MIGRAR_A_CIFRADA;
            case CIFRADO:
                return hayClaveGuardada ? Accion.ABRIR_CIFRADA : Accion.CLAVE_AUSENTE;
            case AUSENTE:
            default:
                return Accion.CREAR_CIFRADA;
        }
    }
}
