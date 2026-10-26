package com.softmyc.tpv.data.local.cifrado;

/**
 * Error al preparar, migrar o abrir una base cifrada. Los mensajes nunca incluyen la clave.
 */
public class BaseCifradaException extends RuntimeException {

    public BaseCifradaException(String mensaje) {
        super(mensaje);
    }

    public BaseCifradaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
