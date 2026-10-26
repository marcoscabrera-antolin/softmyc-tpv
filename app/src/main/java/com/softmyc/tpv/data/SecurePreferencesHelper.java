package com.softmyc.tpv.data;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Centralized helper for managing EncryptedSharedPreferences across the application.
 * Prevents duplicate code blocks and unifies preferences storage naming conventions.
 */
public class SecurePreferencesHelper {

    private static final String TAG = "SecurePrefsHelper";

    private static final String PREFS_SESSION = "SesionTPVEncrypted";
    private static final String PREFS_PRINTER = "PreferencesImpresoraEncrypted";
    private static final String PREFS_COUNTER = "PedidoCounterEncrypted";
    /** Almacén de la clave de cifrado de las bases Room (una clave por instalación). */
    public static final String NOMBRE_ALMACEN_CLAVE_BD = "ClaveBaseTPVEncrypted";

    /** Instancias en memoria usadas si el almacén cifrado falla: nunca se escribe en texto plano. */
    private static final java.util.Map<String, SharedPreferences> MEMORY_FALLBACK =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Ficheros que hoy funcionan con el almacén en memoria por un fallo del Keystore. */
    private static final java.util.Set<String> DEGRADED_FILES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Indica si algún almacén seguro está en modo degradado (sin Keystore): sus datos no sobreviven al proceso
     * y la app no debe asumir negocio, rol ni ajustes de impresora a partir de ellos.
     */
    public static boolean isDegraded() {
        return !DEGRADED_FILES.isEmpty();
    }

    /** Indica si el almacén de un fichero concreto está en modo degradado. Hay que pedir sus prefs antes. */
    public static boolean estaDegradado(String nombreAlmacen) {
        return DEGRADED_FILES.contains(nombreAlmacen);
    }

    /** Almacén de la clave de cifrado de las bases Room. */
    public static SharedPreferences getClaveBasePrefs(Context context) {
        return getSecurePrefs(context, NOMBRE_ALMACEN_CLAVE_BD);
    }

    /**
     * Retrieves the secure preferences for User Login Session.
     */
    public static SharedPreferences getSessionPrefs(Context context) {
        return getSecurePrefs(context, PREFS_SESSION);
    }

    /**
     * Retrieves the secure preferences for Printer and Layout settings.
     */
    public static SharedPreferences getPrinterPrefs(Context context) {
        return getSecurePrefs(context, PREFS_PRINTER);
    }

    /**
     * Retrieves the secure preferences for local Order ID counting/tracking.
     */
    public static SharedPreferences getCounterPrefs(Context context) {
        return getSecurePrefs(context, PREFS_COUNTER);
    }

    /** Crea el almacén cifrado de un fichero. Es una operación cara (unos 150 ms: clave maestra del Keystore y Tink). */
    interface Factory {
        SharedPreferences create(Context context, String name) throws Exception;
    }

    /** Instancia cifrada ya creada, asociada al contexto de aplicación con el que se creó. */
    private static final class Cached {
        final Context application;
        final SharedPreferences prefs;

        Cached(Context application, SharedPreferences prefs) {
            this.application = application;
            this.prefs = prefs;
        }
    }

    /** Una instancia por fichero: abrirla en cada llamada costaba ~150 ms, cuatro veces en cada cobro. */
    private static final java.util.Map<String, Cached> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Object CREATE_LOCK = new Object();
    private static volatile Factory factory = SecurePreferencesHelper::createEncrypted;

    private static SharedPreferences createEncrypted(Context context, String secureName) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        return EncryptedSharedPreferences.create(
                context,
                secureName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    /** Solo para tests: sustituye la creación cara (null restaura la real) y vacía las cachés. */
    static void setFactoryForTests(Factory replacement) {
        factory = replacement != null ? replacement : SecurePreferencesHelper::createEncrypted;
        CACHE.clear();
        MEMORY_FALLBACK.clear();
        DEGRADED_FILES.clear();
    }

    /**
     * Devuelve la instancia de EncryptedSharedPreferences de un fichero, creándola solo la primera vez.
     * Si el Keystore falla se usa un almacén en memoria: los secretos (token, clave de API) nunca se degradan
     * a texto plano en disco; simplemente no sobreviven al proceso. El fallo no se cachea, de modo que el
     * almacén cifrado se recupera en cuanto el Keystore vuelve a funcionar.
     */
    private static SharedPreferences getSecurePrefs(Context context, String secureName) {
        final Context application = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        Cached hit = CACHE.get(secureName);
        if (hit != null && hit.application == application) {
            return hit.prefs;
        }
        synchronized (CREATE_LOCK) {
            hit = CACHE.get(secureName);
            if (hit != null && hit.application == application) {
                return hit.prefs;
            }
            try {
                SharedPreferences created = factory.create(application, secureName);
                CACHE.put(secureName, new Cached(application, created));
                DEGRADED_FILES.remove(secureName);
                return created;
            } catch (Exception e) {
                DEGRADED_FILES.add(secureName);
                android.util.Log.e(TAG, "EncryptedSharedPreferences no disponible para " + secureName
                        + "; se usa almacenamiento en memoria (sin texto plano en disco). Los datos no se conservan al cerrar la app", e);
                return MEMORY_FALLBACK.computeIfAbsent(secureName, name -> new InMemorySharedPreferences());
            }
        }
    }
}
