package com.softmyc.tpv.domain.port;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import java.util.List;

/** Puerto que reconstruye las líneas del pedido a partir del formato en que se guardó la venta. */
public interface ItemsDeserializer {

    /** {@code productosDisponibles} puede ser null si el catálogo aún no se ha cargado. Lanza {@code DeserializacionVentaException} si el contenido guardado no se puede interpretar. */
    List<PedidoItem> deserializar(String itemsJson, List<Producto> productosDisponibles);
}
