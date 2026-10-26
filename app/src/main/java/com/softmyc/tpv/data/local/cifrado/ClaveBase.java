package com.softmyc.tpv.data.local.cifrado;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.IntFunction;

/**
 * Clave de cifrado de las bases Room: 256 bits aleatorios generados una vez por instalación y guardados en un
 * almacén cifrado con el Keystore. No se registra en logs ni aparece en mensajes de error.
 */
public final class ClaveBase {

    public static final int BYTES_CLAVE = 32;
    private static final int LONGITUD_HEX = BYTES_CLAVE * 2;
    private static final char[] DIGITOS_HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Fuente de producción: bytes de un SecureRandom. */
    public static final IntFunction<byte[]> FUENTE_ALEATORIA = longitud -> {
        byte[] bytes = new byte[longitud];
        ALEATORIO.nextBytes(bytes);
        return bytes;
    };

    /** Almacén persistente de la clave (en producción, EncryptedSharedPreferences). */
    public interface Almacen {
        /** False si el almacén no persiste (Keystore degradado): no se crea ni se usa una clave en ese caso. */
        boolean disponible();

        /** Clave hexadecimal guardada, o null si todavía no existe. */
        String leer();

        boolean guardar(String valor);
    }

    private ClaveBase() {
    }

    /** Devuelve la clave hexadecimal guardada, o null si todavía no se ha creado. */
    public static String leer(Almacen almacen) {
        exigirDisponible(almacen);
        String valor = almacen.leer();
        if (valor != null && !esHexadecimal(valor, LONGITUD_HEX)) {
            throw new BaseCifradaException("La clave guardada de la base no tiene el formato esperado");
        }
        return valor;
    }

    /**
     * Genera, guarda y comprueba una clave nueva. Solo debe llamarse cuando no existe ninguna: nunca sobrescribe.
     */
    public static String crear(Almacen almacen, IntFunction<byte[]> fuente) {
        exigirDisponible(almacen);
        if (almacen.leer() != null) {
            throw new BaseCifradaException("Ya existe una clave de base: no se sobrescribe");
        }
        byte[] bytes = fuente.apply(BYTES_CLAVE);
        if (bytes == null || bytes.length != BYTES_CLAVE) {
            throw new BaseCifradaException("La fuente de aleatoriedad no entregó una clave de 256 bits");
        }
        String hex = aHexadecimal(bytes);
        Arrays.fill(bytes, (byte) 0);
        if (!almacen.guardar(hex) || !hex.equals(almacen.leer())) {
            throw new BaseCifradaException("No se pudo guardar la clave de la base de datos");
        }
        return hex;
    }

    /**
     * Clave en formato raw de SQLCipher ("x'...'"): evita la derivación PBKDF2 en cada apertura. El mismo texto
     * se usa al abrir con Room y en ATTACH ... KEY durante la migración.
     */
    public static String textoSqlCipher(String hex) {
        return "x'" + hex + "'";
    }

    /** Passphrase en bytes para {@code SupportOpenHelperFactory}. */
    public static byte[] passphrase(String hex) {
        return textoSqlCipher(hex).getBytes(StandardCharsets.US_ASCII);
    }

    private static void exigirDisponible(Almacen almacen) {
        if (!almacen.disponible()) {
            throw new BaseCifradaException("El almacén de la clave no persiste (Keystore no disponible): "
                    + "no se abre la base para no crear una clave que se pierda al cerrar la app");
        }
    }

    private static boolean esHexadecimal(String valor, int longitud) {
        if (valor.length() != longitud) {
            return false;
        }
        for (int i = 0; i < longitud; i++) {
            if (Character.digit(valor.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String aHexadecimal(byte[] bytes) {
        char[] salida = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int valor = bytes[i] & 0xFF;
            salida[i * 2] = DIGITOS_HEX[valor >>> 4];
            salida[i * 2 + 1] = DIGITOS_HEX[valor & 0x0F];
        }
        return new String(salida);
    }
}
