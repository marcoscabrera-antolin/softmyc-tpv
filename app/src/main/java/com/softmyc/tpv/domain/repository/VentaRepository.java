package com.softmyc.tpv.domain.repository;

import java.util.List;

/**
 * Contrato del repositorio de consultas de ventas. La capa domain define QUÉ se necesita; la capa data implementa CÓMO.
 * Guardar la venta es responsabilidad del caso de uso de cobro (puerto VentaStore), no de este repositorio.
 */
public interface VentaRepository {

    /** Ventas desde el instante dado (ms), ordenadas de más nueva a más antigua. */
    List<VentaResumen> getDesde(long inicioMs);

    /** Las {@code limite} ventas más recientes, de más nueva a más antigua. */
    List<VentaResumen> getRecientes(int limite);

    /**
     * Obtiene una venta concreta por su ID de ticket.
     * @return la venta, o null si no existe.
     */
    VentaResumen getById(int id);
}
