package com.softmyc.tpv.data.auth;

import android.content.SharedPreferences;

import java.util.Base64;

/**
 * Único lugar donde se guardan los tokens de la sesión: las preferencias cifradas de sesión. El rol, el usuario y el
 * negocio también se publican con las claves que ya leen las pantallas (user_role, user_name, restaurant_id).
 */
public final class SessionStore {
    private static final String ACCESS = "auth.access";
    private static final String REFRESH = "auth.refresh";
    private static final String ACCESS_EXP = "auth.access_exp";
    private static final String REFRESH_EXP = "auth.refresh_exp";
    private static final String DEMO = "auth.demo";
    private static final String ISSUER = "auth.issuer";
    private static final String ROLE = "user_role";
    private static final String USER = "user_name";
    private static final String TENANT = "restaurant_id";
    private static final String BIO_IV = "auth.bio_iv";
    private static final String BIO_CT = "auth.bio_ct";

    private final SharedPreferences prefs;

    public SessionStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /** La sesión guardada, o null si no hay ninguna o está incompleta. */
    public synchronized AuthSession load() {
        String access = prefs.getString(ACCESS, null);
        String refresh = prefs.getString(REFRESH, null);
        if (access == null || refresh == null) return null;
        return new AuthSession(access, refresh, prefs.getLong(ACCESS_EXP, 0L), prefs.getLong(REFRESH_EXP, 0L),
                prefs.getString(USER, null), prefs.getString(ROLE, null), prefs.getString(TENANT, null), prefs.getBoolean(DEMO, false), prefs.getString(ISSUER, null));
    }

    /**
     * Guarda {@code s} solo si la sesión guardada sigue siendo la que tiene el acceso esperado. Evita que una renovación que
     * termina tarde reescriba tokens tras un cierre de sesión o una renovación hecha por otro hilo. Atómico.
     */
    public synchronized boolean saveIfCurrent(String expectedAccessToken, AuthSession s) {
        AuthSession current = load();
        if (current == null || !current.accessToken.equals(expectedAccessToken)) return false;
        save(s);
        return true;
    }

    public synchronized void save(AuthSession s) {
        prefs.edit()
                .putString(ISSUER, s.issuer)
                .putString(ACCESS, s.accessToken)
                .putString(REFRESH, s.refreshToken)
                .putLong(ACCESS_EXP, s.accessExpiresAtMs)
                .putLong(REFRESH_EXP, s.refreshExpiresAtMs)
                .putBoolean(DEMO, s.demo)
                .putString(ROLE, s.role)
                .putString(USER, s.username)
                .putString(TENANT, s.restaurantId)
                .commit();
    }

    /** Registro cifrado con la clave biométrica (IV y texto cifrado). Sin la clave del Keystore no sirve de nada. */
    public static final class BiometricEnrollment {
        public final byte[] iv;
        public final byte[] ciphertext;

        public BiometricEnrollment(byte[] iv, byte[] ciphertext) {
            this.iv = iv.clone();
            this.ciphertext = ciphertext.clone();
        }
    }

    public synchronized void saveBiometricEnrollment(BiometricEnrollment enrollment) {
        prefs.edit()
                .putString(BIO_IV, Base64.getEncoder().encodeToString(enrollment.iv))
                .putString(BIO_CT, Base64.getEncoder().encodeToString(enrollment.ciphertext))
                .commit();
    }

    /** El registro biométrico guardado, o null si no hay o está incompleto. */
    public synchronized BiometricEnrollment loadBiometricEnrollment() {
        String iv = prefs.getString(BIO_IV, null);
        String ciphertext = prefs.getString(BIO_CT, null);
        if (iv == null || ciphertext == null) return null;
        return new BiometricEnrollment(Base64.getDecoder().decode(iv), Base64.getDecoder().decode(ciphertext));
    }

    public synchronized void clearBiometricEnrollment() {
        prefs.edit().remove(BIO_IV).remove(BIO_CT).commit();
    }

    public synchronized void clear() {
        prefs.edit().remove(ACCESS).remove(REFRESH).remove(ACCESS_EXP).remove(REFRESH_EXP).remove(DEMO)
                .remove(ISSUER).remove(ROLE).remove(USER).remove(TENANT).remove("current_tenant").remove("hora_login").remove("auth_token")
                .remove(BIO_IV).remove(BIO_CT).commit();
    }
}
