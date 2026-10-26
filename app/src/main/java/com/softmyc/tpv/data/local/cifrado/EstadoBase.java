package com.softmyc.tpv.data.local.cifrado;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Clasifica un fichero de base de datos por su cabecera. Una base SQLite en claro empieza por
 * "SQLite format 3" seguido de un byte nulo; cualquier otro contenido no vacío se trata como cifrado.
 */
public final class EstadoBase {

    public enum Estado { AUSENTE, EN_CLARO, CIFRADO }

    static final byte[] CABECERA_SQLITE = "SQLite format 3\u0000".getBytes(StandardCharsets.US_ASCII);

    private EstadoBase() {
    }

    /** Un fichero inexistente o vacío cuenta como ausente. */
    public static Estado estadoDe(File fichero) throws IOException {
        if (!fichero.isFile() || fichero.length() == 0) {
            return Estado.AUSENTE;
        }
        byte[] inicio = new byte[CABECERA_SQLITE.length];
        try (DataInputStream in = new DataInputStream(new FileInputStream(fichero))) {
            in.readFully(inicio);
        } catch (EOFException demasiadoCorto) {
            return Estado.CIFRADO;
        }
        return esCabeceraEnClaro(inicio) ? Estado.EN_CLARO : Estado.CIFRADO;
    }

    static boolean esCabeceraEnClaro(byte[] primerosBytes) {
        return primerosBytes != null
                && primerosBytes.length >= CABECERA_SQLITE.length
                && Arrays.equals(Arrays.copyOf(primerosBytes, CABECERA_SQLITE.length), CABECERA_SQLITE);
    }
}
