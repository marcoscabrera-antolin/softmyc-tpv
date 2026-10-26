package com.softmyc.tpv.domain;

/**
 * El detalle guardado de una venta (líneas en JSON) no se puede leer. Nunca se devuelve una lista parcial:
 * quien la consume debe mostrar que el detalle no está disponible en lugar de un resumen incompleto.
 */
public class DeserializacionVentaException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DeserializacionVentaException(String message, Throwable cause) {
        super(message, cause);
    }
}
