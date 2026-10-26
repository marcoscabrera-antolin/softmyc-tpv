package com.softmyc.tpv.domain.service;

import com.softmyc.tpv.domain.model.UniversalOrder;

/**
 * Contrato de servicio de dominio para la generación y emisión de tickets/facturas.
 * Desacopla la lógica de impresión del SDK de Android, sockets TCP o drivers ESC/POS.
 */
public interface UniversalPrinterService {

    /**
     * Formatea un pedido comercial en una representación textual lista para ticket térmico.
     */
    String formatTicket(UniversalOrder order, String storeName);

    /**
     * Envía a imprimir el pedido a través del driver configurado.
     */
    void printTicket(UniversalOrder order, String storeName) throws Exception;
}
