package com.softmyc.tpv.data.repo;

import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.port.ItemsDeserializer;
import com.softmyc.tpv.domain.port.ItemsSerializer;

import java.util.List;
import java.util.Map;

/** Implementación de {@link ItemsSerializer} con el formato JSON de sincronización. */
public final class ItemsSerializerSync implements ItemsSerializer, ItemsDeserializer {

    @Override
    public String serializar(List<PedidoItem> items, Map<String, Double> preciosExtras) {
        return SyncManager.serializarItems(items, preciosExtras);
    }

    @Override
    public List<PedidoItem> deserializar(String itemsJson, List<Producto> productosDisponibles) {
        return SyncManager.deserializarItems(itemsJson, productosDisponibles);
    }
}
