package com.softmyc.tpv.domain;

/**
 * Las líneas de una venta no se pudieron serializar. La venta NO debe guardarse: sin detalle el total
 * no se puede auditar ni subir. Quien guarda la venta debe mostrar el aviso al usuario.
 */
public class SerializacionVentaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SerializacionVentaException(String message, Throwable cause) {
        super(message, cause);
    }
}
