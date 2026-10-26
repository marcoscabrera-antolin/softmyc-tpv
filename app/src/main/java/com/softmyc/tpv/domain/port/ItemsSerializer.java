package com.softmyc.tpv.domain.port;

import com.softmyc.tpv.domain.PedidoItem;

import java.util.List;
import java.util.Map;

/** Puerto que serializa las líneas del pedido al formato que se guarda en la venta. */
public interface ItemsSerializer {

    String serializar(List<PedidoItem> items, Map<String, Double> preciosExtras);
}
