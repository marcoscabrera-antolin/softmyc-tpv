package com.softmyc.tpv.data.auth;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import androidx.biometric.BiometricManager;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Clave AES del Android Keystore que solo se usa tras una autenticación biométrica fuerte (una vez por operación).
 * Un atacante que salte la pantalla con Frida no puede usarla: el Keystore exige el token de la huella en el TEE.
 * Si se registra una huella nueva, la clave queda invalidada y hay que volver a entrar con contraseña.
 */
public final class BiometricUnlockKeys {

    private static final String TAG = "BiometricUnlockKeys";
    static final String KEY_ALIAS = "softmyc_biometric_unlock";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int NO_TIMEOUT_SECONDS = -1;

    private BiometricUnlockKeys() {
    }

    /** true si el dispositivo tiene biometría fuerte enrolada y disponible ahora mismo. */
    public static boolean isStrongBiometricReady(Context context) {
        return BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /** Cifrado para el registro de la huella. Crea la clave si no existe. */
    public static Cipher newEnrollCipher() throws GeneralSecurityException {
        ensureKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, loadKey());
        return cipher;
    }

    /** Descifrado con el IV guardado. Lanza KeyPermanentlyInvalidatedException si la huella cambió. */
    public static Cipher newUnlockCipher(byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, loadKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher;
    }

    /** Borra la clave del Keystore (p. ej. tras una invalidación). */
    public static void delete() {
        try {
            KeyStore keyStore = keyStore();
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS);
        } catch (GeneralSecurityException e) {
            Log.w(TAG, "No se pudo borrar la clave biométrica", e);
        }
    }

    private static void ensureKey() throws GeneralSecurityException {
        if (keyStore().containsAlias(KEY_ALIAS)) return;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(buildSpec());
        generator.generateKey();
    }

    private static KeyGenParameterSpec buildSpec() {
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        } else {
            // Con CryptoObject, -1 exige una autenticación por cada operación (API 26-29).
            builder.setUserAuthenticationValidityDurationSeconds(NO_TIMEOUT_SECONDS);
        }
        return builder.build();
    }

    private static SecretKey loadKey() throws GeneralSecurityException {
        return (SecretKey) keyStore().getKey(KEY_ALIAS, null);
    }

    private static KeyStore keyStore() throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            return keyStore;
        } catch (IOException e) {
            throw new GeneralSecurityException("No se pudo abrir el Android Keystore", e);
        }
    }
}
