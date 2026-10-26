package com.softmyc.tpv.data.printer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Constructor de dos parámetros de {@link PrinterHelper} (sin estilo explícito): imprime con la letra normal.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PrinterHelperDosParametrosTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final byte[] INICIALIZAR = {0x1B, 0x40};
    private static final byte[] AVANCE = "\n\n\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CORTAR = {0x1D, 0x56, 0x01};
    private static final long ESPERA_SEGUNDOS = 5;

    @Test
    public void sin_estilo_explicito_imprime_con_letra_normal() throws Exception {
        try (FakeImpresoraTcp impresora = new FakeImpresoraTcp()) {
            CountDownLatch terminado = new CountDownLatch(1);
            boolean[] resultado = new boolean[1];
            new PrinterHelper("127.0.0.1", impresora.puerto()).print(null, "Hola", (ok, error) -> {
                resultado[0] = ok;
                terminado.countDown();
            });

            assertTrue("la impresión debe terminar", terminado.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
            assertTrue(resultado[0]);
            byte[] esperado = concat(INICIALIZAR, new byte[]{0x1D, 0x21, 0x00},
                    "Hola".getBytes(StandardCharsets.ISO_8859_1), AVANCE, CORTAR);
            assertArrayEquals(esperado, impresora.bytesRecibidos());
        }
    }

    private static byte[] concat(byte[]... partes) {
        int total = 0;
        for (byte[] parte : partes) total += parte.length;
        byte[] salida = new byte[total];
        int pos = 0;
        for (byte[] parte : partes) {
            System.arraycopy(parte, 0, salida, pos, parte.length);
            pos += parte.length;
        }
        return salida;
    }
}
