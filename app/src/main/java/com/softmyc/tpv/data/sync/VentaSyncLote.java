package com.softmyc.tpv.data.sync;

import com.softmyc.tpv.data.local.VentaLocal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prepara la subida de ventas pendientes (troceo en lotes y cuerpo JSON) e interpreta la respuesta de
 * {@code POST /orders/sync} distinguiendo ventas confirmadas, rechazadas y errores del servidor.
 */
public final class VentaSyncLote {
    /** El servidor admite 100 pedidos y 512 kB por petición; con 50 hay margen en ambos límites. */
    public static final int TAMANO_MAXIMO = 50;

    private VentaSyncLote() {}

    public static List<List<VentaLocal>> trocear(List<VentaLocal> ventas, int tamano) {
        List<List<VentaLocal>> lotes = new ArrayList<>();
        for (int i = 0; i < ventas.size(); i += tamano) {
            lotes.add(new ArrayList<>(ventas.subList(i, Math.min(ventas.size(), i + tamano))));
        }
        return lotes;
    }

    public static JSONArray aJson(List<VentaLocal> lote) throws JSONException {
        JSONArray ventas = new JSONArray();
        for (VentaLocal v : lote) {
            JSONObject json = new JSONObject();
            json.put("localId", v.id);
            json.put("uuid", v.uuid);
            json.put("total", v.total);
            json.put("metodoPago", v.metodoPago != null ? v.metodoPago : "EFECTIVO");
            json.put("origen", "tpv");
            json.put("estado", "impreso");
            json.put("fecha", v.fecha);
            json.put("items", itemsOVacio(v.itemsJson));
            ventas.put(json);
        }
        return ventas;
    }

    private static JSONArray itemsOVacio(String itemsJson) {
        try {
            return new JSONArray(itemsJson);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static Resultado interpretar(int codigoHttp, String cuerpo) {
        if (codigoHttp < 200 || codigoHttp >= 300) {
            return Resultado.error("El servidor respondió HTTP " + codigoHttp, codigoHttp);
        }
        try {
            JSONObject json = new JSONObject(cuerpo);
            if (!json.optBoolean("success")) {
                return Resultado.error("El servidor no confirmó la subida", codigoHttp);
            }
            Set<Integer> confirmadas = new HashSet<>();
            JSONArray ids = json.optJSONArray("syncedIds");
            for (int i = 0; ids != null && i < ids.length(); i++) confirmadas.add(ids.getInt(i));

            List<Rechazo> rechazadas = new ArrayList<>();
            JSONArray rej = json.optJSONArray("rejected");
            for (int i = 0; rej != null && i < rej.length(); i++) {
                JSONObject r = rej.getJSONObject(i);
                rechazadas.add(new Rechazo(r.isNull("localId") ? -1 : r.optInt("localId", -1), r.optString("reason", "sin motivo")));
            }
            return new Resultado(confirmadas, rechazadas, null, codigoHttp);
        } catch (Exception e) {
            return Resultado.error("Respuesta del servidor no válida", codigoHttp);
        }
    }

    public static final class Rechazo {
        public final int localId;
        public final String motivo;

        Rechazo(int localId, String motivo) {
            this.localId = localId;
            this.motivo = motivo;
        }
    }

    public static final class Resultado {
        public final Set<Integer> confirmadas;
        public final List<Rechazo> rechazadas;
        /** null si la petición fue aceptada; si no, el motivo para registrar y avisar. */
        public final String error;
        /** Código HTTP de la respuesta (0 si no hubo respuesta interpretable). */
        public final int codigoHttp;

        Resultado(Set<Integer> confirmadas, List<Rechazo> rechazadas, String error, int codigoHttp) {
            this.confirmadas = confirmadas;
            this.rechazadas = rechazadas;
            this.error = error;
            this.codigoHttp = codigoHttp;
        }

        static Resultado error(String mensaje, int codigoHttp) {
            return new Resultado(Collections.emptySet(), Collections.emptyList(), mensaje, codigoHttp);
        }

        /** 401/403: la sesión ya no vale; reintentar solo no lo arregla, el usuario debe volver a entrar. */
        public boolean esFalloDeCredenciales() {
            return codigoHttp == 401 || codigoHttp == 403;
        }
    }
}
