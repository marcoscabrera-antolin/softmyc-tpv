package com.softmyc.tpv.data.local.cifrado;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Sustituto de SQLCipher para los tests JVM. Las instantáneas se fijan por ruta; "exportar" escribe un marcador
 * cifrado en el destino y copia la instantánea del origen, salvo que el test indique otra cosa.
 */
final class OperacionesBaseFalsas implements OperacionesBase {

    static final String MARCADOR_CIFRADO = "CONTENIDO-CIFRADO";

    final Map<String, Instantanea> instantaneas = new HashMap<>();
    final Map<String, Instantanea> exportacionesForzadas = new HashMap<>();
    String claveAceptada;
    int exportaciones;
    /** Si es true, la segunda lectura cifrada (la de la base ya sustituida) lanza error. */
    boolean fallarLecturaTrasSustitucion;
    int lecturasCifradas;

    @Override
    public Instantanea leerEnClaro(File fichero) {
        return instantaneas.get(fichero.getAbsolutePath());
    }

    @Override
    public Instantanea leerCifrada(File fichero, String claveHex) {
        lecturasCifradas++;
        if (!claveHex.equals(claveAceptada)) {
            throw new IllegalStateException("clave incorrecta");
        }
        if (fallarLecturaTrasSustitucion && lecturasCifradas >= 2) {
            throw new IllegalStateException("la base cifrada no abre");
        }
        return instantaneas.get(fichero.getAbsolutePath());
    }

    @Override
    public void exportarACifrada(File origen, File destino, String claveHex, int version) {
        exportaciones++;
        String ruta = destino.getAbsolutePath();
        Instantanea forzada = exportacionesForzadas.get(ruta);
        instantaneas.put(ruta, forzada != null ? forzada : instantaneas.get(origen.getAbsolutePath()));
        try {
            Files.write(destino.toPath(), MARCADOR_CIFRADO.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
