package com.softmyc.tpv.domain.repository;

import com.softmyc.tpv.domain.model.UniversalOrder;

import java.util.List;

/**
 * Contrato de repositorio de dominio puro para pedidos y ventas universales.
 * Cumple estrictamente con Clean Architecture y el Principio de Inversión de Dependencias (DIP):
 * el dominio define qué operaciones de persistencia necesita sin conocer Room, SQLite ni Android.
 */
public interface UniversalOrderRepository {

    /**
     * Guarda o actualiza un pedido en el medio de almacenamiento persistente.
     */
    void save(UniversalOrder order);

    /**
     * Recupera un pedido por su identificador único.
     */
    UniversalOrder findById(String id);

    /**
     * Obtiene los N pedidos más recientes ordenados cronológicamente descendente.
     */
    List<UniversalOrder> findRecent(int limit);

    /**
     * Obtiene los pedidos filtrados por sector comercial.
     */
    List<UniversalOrder> findBySector(String sector, int limit);
}
