package com.softmyc.tpv.data.sync;

import android.content.Context;
import android.util.Log;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.SyncGuard;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.local.VentaLocal;
import com.softmyc.tpv.data.network.OrderSyncService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sube al servidor las ventas pendientes (isSynced = 0): en lotes, de uno en uno, sin solaparse, leyendo las
 * ventas rechazadas y sin marcar como subida una venta que se editó mientras se enviaba.
 */
public final class VentaUploader {
    private static final String TAG = "VentaUploader";
    /** Compartido: varias pantallas de pedido (cada una con su ViewModel) no deben subir las mismas ventas a la vez. */
    private static final AtomicBoolean EN_CURSO = new AtomicBoolean(false);
    private static final AtomicBoolean REPETIR = new AtomicBoolean(false);

    public interface Escucha {
        void ventasRechazadas(int cantidad);
    }

    private final Context context;
    private final Executor disco;
    private final Escucha escucha;
    private final SyncStatusStore estado;

    /** @param disco ejecutor de tareas de base de datos (nunca el hilo principal). */
    public VentaUploader(Context context, Executor disco, Escucha escucha) {
        this.context = context.getApplicationContext();
        this.disco = disco;
        this.escucha = escucha;
        this.estado = SyncStatusStore.of(this.context);
    }

    /** Ventas pendientes (isSynced = 0) del negocio activo. Bloqueante: no llamar desde el hilo principal. */
    public static int pendientes(Context context) {
        return AppDatabase.getDatabase(context.getApplicationContext()).ventaDao().getUnsynced().size();
    }

    /** @param alTerminar se llama (en cualquier hilo) cuando acaba el intento, haya subido algo o no; puede ser null. */
    public void subirPendientes(Runnable alTerminar) {
        disco.execute(() -> {
            try {
                estado.iniciarIntento();
                AppDatabase db = AppDatabase.getDatabase(context);
                List<VentaLocal> pendientes = db.ventaDao().getUnsynced();
                if (pendientes == null || pendientes.isEmpty()) {
                    fin(alTerminar);
                    return;
                }
                String tenant = TenantContext.read(SecurePreferencesHelper.getPrinterPrefs(context));
                if (tenant == null) {
                    Log.w(TAG, "Subida de ventas omitida: " + TenantContext.NO_TENANT_MESSAGE);
                    estado.registrarOmision(SyncOmision.SIN_NEGOCIO);
                    fin(alTerminar);
                    return;
                }
                if (!SyncGuard.canUploadFor(Auth.manager(context).current(), tenant)) {
                    Log.w(TAG, "Subida de ventas omitida: la sesión no corresponde al negocio local '" + tenant
                            + "' (" + pendientes.size() + " ventas pendientes)");
                    estado.registrarOmision(SyncOmision.SESION_OTRO_NEGOCIO);
                    fin(alTerminar);
                    return;
                }
                if (!EN_CURSO.compareAndSet(false, true)) {
                    // Hay una subida en marcha que no incluye estas ventas: se repite al terminar.
                    REPETIR.set(true);
                    fin(alTerminar);
                    return;
                }
                for (VentaLocal v : pendientes) {
                    if (v.uuid == null || v.uuid.isEmpty()) {
                        // Venta sin uuid (no debería ocurrir tras la migración): se le asigna y se guarda antes de enviarla.
                        v.uuid = UUID.randomUUID().toString();
                        db.ventaDao().update(v);
                    }
                }
                subirLotes(db, tenant, VentaSyncLote.trocear(pendientes, VentaSyncLote.TAMANO_MAXIMO), 0, alTerminar);
            } catch (Exception e) {
                Log.e(TAG, "Error preparando la subida de ventas", e);
                liberar(alTerminar);
            }
        });
    }

    private static void fin(Runnable alTerminar) {
        if (alTerminar != null) alTerminar.run();
    }

    /** Termina la subida actual y, si mientras tanto se pidió otra (p. ej. un cobro nuevo), la lanza ahora. */
    private void liberar(Runnable alTerminar) {
        EN_CURSO.set(false);
        fin(alTerminar);
        if (REPETIR.getAndSet(false)) subirPendientes(null);
    }

    /** Sube los lotes de uno en uno; un lote con error HTTP no impide intentar los siguientes. */
    private void subirLotes(AppDatabase db, String tenant, List<List<VentaLocal>> lotes, int indice, Runnable alTerminar) {
        if (indice >= lotes.size()) {
            liberar(alTerminar);
            return;
        }
        List<VentaLocal> lote = lotes.get(indice);
        org.json.JSONArray payload;
        try {
            payload = VentaSyncLote.aJson(lote);
        } catch (org.json.JSONException e) {
            Log.e(TAG, "No se pudo construir el lote de ventas", e);
            liberar(alTerminar);
            return;
        }
        new OrderSyncService(context).syncLocalOrders(tenant, payload, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                Log.w(TAG, "Subida de ventas fallida (se reintentará en la próxima sincronización): " + e.getMessage());
                estado.registrarOmision(SyncOmision.clasificar(e));
                liberar(alTerminar);
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                final VentaSyncLote.Resultado resultado;
                try (okhttp3.Response r = response) {
                    String cuerpo = r.body() != null ? r.body().string() : "";
                    resultado = VentaSyncLote.interpretar(r.code(), cuerpo);
                } catch (java.io.IOException | RuntimeException e) {
                    // OkHttp no llama a onFailure si onResponse lanza: sin esto la bandera quedaría activa para siempre.
                    Log.w(TAG, "No se pudo leer la respuesta de la subida: " + e.getMessage());
                    estado.registrarOmision(SyncOmision.SERVIDOR_ERROR);
                    liberar(alTerminar);
                    return;
                }
                try {
                    disco.execute(() -> {
                        try {
                            procesarResultado(db, lote, resultado);
                            subirLotes(db, tenant, lotes, indice + 1, alTerminar);
                        } catch (Exception e) {
                            Log.e(TAG, "Error registrando el resultado de la subida", e);
                            liberar(alTerminar);
                        }
                    });
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    // Ejecutor cerrado (p. ej. ViewModel destruido): sin esto la bandera quedaría activa para siempre.
                    Log.w(TAG, "No se pudo registrar el resultado de la subida: " + e.getMessage());
                    liberar(alTerminar);
                }
            }
        });
    }

    private void procesarResultado(AppDatabase db, List<VentaLocal> lote, VentaSyncLote.Resultado resultado) {
        if (resultado.error != null) {
            Log.w(TAG, "Subida de ventas rechazada: " + resultado.error);
            estado.registrarOmision(resultado.esFalloDeCredenciales() ? SyncOmision.SIN_CREDENCIALES : SyncOmision.SERVIDOR_ERROR);
            return;
        }
        for (VentaLocal v : lote) {
            if (resultado.confirmadas.contains(v.id)) {
                db.ventaDao().markAsSyncedSiNoCambio(v.id, v.total, v.itemsJson, v.metodoPago);
            }
        }
        estado.registrarLoteAceptado(resultado.rechazadas.size(), System.currentTimeMillis());
        if (!resultado.rechazadas.isEmpty()) {
            for (VentaSyncLote.Rechazo r : resultado.rechazadas) {
                Log.w(TAG, "Venta " + r.localId + " rechazada por el servidor: " + r.motivo);
            }
            if (escucha != null) escucha.ventasRechazadas(resultado.rechazadas.size());
        }
    }
}
