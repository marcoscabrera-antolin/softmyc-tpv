package com.softmyc.tpv.data.sync;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Cola de cambios de catálogo hechos en la tablet que aún no están en el servidor. Mientras no esté vacía,
 * la sincronización no sustituye el catálogo local por el del servidor: así una edición hecha sin red
 * no se pierde en la siguiente descarga.
 *
 * Guarda como mucho una operación por producto (la última). Cada operación lleva un número de secuencia:
 * una operación enviada solo se quita si sigue siendo la misma, no si el producto se reeditó mientras tanto.
 */
public final class CatalogOutbox {
    public static final String UPSERT = "upsert";
    public static final String DELETE = "delete";

    private static final String CLAVE_SECUENCIA = "catalog_outbox_seq";
    /** Compartido por todas las instancias: add (hilo de la pantalla) y remove (hilo de red) no deben pisarse. */
    private static final Object LOCK = new Object();

    public static final class Op {
        public final String tipo;
        public final int codigo;
        public final long secuencia;

        Op(String tipo, int codigo, long secuencia) {
            this.tipo = tipo;
            this.codigo = codigo;
            this.secuencia = secuencia;
        }
    }

    private final SharedPreferences prefs;

    public CatalogOutbox(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    private static String clave(String tenant) {
        return "catalog_outbox_" + tenant;
    }

    public void add(String tenant, String tipo, int codigo) {
        synchronized (LOCK) {
            long secuencia = prefs.getLong(CLAVE_SECUENCIA, 0L) + 1;
            List<Op> ops = new ArrayList<>();
            for (Op op : leer(tenant)) {
                if (op.codigo != codigo) ops.add(op);
            }
            ops.add(new Op(tipo, codigo, secuencia));
            guardar(tenant, ops, secuencia);
        }
    }

    /** Quita la operación solo si sigue siendo exactamente la enviada (misma secuencia). */
    public void remove(String tenant, Op enviada) {
        synchronized (LOCK) {
            List<Op> ops = new ArrayList<>();
            for (Op op : leer(tenant)) {
                if (op.secuencia != enviada.secuencia) ops.add(op);
            }
            guardar(tenant, ops, prefs.getLong(CLAVE_SECUENCIA, 0L));
        }
    }

    public List<Op> pending(String tenant) {
        synchronized (LOCK) {
            return leer(tenant);
        }
    }

    public boolean isEmpty(String tenant) {
        return pending(tenant).isEmpty();
    }

    private List<Op> leer(String tenant) {
        List<Op> ops = new ArrayList<>();
        String guardado = prefs.getString(clave(tenant), "[]");
        try {
            JSONArray arr = new JSONArray(guardado);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ops.add(new Op(o.getString("tipo"), o.getInt("codigo"), o.getLong("seq")));
            }
        } catch (Exception e) {
            // Cola ilegible: se trata como vacía en lugar de bloquear la sincronización para siempre.
            android.util.Log.e("CatalogOutbox", "Cola de catálogo ilegible, se descarta: " + e.getMessage());
        }
        return ops;
    }

    private void guardar(String tenant, List<Op> ops, long secuencia) {
        try {
            JSONArray arr = new JSONArray();
            for (Op op : ops) {
                arr.put(new JSONObject().put("tipo", op.tipo).put("codigo", op.codigo).put("seq", op.secuencia));
            }
            // commit(): se escribe en el momento, no en segundo plano, para no perder el cambio si el proceso muere.
            prefs.edit().putString(clave(tenant), arr.toString()).putLong(CLAVE_SECUENCIA, secuencia).commit();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo guardar la cola de catálogo", e);
        }
    }
}
