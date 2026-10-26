package com.softmyc.tpv.data.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Decide qué hace el botón de huella en la pantalla de login. Lógica pura (sin Android) para poder probarla en la JVM.
 *
 * Degradación segura: sin hardware biométrico fuerte, sin sesión utilizable o con la clave cambiada, el botón se oculta
 * y el acceso queda solo con usuario y contraseña.
 */
public final class BiometricUnlockPolicy {

    /** Texto que se cifra con la clave del Keystore: si el descifrado no lo devuelve, la huella no es válida. */
    private static final byte[] UNLOCK_MARKER = "softmyc-biometric-unlock-v1".getBytes(StandardCharsets.UTF_8);

    public enum Action {
        /** El botón no se muestra: no hay huella fuerte, no hay sesión o no hay nada que desbloquear. */
        HIDDEN,
        /** Hay sesión pero la huella aún no está ligada a la clave: se cifra el marcador tras autenticar. */
        ENROLL,
        /** La huella ya está ligada: se descifra el marcador con la clave y solo entonces se reabre la sesión. */
        UNLOCK
    }

    private BiometricUnlockPolicy() {
    }

    /** Copia del marcador que se cifra al registrar la huella. */
    public static byte[] unlockMarker() {
        return UNLOCK_MARKER.clone();
    }

    public static Action decide(boolean strongBiometricReady, boolean sessionUsable, boolean hasEnrollment) {
        if (!strongBiometricReady || !sessionUsable) return Action.HIDDEN;
        return hasEnrollment ? Action.UNLOCK : Action.ENROLL;
    }

    /** true solo si el marcador descifrado coincide exactamente con el esperado. */
    public static boolean isUnlockMarker(byte[] decrypted) {
        return decrypted != null && MessageDigest.isEqual(decrypted, UNLOCK_MARKER);
    }
}
