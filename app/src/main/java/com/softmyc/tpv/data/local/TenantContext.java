package com.softmyc.tpv.data.local;

import android.content.SharedPreferences;

import java.util.regex.Pattern;

/**
 * Fuente única del negocio (tenant) activo. Todo el código que necesita el id de negocio (base local,
 * API, SyncGuard, sembrado de catálogo) pasa por aquí, de modo que un mismo negocio siempre tiene un único
 * id canónico y un único fichero de base de datos.
 *
 * <p>No hay negocio por defecto: si el id falta o no es válido, {@link #read} devuelve null y las operaciones
 * que dependen del negocio deben negarse con {@link NoActiveTenantException}.
 */
public final class TenantContext {

    /** Id del negocio de La Nube: conserva el fichero de base de datos de siempre. */
    public static final String LA_NUBE = "lanube";
    /** Mensaje de la operación denegada cuando no hay un negocio válido activo. */
    public static final String NO_TENANT_MESSAGE = "Sin negocio activo: vuelve a iniciar sesión";
    /** Clave de la sesión con el id canónico del negocio. */
    public static final String KEY_RESTAURANT_ID = "restaurant_id";
    /** Clave heredada que las pantallas siguen leyendo: "&lt;id&gt;_v2". Se mantiene sincronizada, no se lee primero. */
    public static final String KEY_CURRENT_TENANT = "current_tenant";

    private static final String PREFIX = "softmyc";
    private static final String VERSION_SUFFIX = "_v2";
    private static final String LEGACY_BOCATA_MARKER = "bocata";
    private static final String LEGACY_BOCATA_ID = "elbocata";
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{1,40}");
    private static final String LEGACY_DATABASE_FILE = "softmyc_database.db";

    private TenantContext() { }

    /** Se lanza cuando una operación necesita un negocio y no hay uno válido activo. */
    public static final class NoActiveTenantException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public NoActiveTenantException(String message) {
            super(message);
        }
    }

    /**
     * Normaliza un id de negocio: minúsculas, sin espacios, sin prefijo "softmyc" ni sufijo "_v2", con los alias
     * del negocio de El Bocata unificados. Devuelve null si falta o no es un identificador válido.
     */
    public static String canonicalId(String rawTenant) {
        if (rawTenant == null) return null;
        String clean = rawTenant.trim().toLowerCase().replace(PREFIX + "_", "").replace(PREFIX, "");
        if (clean.endsWith(VERSION_SUFFIX)) {
            clean = clean.substring(0, clean.length() - VERSION_SUFFIX.length());
        }
        if (clean.contains(LEGACY_BOCATA_MARKER)) return LEGACY_BOCATA_ID;
        return VALID_ID.matcher(clean).matches() ? clean : null;
    }

    /** Nombre del fichero SQLite de un negocio. Solo acepta ids ya canónicos (así no hay rutas inesperadas). */
    public static String databaseFileName(String canonicalId) {
        if (LA_NUBE.equals(canonicalId)) return LEGACY_DATABASE_FILE;
        return "softmyc_" + canonicalId + ".db";
    }

    /** Lee el negocio activo de las preferencias de sesión, o null si no hay uno válido. */
    public static String read(SharedPreferences prefs) {
        if (prefs == null) return null;
        String id = canonicalId(prefs.getString(KEY_RESTAURANT_ID, null));
        if (id == null) id = canonicalId(prefs.getString(KEY_CURRENT_TENANT, null));
        return id;
    }

    /** Indica si hay un negocio válido activo. */
    public static boolean hasActive(SharedPreferences prefs) {
        return read(prefs) != null;
    }

    /**
     * Guarda el negocio activo. Un id inválido borra el negocio: no se deja ningún valor por defecto.
     */
    public static void save(SharedPreferences prefs, String rawTenant) {
        String id = canonicalId(rawTenant);
        SharedPreferences.Editor editor = prefs.edit();
        if (id == null) {
            editor.remove(KEY_RESTAURANT_ID).remove(KEY_CURRENT_TENANT);
        } else {
            editor.putString(KEY_RESTAURANT_ID, id).putString(KEY_CURRENT_TENANT, id + VERSION_SUFFIX);
        }
        editor.apply();
    }
}
