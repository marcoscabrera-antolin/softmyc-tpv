package com.softmyc.tpv.data.repo;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.domain.port.AjustesImpresora;
import com.softmyc.tpv.domain.port.PrinterSettings;

/** Lee los ajustes de impresión de las preferencias cifradas; si fallan, usa los valores por defecto. */
public final class PrinterSettingsPrefs implements PrinterSettings {

    private static final String TAG = "PrinterSettingsPrefs";
    private static final String CLAVE_TAMANIO_LETRA = "tamanio_letra";
    private static final String CLAVE_PUERTO = "impresora_puerto";
    private static final int ESTILO_LETRA_POR_DEFECTO = 0;

    private final Context appContext;

    public PrinterSettingsPrefs(Context appContext) {
        this.appContext = appContext;
    }

    @Override
    public AjustesImpresora leer() {
        int fontStyle = ESTILO_LETRA_POR_DEFECTO;
        int puerto = AjustesImpresora.PUERTO_POR_DEFECTO;
        try {
            SharedPreferences sp = SecurePreferencesHelper.getPrinterPrefs(appContext);
            fontStyle = sp.getInt(CLAVE_TAMANIO_LETRA, ESTILO_LETRA_POR_DEFECTO);
            puerto = sp.getInt(CLAVE_PUERTO, AjustesImpresora.PUERTO_POR_DEFECTO);
        } catch (Exception e) {
            Log.w(TAG, "No se pudieron leer los ajustes de impresora; se usan los valores por defecto", e);
        }
        AjustesImpresora ajustes = AjustesImpresora.of(fontStyle, puerto);
        if (ajustes.puerto != puerto) {
            Log.w(TAG, "Puerto de impresora fuera de rango (" + puerto + "); se usa el " + ajustes.puerto);
        }
        return ajustes;
    }
}
