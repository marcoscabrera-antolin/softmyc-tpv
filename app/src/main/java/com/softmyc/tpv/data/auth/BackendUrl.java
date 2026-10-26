package com.softmyc.tpv.data.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.softmyc.tpv.BuildConfig;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.network.OrderSyncService;

/** Dirección del servidor: la de Ajustes si hay una, o la de la compilación. */
public final class BackendUrl {

    private BackendUrl() { }

    /** Raíz del servidor (esquema, host y puerto), sin la ruta de pedidos ni barras finales. */
    public static String root(Context context) {
        SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(context);
        String url = prefs.getString("backend_base_url", null);
        if (url == null || url.trim().isEmpty()) url = BuildConfig.DEFAULT_BACKEND_URL;
        return rootOf(url);
    }

    public static String rootOf(String url) {
        String root = OrderSyncService.cleanUrl(url).replace("/api/tpv/orders", "").replace("/api/tpv", "").replace("/orders", "");
        while (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        if (root.endsWith("/api")) root = root.substring(0, root.length() - 4);
        return root;
    }
}
