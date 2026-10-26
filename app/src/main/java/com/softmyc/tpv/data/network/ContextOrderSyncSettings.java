package com.softmyc.tpv.data.network;

import android.content.Context;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.local.TenantContext;

/** Lectura de los ajustes reales del dispositivo (preferencias seguras y sesión). */
final class ContextOrderSyncSettings implements OrderSyncSettings {
    private final Context appContext;

    ContextOrderSyncSettings(Context appContext) {
        this.appContext = appContext;
    }

    @Override
    public String backendBaseUrl() {
        return SecurePreferencesHelper.getPrinterPrefs(appContext).getString("backend_base_url", null);
    }

    @Override
    public String apiKey() {
        return SecurePreferencesHelper.getPrinterPrefs(appContext).getString("backend_api_key", null);
    }

    @Override
    public String activeTenant() {
        return TenantContext.read(SecurePreferencesHelper.getPrinterPrefs(appContext));
    }

    @Override
    public boolean hasUserSession() {
        AuthSession session = Auth.manager(appContext).current();
        return session != null && !session.demo;
    }
}
