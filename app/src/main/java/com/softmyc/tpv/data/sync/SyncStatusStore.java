package com.softmyc.tpv.data.sync;

import android.content.Context;
import android.content.SharedPreferences;

import com.softmyc.tpv.data.SecurePreferencesHelper;

/** Persiste el {@link SyncStatus} en las preferencias seguras (no depende del negocio activo, así sobrevive a cambios de negocio). */
public final class SyncStatusStore {

    private static final String CLAVE_OMISION = "sync_ultima_omision";
    private static final String CLAVE_RECHAZADAS = "sync_rechazadas_ultimo_intento";
    private static final String CLAVE_ULTIMO_EXITO = "sync_ultimo_exito_ms";

    private final SharedPreferences prefs;

    public SyncStatusStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public static SyncStatusStore of(Context context) {
        return new SyncStatusStore(SecurePreferencesHelper.getPrinterPrefs(context.getApplicationContext()));
    }

    public SyncStatus leer() {
        String omision = prefs.getString(CLAVE_OMISION, SyncOmision.NINGUNA.name());
        SyncOmision motivo;
        try {
            motivo = SyncOmision.valueOf(omision);
        } catch (IllegalArgumentException e) {
            motivo = SyncOmision.NINGUNA;
        }
        return new SyncStatus(motivo, prefs.getInt(CLAVE_RECHAZADAS, 0), prefs.getLong(CLAVE_ULTIMO_EXITO, 0L));
    }

    /** Empieza un intento: se borran el motivo de omisión y las rechazadas del intento anterior, pero no el último éxito. */
    public void iniciarIntento() {
        prefs.edit().putString(CLAVE_OMISION, SyncOmision.NINGUNA.name()).putInt(CLAVE_RECHAZADAS, 0).apply();
    }

    public void registrarOmision(SyncOmision motivo) {
        prefs.edit().putString(CLAVE_OMISION, motivo.name()).apply();
    }

    /** Un lote que el servidor ha aceptado (puede traer ventas rechazadas dentro). */
    public void registrarLoteAceptado(int rechazadas, long ahoraMs) {
        int acumuladas = prefs.getInt(CLAVE_RECHAZADAS, 0) + rechazadas;
        prefs.edit().putInt(CLAVE_RECHAZADAS, acumuladas).putLong(CLAVE_ULTIMO_EXITO, ahoraMs).apply();
    }
}
