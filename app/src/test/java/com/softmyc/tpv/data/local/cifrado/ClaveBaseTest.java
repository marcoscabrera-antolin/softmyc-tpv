package com.softmyc.tpv.data.local.cifrado;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ClaveBaseTest {

    private static final String CLAVE_HEX = "ab".repeat(32);

    @Test
    public void crearGeneraClaveDe256BitsEnHexadecimalYLaGuarda() {
        // Arrange
        AlmacenFalso almacen = new AlmacenFalso();
        byte[] bytesFijos = new byte[ClaveBase.BYTES_CLAVE];
        for (int i = 0; i < bytesFijos.length; i++) {
            bytesFijos[i] = (byte) i;
        }

        // Act
        String clave = ClaveBase.crear(almacen, longitud -> bytesFijos.clone());

        // Assert
        assertEquals(64, clave.length());
        assertEquals("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f", clave);
        assertEquals(clave, almacen.valor);
    }

    @Test
    public void crearNoSobrescribeUnaClaveExistente() {
        AlmacenFalso almacen = new AlmacenFalso();
        almacen.valor = CLAVE_HEX;

        try {
            ClaveBase.crear(almacen, ClaveBaseTest::bytesFijos);
            fail("Debe rechazar sobrescribir la clave");
        } catch (BaseCifradaException esperada) {
            assertEquals(CLAVE_HEX, almacen.valor);
        }
    }

    @Test
    public void crearFallaSinGuardarNadaSiElAlmacenNoEsPersistente() {
        AlmacenFalso almacen = new AlmacenFalso();
        almacen.disponible = false;

        try {
            ClaveBase.crear(almacen, ClaveBaseTest::bytesFijos);
            fail("Sin almacén persistente no se crea clave");
        } catch (BaseCifradaException esperada) {
            assertNull(almacen.valor);
        }
    }

    @Test
    public void crearFallaSiElAlmacenNoConfirmaElGuardado() {
        AlmacenFalso almacen = new AlmacenFalso();
        almacen.guardarOk = false;

        try {
            ClaveBase.crear(almacen, ClaveBaseTest::bytesFijos);
            fail("Un guardado no confirmado debe fallar");
        } catch (BaseCifradaException esperada) {
            assertNull(almacen.valor);
        }
    }

    @Test
    public void leerDevuelveNullCuandoAunNoHayClave() {
        assertNull(ClaveBase.leer(new AlmacenFalso()));
    }

    @Test
    public void leerDevuelveLaClaveGuardada() {
        AlmacenFalso almacen = new AlmacenFalso();
        almacen.valor = CLAVE_HEX;

        assertEquals(CLAVE_HEX, ClaveBase.leer(almacen));
    }

    @Test
    public void leerRechazaUnValorConFormatoInvalidoSinRevelarlo() {
        AlmacenFalso almacen = new AlmacenFalso();
        almacen.valor = "no-es-hex";

        try {
            ClaveBase.leer(almacen);
            fail("Un valor mal formado no es una clave válida");
        } catch (BaseCifradaException esperada) {
            assertFalse(esperada.getMessage().contains("no-es-hex"));
        }
    }

    @Test
    public void leerFallaSiElAlmacenNoEsPersistente() {
        AlmacenFalso almacen = new AlmacenFalso();
        almacen.disponible = false;
        almacen.valor = CLAVE_HEX;

        try {
            ClaveBase.leer(almacen);
            fail("Un almacén degradado no se usa para leer la clave");
        } catch (BaseCifradaException esperada) {
            assertTrue(esperada.getMessage().contains("Keystore"));
        }
    }

    @Test
    public void passphraseUsaElFormatoRawDeSqlCipher() {
        byte[] passphrase = ClaveBase.passphrase(CLAVE_HEX);

        assertArrayEquals(("x'" + CLAVE_HEX + "'").getBytes(StandardCharsets.US_ASCII), passphrase);
    }

    @Test
    public void fuenteAleatoriaDevuelveBytesDistintosEnCadaLlamada() {
        byte[] primera = ClaveBase.FUENTE_ALEATORIA.apply(ClaveBase.BYTES_CLAVE);
        byte[] segunda = ClaveBase.FUENTE_ALEATORIA.apply(ClaveBase.BYTES_CLAVE);

        assertEquals(ClaveBase.BYTES_CLAVE, primera.length);
        assertNotEquals(new String(primera, StandardCharsets.ISO_8859_1), new String(segunda, StandardCharsets.ISO_8859_1));
    }

    private static byte[] bytesFijos(int longitud) {
        return new byte[longitud];
    }

    /** Almacén en memoria con los modos de fallo que hay que probar. */
    static final class AlmacenFalso implements ClaveBase.Almacen {
        boolean disponible = true;
        boolean guardarOk = true;
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
            if (!guardarOk) {
                return false;
            }
            valor = nuevo;
            return true;
        }
    }
}
