package com.softmyc.tpv.data.sync;

import android.content.Context;
import android.util.Log;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.SyncGuard;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.network.OrderSyncService;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Envía al servidor los cambios de catálogo pendientes de {@link CatalogOutbox}, de uno en uno y sin solaparse.
 *
 * Una operación sale de la cola con respuesta 2xx (o 404 al borrar), o si el servidor la rechaza de forma
 * definitiva (4xx salvo 401, 408 y 429: sin permiso o datos no válidos; reintentarla no serviría y
 * bloquearía la descarga del catálogo para siempre). Con red caída, 5xx, 401, 408 o 429 se queda en la cola y
 * el envío se detiene hasta el próximo intento.
 */
public final class CatalogOutboxSender {
    private static final String TAG = "CatalogOutboxSender";
    /** Un solo envío a la vez: dos recorridos simultáneos podrían reordenar un UPSERT y un DELETE. */
    private static final AtomicBoolean ENVIANDO = new AtomicBoolean(false);
    private static final AtomicBoolean REPETIR = new AtomicBoolean(false);

    public interface Resultado {
        /**
         * @param pendientes  operaciones que siguen en la cola tras el intento.
         * @param descartadas operaciones que el servidor rechazó de forma definitiva y se han retirado de la cola.
         */
        void alTerminar(int pendientes, int descartadas);
    }

    /** ¿Un código HTTP es un rechazo definitivo (no tiene sentido reintentar)? */
    static boolean esRechazoDefinitivo(int codigo) {
        return codigo >= 400 && codigo < 500 && codigo != 401 && codigo != 408 && codigo != 429;
    }

    private final Context context;
    private final String tenant;
    private final CatalogOutbox outbox;

    public CatalogOutboxSender(Context context, String tenant) {
        this.context = context.getApplicationContext();
        this.tenant = tenant;
        this.outbox = new CatalogOutbox(SecurePreferencesHelper.getPrinterPrefs(this.context));
    }

    public void enviar(Resultado resultado) {
        if (!ENVIANDO.compareAndSet(false, true)) {
            // Ya hay un envío en marcha con una lista anterior: se repite al terminar para incluir lo nuevo.
            REPETIR.set(true);
            resultado.alTerminar(outbox.pending(tenant).size(), 0);
            return;
        }
        if (!SyncGuard.canUploadFor(Auth.manager(context).current(), tenant)) {
            Log.w(TAG, "Envío de catálogo omitido: la sesión no corresponde al negocio local '" + tenant + "'");
            terminar(resultado, 0);
            return;
        }
        siguiente(outbox.pending(tenant), 0, 0, resultado);
    }

    private void terminar(Resultado resultado, int descartadas) {
        resultado.alTerminar(outbox.pending(tenant).size(), descartadas);
        ENVIANDO.set(false);
        if (REPETIR.getAndSet(false)) enviar((p, d) -> { });
    }

    private void siguiente(List<CatalogOutbox.Op> ops, int indice, int descartadas, Resultado resultado) {
        if (indice >= ops.size()) {
            terminar(resultado, descartadas);
            return;
        }
        CatalogOutbox.Op op = ops.get(indice);
        Callback alResponder = new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                Log.w(TAG, "Cambio de catálogo sin enviar (" + op.tipo + " " + op.codigo + "): " + e.getMessage());
                terminar(resultado, descartadas);
            }

            @Override
            public void onResponse(Call call, Response response) {
                int codigo;
                try (Response r = response) {
                    codigo = r.code();
                }
                boolean hecho = (codigo >= 200 && codigo < 300) || (CatalogOutbox.DELETE.equals(op.tipo) && codigo == 404);
                if (hecho) {
                    outbox.remove(tenant, op);
                    siguiente(ops, indice + 1, descartadas, resultado);
                } else if (esRechazoDefinitivo(codigo)) {
                    Log.w(TAG, "El servidor rechazó definitivamente el cambio de catálogo (" + op.tipo + " " + op.codigo
                            + "): HTTP " + codigo + "; se retira de la cola");
                    outbox.remove(tenant, op);
                    siguiente(ops, indice + 1, descartadas + 1, resultado);
                } else {
                    Log.w(TAG, "Cambio de catálogo sin enviar (" + op.tipo + " " + op.codigo + "): HTTP " + codigo);
                    terminar(resultado, descartadas);
                }
            }
        };

        OrderSyncService servicio = new OrderSyncService(context);
        if (CatalogOutbox.DELETE.equals(op.tipo)) {
            servicio.deleteProduct(op.codigo, alResponder);
            return;
        }
        // La base del negocio de la cola, no la del negocio activo en este momento.
        ProductoLocal producto = AppDatabase.getDatabaseForTenant(context, tenant).productoDao().getByCodigo(op.codigo);
        if (producto == null) {
            // Se creó y se borró antes de enviarlo: no hay nada que subir.
            outbox.remove(tenant, op);
            siguiente(ops, indice + 1, descartadas, resultado);
            return;
        }
        servicio.upsertProduct(producto, alResponder);
    }
}
