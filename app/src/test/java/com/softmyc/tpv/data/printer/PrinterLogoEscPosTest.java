package com.softmyc.tpv.data.printer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;

/**
 * Logo para la impresora térmica: cabecera GS v 0 con el tamaño correcto, y la conversión de píxeles a bits según
 * sea el fondo (transparente, claro u oscuro). Un fondo transparente o blanco no debe imprimir nada.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PrinterLogoEscPosTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final int ANCHO = 200;
    private static final int ALTO = 185;
    private static final int BYTES_POR_FILA = ANCHO / 8;
    private static final int CABECERA = 8;

    @Test
    public void el_logo_del_ticket_lleva_la_cabecera_de_imagen_raster_con_su_tamano() {
        byte[] logo = PrinterHelper.getLogoEscPosBytes(ApplicationProvider.getApplicationContext());

        assertEquals(CABECERA + BYTES_POR_FILA * ALTO, logo.length);
        assertEquals(0x1D, logo[0] & 0xFF);
        assertEquals(0x76, logo[1] & 0xFF);
        assertEquals(0x30, logo[2] & 0xFF);
        assertEquals(BYTES_POR_FILA, logo[4] & 0xFF);
        assertEquals(ALTO, logo[6] & 0xFF);
    }

    @Test
    public void un_bitmap_transparente_no_imprime_ningun_punto() throws Exception {
        Bitmap bitmap = relleno(Color.TRANSPARENT);

        assertTodoCero(convertir(bitmap));
    }

    @Test
    public void un_bitmap_blanco_opaco_no_imprime_ningun_punto() throws Exception {
        Bitmap bitmap = relleno(Color.WHITE);

        assertTodoCero(convertir(bitmap));
    }

    @Test
    public void un_trazo_naranja_sobre_fondo_claro_imprime_puntos() throws Exception {
        Bitmap bitmap = relleno(Color.WHITE);
        bitmap.setPixel(100, 90, Color.rgb(255, 128, 0));

        byte[] salida = convertir(bitmap);

        assertTrue("el trazo oscuro debe imprimirse", hayBitsEncendidos(salida));
    }

    @Test
    public void sobre_fondo_oscuro_solo_se_imprime_el_cromatismo_naranja() throws Exception {
        Bitmap soloFondo = relleno(Color.BLACK);
        Bitmap conNaranja = relleno(Color.BLACK);
        conNaranja.setPixel(100, 90, Color.rgb(255, 128, 0));
        conNaranja.setPixel(101, 90, Color.rgb(200, 200, 200));

        assertTodoCero(convertir(soloFondo));
        assertTrue("el naranja sobre fondo oscuro debe imprimirse", hayBitsEncendidos(convertir(conNaranja)));
    }

    private static Bitmap relleno(int color) {
        Bitmap bitmap = Bitmap.createBitmap(ANCHO, ALTO, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    /** Datos de imagen tras la cabecera GS v 0 (los bytes de píxeles). */
    private static byte[] convertir(Bitmap bitmap) throws Exception {
        Method metodo = PrinterHelper.class.getDeclaredMethod("convertBitmapToEscPosBytes", Bitmap.class);
        metodo.setAccessible(true);
        byte[] completo = (byte[]) metodo.invoke(null, bitmap);
        byte[] pixeles = new byte[completo.length - CABECERA];
        System.arraycopy(completo, CABECERA, pixeles, 0, pixeles.length);
        return pixeles;
    }

    private static void assertTodoCero(byte[] datos) {
        assertTrue("ningún punto debe imprimirse", !hayBitsEncendidos(datos));
    }

    private static boolean hayBitsEncendidos(byte[] datos) {
        for (byte b : datos) {
            if (b != 0) return true;
        }
        return false;
    }
}
