package com.softmyc.tpv.data.sync;

import android.content.Context;
import android.util.Log;

import com.softmyc.tpv.domain.repository.CatalogoTenantRepository;
import com.softmyc.tpv.domain.repository.ExtraCatalogo;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;
import com.softmyc.tpv.data.network.CatalogoRemotoParser;
import com.softmyc.tpv.data.network.OrderSyncService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Descarga el catálogo del servidor y lo aplica en Room. Modo offline silencioso: si falla, la app sigue con los
 * datos locales.
 */
public final class CatalogoRemotoDescarga {
    private static final String TAG = "CatalogoRemotoDescarga";

    private final CatalogoTenantRepository catalogo;
    private final ExecutorService executor;
    private final Runnable alAplicar;

    /**
     * @param alAplicar se ejecuta tras aplicar un catálogo nuevo, para recargar los datos locales en pantalla
     */
    public CatalogoRemotoDescarga(CatalogoTenantRepository catalogo, ExecutorService executor, Runnable alAplicar) {
        this.catalogo = catalogo;
        this.executor = executor;
        this.alAplicar = alAplicar;
    }

    public void descargar(Context context, String tenant) {
        OrderSyncService syncService = new OrderSyncService(context);
        syncService.fetchProducts(tenant, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Modo offline silencioso: la app continúa trabajando con los datos locales de Room
            }

            @Override
            public void onResponse(Call call, Response response) {
                procesarRespuesta(response, tenant);
            }
        });
    }

    private void procesarRespuesta(Response response, String tenant) {
        if (!response.isSuccessful() || response.body() == null) return;
        try {
            JSONObject obj = new JSONObject(response.body().string());
            if (!obj.optBoolean("success")) return;
            JSONArray prodsArr = obj.optJSONArray("products");
            if (prodsArr == null || prodsArr.length() == 0) return;
            JSONArray extrasArr = obj.optJSONArray("extras");
            JSONArray salsasArr = obj.optJSONArray("salsas");
            executor.execute(() -> aplicar(tenant, prodsArr, extrasArr, salsasArr));
        } catch (Exception ignored) {
            // Respuesta no válida: se mantiene el catálogo local
        }
    }

    private void aplicar(String tenant, JSONArray prodsArr, JSONArray extrasArr, JSONArray salsasArr) {
        try {
            List<ProductoCatalogo> newProds = CatalogoRemotoParser.productos(prodsArr);
            List<ExtraCatalogo> newExtras = CatalogoRemotoParser.extras(extrasArr);
            List<String> newSalsas = CatalogoRemotoParser.salsas(salsasArr);
            if (catalogo.aplicarDescargaServidor(tenant, newProds, newExtras, newSalsas)) {
                alAplicar.run();
            }
        } catch (Exception e) {
            Log.w(TAG, "No se pudo aplicar el catálogo descargado del servidor", e);
        }
    }
}
