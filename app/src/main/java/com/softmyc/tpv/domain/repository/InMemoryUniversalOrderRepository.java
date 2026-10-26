package com.softmyc.tpv.domain.repository;

import com.softmyc.tpv.domain.model.UniversalOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación en memoria del repositorio de pedidos universales.
 * Ideal para pruebas unitarias, benchmarks y ejecución en entornos sin base de datos Android.
 */
public class InMemoryUniversalOrderRepository implements UniversalOrderRepository {

    private final Map<String, UniversalOrder> storage = new LinkedHashMap<>();

    @Override
    public synchronized void save(UniversalOrder order) {
        if (order != null) {
            storage.put(order.getId(), order);
        }
    }

    @Override
    public synchronized UniversalOrder findById(String id) {
        return storage.get(id);
    }

    @Override
    public synchronized List<UniversalOrder> findRecent(int limit) {
        List<UniversalOrder> list = new ArrayList<>(storage.values());
        Collections.reverse(list); // Los más recientes primero
        if (limit > 0 && list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }

    @Override
    public synchronized List<UniversalOrder> findBySector(String sector, int limit) {
        if (sector == null) return Collections.emptyList();
        List<UniversalOrder> filtered = new ArrayList<>();
        List<UniversalOrder> all = findRecent(0);

        for (UniversalOrder order : all) {
            if (sector.equalsIgnoreCase(order.getBusinessSector())) {
                filtered.add(order);
                if (limit > 0 && filtered.size() >= limit) break;
            }
        }
        return filtered;
    }

    public synchronized void clear() {
        storage.clear();
    }

    public synchronized int count() {
        return storage.size();
    }
}
