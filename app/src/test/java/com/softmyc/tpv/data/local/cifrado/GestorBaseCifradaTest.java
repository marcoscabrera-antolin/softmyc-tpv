package com.softmyc.tpv.data.local.cifrado;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.IntFunction;

public class GestorBaseCifradaTest {

    private static final String CLAVE = "ef".repeat(32);

    @Rule
    public TemporaryFolder carpeta = new TemporaryFolder();

    private AlmacenFalsoClave almacen;
    private OperacionesBaseFalsas operaciones;
    private byte[] passphraseRecibida;
    private File base;

    @Before
    public void preparar() {
        almacen = new AlmacenFalsoClave();
        operaciones = new OperacionesBaseFalsas();
        operaciones.claveAceptada = CLAVE;
        base = new File(carpeta.getRoot(), "softmyc_elbocata.db");
    }

    @Test
    public void primeraAperturaCreaLaClaveYUsaLaPassphraseRaw() {
        GestorBaseCifrada gestor = gestor();

        gestor.abrir(base);

        assertEquals(CLAVE, almacen.valor);
        assertNotNull(passphraseRecibida);
        assertArrayEquals(("x'" + CLAVE + "'").getBytes(StandardCharsets.US_ASCII), passphraseRecibida);
        assertEquals(0, operaciones.exportaciones);
    }

    @Test
    public void baseEnClaroSeMigraConUnaClaveNueva() throws Exception {
        Files.write(base.toPath(), cabeceraSqlite());
        operaciones.instantaneas.put(base.getAbsolutePath(), new Instantanea(6, Collections.singletonMap("ventas", 2L), true));
        GestorBaseCifrada gestor = gestor();

        gestor.abrir(base);

        assertEquals(CLAVE, almacen.valor);
        assertEquals(1, operaciones.exportaciones);
        assertArrayEquals(OperacionesBaseFalsas.MARCADOR_CIFRADO.getBytes(StandardCharsets.US_ASCII),
                Files.readAllBytes(base.toPath()));
    }

    @Test
    public void baseCifradaConClaveSeAbreSinMigrarNiCrearOtraClave() throws Exception {
        almacen.valor = CLAVE;
        Files.write(base.toPath(), OperacionesBaseFalsas.MARCADOR_CIFRADO.getBytes(StandardCharsets.US_ASCII));
        operaciones.instantaneas.put(base.getAbsolutePath(), new Instantanea(6, Collections.emptyMap(), true));
        GestorBaseCifrada gestor = gestorCon(longitud -> {
            throw new AssertionError("no debe generarse clave nueva");
        });

        gestor.abrir(base);

        assertEquals(0, operaciones.exportaciones);
        assertEquals(CLAVE, almacen.valor);
    }

    @Test
    public void baseCifradaSinClaveGuardadaNoGeneraUnaClaveNueva() throws Exception {
        Files.write(base.toPath(), OperacionesBaseFalsas.MARCADOR_CIFRADO.getBytes(StandardCharsets.US_ASCII));
        GestorBaseCifrada gestor = gestorCon(longitud -> {
            throw new AssertionError("no debe generarse clave nueva");
        });

        try {
            gestor.abrir(base);
            fail("Sin clave, la base cifrada no se abre ni se reemplaza");
        } catch (BaseCifradaException esperada) {
            assertNull(almacen.valor);
            assertFalse(esperada.getMessage().contains(CLAVE));
        }
    }

    @Test
    public void almacenNoPersistenteImpideAbrirCualquierBase() {
        almacen.disponible = false;
        GestorBaseCifrada gestor = gestor();

        try {
            gestor.abrir(base);
            fail("Con el Keystore degradado no se crea clave");
        } catch (BaseCifradaException esperada) {
            assertNull(almacen.valor);
        }
    }

    /** Gestor cuyo generador produce la clave de prueba (0xEF repetido = "ef" en hex). */
    private GestorBaseCifrada gestor() {
        return gestorCon(longitud -> bytesRepetidos((byte) 0xEF, longitud));
    }

    private GestorBaseCifrada gestorCon(IntFunction<byte[]> fuente) {
        return new GestorBaseCifrada(almacen, operaciones, fuente, passphrase -> {
            passphraseRecibida = passphrase;
            return new NoopFactory();
        });
    }

    private static byte[] bytesRepetidos(byte valor, int longitud) {
        byte[] bytes = new byte[longitud];
        Arrays.fill(bytes, valor);
        return bytes;
    }

    private static byte[] cabeceraSqlite() {
        byte[] bytes = new byte[512];
        System.arraycopy(EstadoBase.CABECERA_SQLITE, 0, bytes, 0, EstadoBase.CABECERA_SQLITE.length);
        return bytes;
    }

    /** Fábrica de prueba: no abre ninguna base, solo prueba la passphrase que recibe. */
    private static final class NoopFactory implements androidx.sqlite.db.SupportSQLiteOpenHelper.Factory {
        @Override
        public androidx.sqlite.db.SupportSQLiteOpenHelper create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration configuration) {
            throw new AssertionError("no debe abrirse en el test");
        }
    }

    private static final class AlmacenFalsoClave implements ClaveBase.Almacen {
        boolean disponible = true;
        String valor;

        @Override
        public boolean disponible() {
            return disponible;
        }

        @Override
        public String leer() {
            return valor;
        }

        @Override
        public boolean guardar(String nuevo) {
            valor = nuevo;
            return true;
        }
    }
}
