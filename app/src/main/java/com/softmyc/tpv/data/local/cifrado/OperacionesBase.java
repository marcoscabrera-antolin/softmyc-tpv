package com.softmyc.tpv.data.local.cifrado;

import java.io.File;

/**
 * Operaciones sobre ficheros de base que dependen de SQLCipher (nativo). Separadas para que la lógica de
 * migración se pruebe en la JVM con una implementación falsa.
 */
public interface OperacionesBase {

    /** Lee una base en claro. Antes hace checkpoint del WAL para que el fichero principal esté completo. */
    Instantanea leerEnClaro(File fichero);

    Instantanea leerCifrada(File fichero, String claveHex);

    /** Exporta el contenido de una base en claro a un fichero nuevo cifrado, conservando user_version. */
    void exportarACifrada(File origen, File destino, String claveHex, int version);
}
