package com.softmyc.tpv.domain;

/**
 * Error controlado al calcular el importe de un pedido o de una de sus líneas (datos incoherentes,
 * como un peso neto no positivo o una línea sin producto). Sustituye a las excepciones crudas del
 * motor para que la UI muestre un aviso en vez de cerrarse.
 */
public class CalculoPedidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CalculoPedidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
