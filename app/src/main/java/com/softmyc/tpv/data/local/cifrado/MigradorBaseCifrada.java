package com.softmyc.tpv.data.local.cifrado;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Convierte una base en claro en una base cifrada sin perder datos.
 *
 * <p>Orden: exportar a un fichero temporal, verificar recuentos, copiar el original como respaldo, sustituir de
 * forma atómica y verificar la base cifrada. El respaldo en claro se borra solo después de esa verificación; si
 * algo falla antes de la sustitución, el original no se ha tocado, y si falla después se restaura.
 */
public final class MigradorBaseCifrada {

    static final String SUFIJO_TEMPORAL = ".cifrando";
    static final String SUFIJO_COPIA = ".claro.bak";

    private final OperacionesBase operaciones;

    public MigradorBaseCifrada(OperacionesBase operaciones) {
        this.operaciones = operaciones;
    }

    static File rutaTemporal(File principal) {
        return new File(principal.getParentFile(), principal.getName() + SUFIJO_TEMPORAL);
    }

    static File rutaCopia(File principal) {
        return new File(principal.getParentFile(), principal.getName() + SUFIJO_COPIA);
    }

    /** Migra una base en claro a cifrada. Lanza {@link BaseCifradaException} si algo no cuadra. */
    public void migrar(File principal, String claveHex) {
        File temporal = rutaTemporal(principal);
        File copia = rutaCopia(principal);
        borrarSiExiste(temporal);
        Instantanea antes = operaciones.leerEnClaro(principal);
        if (!antes.integra()) {
            throw new BaseCifradaException("La base en claro no supera integrity_check: no se migra");
        }
        borrarSiExiste(copia);
        operaciones.exportarACifrada(principal, temporal, claveHex, antes.version());
        Instantanea exportada = operaciones.leerCifrada(temporal, claveHex);
        if (!exportada.integra() || !exportada.equivalenteA(antes)) {
            borrarSiExiste(temporal);
            throw new BaseCifradaException("La copia cifrada no coincide con la original: se conserva la original");
        }
        copiar(principal, copia);
        sustituir(temporal, principal);
        verificarTrasSustitucion(principal, claveHex, antes);
        borrarSiExiste(copia);
    }

    /** Descarta restos de un intento anterior cuando la base todavía no existe. */
    public void descartarRestos(File principal) {
        borrarSiExiste(rutaTemporal(principal));
    }

    /**
     * La base ya está cifrada: limpia restos y, si quedó un respaldo en claro de una migración que no llegó a
     * borrarlo, lo elimina tras comprobar que la base cifrada abre y supera la integridad.
     */
    public void cerrarMigracionPendiente(File principal, String claveHex) {
        borrarSiExiste(rutaTemporal(principal));
        File copia = rutaCopia(principal);
        if (!copia.exists()) {
            return;
        }
        if (!operaciones.leerCifrada(principal, claveHex).integra()) {
            throw new BaseCifradaException("La base cifrada no supera integrity_check: se conserva la copia en claro");
        }
        borrarSiExiste(copia);
    }

    private void verificarTrasSustitucion(File principal, String claveHex, Instantanea antes) {
        File copia = rutaCopia(principal);
        try {
            Instantanea despues = operaciones.leerCifrada(principal, claveHex);
            if (despues.integra() && despues.equivalenteA(antes)) {
                return;
            }
        } catch (RuntimeException e) {
            restaurar(copia, principal);
            throw new BaseCifradaException("La base cifrada no pudo abrirse: se restauró la original en claro", e);
        }
        restaurar(copia, principal);
        throw new BaseCifradaException("La base cifrada no coincide con la original: se restauró la original en claro");
    }

    private static void restaurar(File copia, File principal) {
        sustituir(copia, principal);
    }

    private static void copiar(File origen, File destino) {
        try {
            Files.copy(origen.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(destino.toPath()) != Files.size(origen.toPath())) {
                throw new BaseCifradaException("La copia de seguridad tiene un tamaño distinto del original");
            }
            forzarEscritura(destino);
        } catch (IOException e) {
            throw new BaseCifradaException("No se pudo crear la copia de seguridad de la base", e);
        }
    }

    private static void sustituir(File origen, File destino) {
        try {
            forzarEscritura(origen);
            Files.move(origen.toPath(), destino.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BaseCifradaException("No se pudo sustituir la base por su versión cifrada", e);
        }
    }

    private static void borrarSiExiste(File fichero) {
        try {
            Files.deleteIfExists(fichero.toPath());
        } catch (IOException e) {
            throw new BaseCifradaException("No se pudo borrar un fichero temporal de la base", e);
        }
    }

    private static void forzarEscritura(File fichero) throws IOException {
        try (FileChannel canal = FileChannel.open(fichero.toPath(), StandardOpenOption.WRITE)) {
            canal.force(true);
        }
    }
}
