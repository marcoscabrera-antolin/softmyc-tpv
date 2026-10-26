package com.softmyc.tpv.data.local.cifrado;

import android.content.Context;
import android.content.SharedPreferences;

import com.softmyc.tpv.data.SecurePreferencesHelper;

/**
 * Almacén de la clave de base sobre EncryptedSharedPreferences (clave maestra en el Android Keystore).
 */
public final class AlmacenClaveSegura implements ClaveBase.Almacen {

    private static final String CLAVE_PREFERENCIA = "clave_bd_v1";

    private final SharedPreferences preferencias;
    private final boolean degradado;

    AlmacenClaveSegura(SharedPreferences preferencias, boolean degradado) {
        this.preferencias = preferencias;
        this.degradado = degradado;
    }

    public static AlmacenClaveSegura de(Context context) {
        SharedPreferences preferencias = SecurePreferencesHelper.getClaveBasePrefs(context);
        boolean degradado = SecurePreferencesHelper.estaDegradado(SecurePreferencesHelper.NOMBRE_ALMACEN_CLAVE_BD);
        return new AlmacenClaveSegura(preferencias, degradado);
    }

    @Override
    public boolean disponible() {
        return !degradado;
    }

    @Override
    public String leer() {
        return preferencias.getString(CLAVE_PREFERENCIA, null);
    }

    @Override
    public boolean guardar(String valor) {
        return preferencias.edit().putString(CLAVE_PREFERENCIA, valor).commit();
    }
}
