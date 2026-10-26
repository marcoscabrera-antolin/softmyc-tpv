package com.softmyc.tpv.data.printer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Envío a la impresora térmica por TCP contra una impresora falsa. Sin contexto no se imprime logo, así que el
 * protocolo esperado es: ESC @ (init), GS ! (tamaño), texto en ISO-8859-1, avance de líneas y corte GS V 1.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PrinterHelperTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final byte[] INICIALIZAR = {0x1B, 0x40};
    private static final byte[] AVANCE = "\n\n\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CORTAR = {0x1D, 0x56, 0x01};
    private static final long ESPERA_SEGUNDOS = 5;
    private static final int PUERTO_FUERA_DE_RANGO = 70000;

    private final Context context = ApplicationProvider.getApplicationContext();

    @Before
    public void prefsLimpias() {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().clear().commit();
    }

    @After
    public void limpiarPrefs() {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().clear().commit();
    }

    @Test
    public void envia_init_tamanio_texto_avance_y_corte_en_ese_orden() throws Exception {
        try (FakeImpresoraTcp impresora = new FakeImpresoraTcp()) {
            Resultado resultado = imprimir("127.0.0.1", impresora.puerto(), 0, "Hola");

            assertTrue(resultado.ok);
            assertNull(resultado.error);
            byte[] esperado = concat(INICIALIZAR, tamanio(0x00), "Hola".getBytes(StandardCharsets.ISO_8859_1), AVANCE, CORTAR);
            assertArrayEquals(esperado, impresora.bytesRecibidos());
        }
    }

    @Test
    public void texto_con_acentos_y_enie_se_envia_como_un_byte_iso_8859_1_por_caracter() throws Exception {
        try (FakeImpresoraTcp impresora = new FakeImpresoraTcp()) {
            imprimir("127.0.0.1", impresora.puerto(), 0, "Niño de Ávila");

            byte[] recibido = impresora.bytesRecibidos();
            byte[] texto = "Niño de Ávila".getBytes(StandardCharsets.ISO_8859_1);
            assertEquals(texto.length, "Niño de Ávila".length());
            assertTrue(contiene(recibido, texto));
            assertEquals((byte) 0xF1, texto[2]);
        }
    }

    @Test
    public void simbolo_euro_se_sustituye_por_EUR_y_no_queda_como_interrogacion() throws Exception {
        try (FakeImpresoraTcp impresora = new FakeImpresoraTcp()) {
            imprimir("127.0.0.1", impresora.puerto(), 0, "Total: 5,00€");

            byte[] recibido = impresora.bytesRecibidos();
            assertTrue(contiene(recibido, "Total: 5,00EUR".getBytes(StandardCharsets.US_ASCII)));
            assertFalse("el euro no debe llegar como '?'", contiene(recibido, "5,00?".getBytes(StandardCharsets.US_ASCII)));
        }
    }

    @Test
    public void caracter_fuera_de_iso_8859_1_se_envia_como_interrogacion() throws Exception {
        try (FakeImpresoraTcp impresora = new FakeImpresoraTcp()) {
            imprimir("127.0.0.1", impresora.puerto(), 0, "Ok ✓");

            byte[] recibido = impresora.bytesRecibidos();
            assertTrue(contiene(recibido, "Ok ?".getBytes(StandardCharsets.US_ASCII)));
        }
    }

    @Test
    public void contenido_nulo_solo_envia_comandos_de_inicio_y_corte() throws Exception {
        try (FakeImpresoraTcp impresora = new FakeImpresoraTcp()) {
            imprimir("127.0.0.1", impresora.puerto(), 0, null);

            assertArrayEquals(concat(INICIALIZAR, tamanio(0x00), AVANCE, CORTAR), impresora.bytesRecibidos());
        }
    }

    @Test
    public void estilo_de_letra_se_traduce_al_byte_de_GS_bang() throws Exception {
        assertEstilo(0, 0x00);
        assertEstilo(1, 0x01);
        assertEstilo(2, 0x11);
    }

    @Test
    public void estilo_de_letra_desconocido_usa_tamanio_normal() throws Exception {
        assertEstilo(7, 0x00);
        assertEstilo(-1, 0x00);
    }

    @Test
    public void conexion_correcta_notifica_exito_sin_error() throws Exception {
        try (FakeImpresoraTcp impresora = new FakeImpresoraTcp()) {
            Resultado resultado = imprimir("127.0.0.1", impresora.puerto(), 1, "x");

            assertTrue(resultado.ok);
            assertNull(resultado.error);
            impresora.bytesRecibidos();
        }
    }

    @Test
    public void puerto_cerrado_notifica_fallo_con_mensaje() throws Exception {
        int puertoCerrado = puertoLibre();

        Resultado resultado = imprimir("127.0.0.1", puertoCerrado, 0, "x");

        assertFalse(resultado.ok);
        assertNotNull(resultado.error);
    }

    @Test
    public void puerto_fuera_de_rango_notifica_fallo_sin_lanzar() throws Exception {
        Resultado resultado = imprimir("127.0.0.1", PUERTO_FUERA_DE_RANGO, 0, "x");

        assertFalse(resultado.ok);
        assertNotNull(resultado.error);
    }

    @Test
    public void ip_nula_notifica_fallo_sin_lanzar() throws Exception {
        Resultado resultado = imprimir(null, 9100, 0, "x");

        assertFalse(resultado.ok);
        assertNotNull(resultado.error);
    }

    @Test
    public void imprimirTicketCocina_usa_la_ip_puerto_y_tamanio_guardados_y_añade_cabecera() throws Exception {
        try (FakeImpresoraTcp impresora = new FakeImpresoraTcp()) {
            SecurePreferencesHelper.getPrinterPrefs(context).edit()
                    .putString("impresora_ip", "127.0.0.1")
                    .putInt("impresora_puerto", impresora.puerto())
                    .putInt("tamanio_letra", 2)
                    .commit();

            PrinterHelper.imprimirTicketCocina(context, "Pizza x2");

            // Con contexto se imprime antes el logo (ESC a 1 ... ESC a 0), así que solo se fija el arranque y el tamaño.
            byte[] recibido = impresora.bytesRecibidos();
            assertArrayEquals(INICIALIZAR, Arrays.copyOf(recibido, INICIALIZAR.length));
            assertTrue(contiene(recibido, tamanio(0x11)));
            assertTrue(contiene(recibido, "=== TICKET DE COCINA (WEB) ===".getBytes(StandardCharsets.US_ASCII)));
            assertTrue(contiene(recibido, "Pizza x2".getBytes(StandardCharsets.US_ASCII)));
        }
    }

    private void assertEstilo(int estilo, int byteEsperado) throws Exception {
        try (FakeImpresoraTcp impresora = new FakeImpresoraTcp()) {
            imprimir("127.0.0.1", impresora.puerto(), estilo, "t");

            byte[] recibido = impresora.bytesRecibidos();
            assertArrayEquals("estilo " + estilo, tamanio(byteEsperado), Arrays.copyOfRange(recibido, 2, 5));
        }
    }

    private static Resultado imprimir(String ip, int puerto, int estilo, String contenido) throws InterruptedException {
        Resultado resultado = new Resultado();
        CountDownLatch terminado = new CountDownLatch(1);
        new PrinterHelper(ip, puerto, estilo).print(null, contenido, (ok, error) -> {
            resultado.ok = ok;
            resultado.error = error;
            terminado.countDown();
        });
        assertTrue("la impresión no terminó", terminado.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
        return resultado;
    }

    private static int puertoLibre() throws IOException {
        try (ServerSocket temporal = new ServerSocket(0)) {
            return temporal.getLocalPort();
        }
    }

    private static byte[] tamanio(int valor) {
        return new byte[]{0x1D, 0x21, (byte) valor};
    }

    private static byte[] concat(byte[]... partes) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        for (byte[] parte : partes) {
            salida.write(parte, 0, parte.length);
        }
        return salida.toByteArray();
    }

    private static boolean contiene(byte[] datos, byte[] buscado) {
        outer:
        for (int i = 0; i + buscado.length <= datos.length; i++) {
            for (int j = 0; j < buscado.length; j++) {
                if (datos[i + j] != buscado[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static final class Resultado {
        boolean ok;
        String error;
    }
}
