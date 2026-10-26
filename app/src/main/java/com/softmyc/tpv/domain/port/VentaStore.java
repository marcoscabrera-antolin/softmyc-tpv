package com.softmyc.tpv.domain.port;

/**
 * Puerto de persistencia de ventas del cobro. La implementación guarda la venta y ajusta su stock
 * de forma atómica; el dominio no sabe si detrás hay Room u otra fuente.
 */
public interface VentaStore {

    /**
     * Guarda la venta del cobro (nueva o edición según {@link VentaCobro#ventaId}).
     *
     * @return el id de la venta guardada (el número del ticket).
     * @throws RuntimeException si no se puede guardar; el caso de uso lo notifica como error de guardado.
     */
    long guardar(VentaCobro cobro);
}
