package com.softmyc.tpv.data.local.cifrado;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class EstadoBaseTest {

    @Rule
    public TemporaryFolder carpeta = new TemporaryFolder();

    @Test
    public void ficheroInexistenteEsAusente() throws Exception {
        File fichero = new File(carpeta.getRoot(), "no_existe.db");

        assertEquals(EstadoBase.Estado.AUSENTE, EstadoBase.estadoDe(fichero));
    }

    @Test
    public void ficheroVacioEsAusente() throws Exception {
        File fichero = escribir("vacia.db", new byte[0]);

        assertEquals(EstadoBase.Estado.AUSENTE, EstadoBase.estadoDe(fichero));
    }

    @Test
    public void cabeceraSqliteIndicaBaseEnClaro() throws Exception {
        File fichero = escribir("en_claro.db", bytesConCabeceraSqlite(4096));

        assertEquals(EstadoBase.Estado.EN_CLARO, EstadoBase.estadoDe(fichero));
    }

    @Test
    public void contenidoSinCabeceraSqliteEsCifrado() throws Exception {
        byte[] aleatorio = new byte[4096];
        aleatorio[0] = 0x5A;
        File fichero = escribir("cifrada.db", aleatorio);

        assertEquals(EstadoBase.Estado.CIFRADO, EstadoBase.estadoDe(fichero));
    }

    @Test
    public void ficheroMasCortoQueLaCabeceraSeTrataComoCifrado() throws Exception {
        File fichero = escribir("corto.db", "SQLite".getBytes(StandardCharsets.US_ASCII));

        assertEquals(EstadoBase.Estado.CIFRADO, EstadoBase.estadoDe(fichero));
    }

    @Test
    public void cabeceraSqliteConOtroByteFinalEsCifrado() throws Exception {
        byte[] casiSqlite = bytesConCabeceraSqlite(64);
        casiSqlite[15] = 0x01;
        File fichero = escribir("casi.db", casiSqlite);

        assertEquals(EstadoBase.Estado.CIFRADO, EstadoBase.estadoDe(fichero));
    }

    private File escribir(String nombre, byte[] contenido) throws Exception {
        File fichero = new File(carpeta.getRoot(), nombre);
        Files.write(fichero.toPath(), contenido);
        return fichero;
    }

    private static byte[] bytesConCabeceraSqlite(int longitud) {
        byte[] bytes = new byte[longitud];
        byte[] cabecera = EstadoBase.CABECERA_SQLITE;
        System.arraycopy(cabecera, 0, bytes, 0, cabecera.length);
        return bytes;
    }
}
