package com.softmyc.tpv.data.local;

import com.softmyc.tpv.data.sync.CatalogOutbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Actualización del catálogo local en una única transacción de Room. Conserva los datos que solo
 * existen en el dispositivo (stock y unidad de medida) y nunca vacía el catálogo si la entrada
 * (respuesta del servidor o CSV) no trae productos.
 */
public final class CatalogSync {

    private CatalogSync() { }

    /**
     * Sincronización desde el servidor: los productos reflejan los del servidor (se eliminan los que
     * ya no existen allí), los extras se añaden o actualizan y las salsas se sustituyen si llegan.
     *
     * @return false si no había productos y no se tocó nada.
     */
    public static boolean syncFromServer(AppDatabase db, List<ProductoLocal> productos,
                                         List<ExtraLocal> extras, List<SalsaLocal> salsas) {
        return syncFromServer(db, productos, extras, salsas, Collections.<Integer, String>emptyMap());
    }

    /**
     * Igual que {@link #syncFromServer(AppDatabase, List, List, List)}, pero protege los cambios de catálogo aún no
     * enviados al servidor ({@code pendientes}: código de producto → tipo de operación de {@link CatalogOutbox}).
     * Un alta o edición pendiente conserva la versión local; un borrado pendiente no se resucita desde el servidor.
     *
     * @return false si no había productos y no se tocó nada.
     */
    public static boolean syncFromServer(AppDatabase db, List<ProductoLocal> productos,
                                         List<ExtraLocal> extras, List<SalsaLocal> salsas,
                                         Map<Integer, String> pendientes) {
        if (productos == null || productos.isEmpty()) return false;
        db.runInTransaction(() -> {
            replaceProductsKeepingLocalData(db, productos, pendientes);
            if (extras != null && !extras.isEmpty()) db.extraDao().insertAll(extras);
            if (salsas != null && !salsas.isEmpty()) {
                db.salsaDao().deleteAll();
                db.salsaDao().insertAll(salsas);
            }
        });
        return true;
    }

    /** Convierte la cola pendiente de un negocio en el mapa código → tipo que espera {@link #syncFromServer}. */
    public static Map<Integer, String> pendientesDe(List<CatalogOutbox.Op> operaciones) {
        Map<Integer, String> pendientes = new HashMap<>();
        if (operaciones == null) return pendientes;
        for (CatalogOutbox.Op op : operaciones) {
            pendientes.put(op.codigo, op.tipo);
        }
        return pendientes;
    }

    /**
     * Importación desde CSV: sustituye los productos, y los extras y salsas solo si el CSV los trae.
     *
     * @return false si el CSV no contenía productos y no se tocó nada.
     */
    public static boolean replaceCatalog(AppDatabase db, List<ProductoLocal> productos,
                                         List<ExtraLocal> extras, List<SalsaLocal> salsas) {
        if (productos == null || productos.isEmpty()) return false;
        db.runInTransaction(() -> {
            replaceProductsKeepingLocalData(db, productos, Collections.<Integer, String>emptyMap());
            if (extras != null && !extras.isEmpty()) {
                db.extraDao().deleteAll();
                db.extraDao().insertAll(extras);
            }
            if (salsas != null && !salsas.isEmpty()) {
                db.salsaDao().deleteAll();
                db.salsaDao().insertAll(salsas);
            }
        });
        return true;
    }

    private static void replaceProductsKeepingLocalData(AppDatabase db, List<ProductoLocal> incoming,
                                                        Map<Integer, String> pendientes) {
        Map<Integer, ProductoLocal> existing = new HashMap<>();
        for (ProductoLocal p : db.productoDao().getAll()) existing.put(p.codigo, p);

        List<ProductoLocal> resultado = new ArrayList<>();
        Set<Integer> codigosRecibidos = new HashSet<>();
        for (ProductoLocal p : incoming) {
            codigosRecibidos.add(p.codigo);
            String pendiente = pendientes.get(p.codigo);
            if (CatalogOutbox.DELETE.equals(pendiente)) continue;
            ProductoLocal previous = existing.get(p.codigo);
            if (CatalogOutbox.UPSERT.equals(pendiente) && previous != null) {
                resultado.add(previous);
                continue;
            }
            if (previous != null) {
                p.stock = previous.stock;
                if ("UD".equals(p.unidadMedida) && previous.unidadMedida != null) {
                    p.unidadMedida = previous.unidadMedida;
                }
            }
            resultado.add(p);
        }
        for (Map.Entry<Integer, String> entrada : pendientes.entrySet()) {
            ProductoLocal local = existing.get(entrada.getKey());
            boolean altaLocalPendiente = CatalogOutbox.UPSERT.equals(entrada.getValue());
            if (altaLocalPendiente && local != null && !codigosRecibidos.contains(entrada.getKey())) {
                resultado.add(local);
            }
        }

        db.productoDao().deleteAll();
        db.productoDao().insertAll(resultado);
    }
}
