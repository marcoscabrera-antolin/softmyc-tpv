package com.softmyc.tpv.presentation.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

/**
 * Gráfico de ventas: dibujo con datos vacíos, de un solo punto y con tooltip, y selección táctil del punto más
 * cercano (con acotado en los extremos). La vista se mide a 800x400 para que los cálculos de posición sean fijos.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SalesChartViewTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final int ANCHO = 800;
    /** Mismos márgenes que usa la vista: izquierdo 110 y derecho 40. */
    private static final float PADDING_IZQ = 110f;
    private static final float PADDING_DER = 40f;

    private SalesChartView vista;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        vista = new SalesChartView(context);
        vista.layout(0, 0, ANCHO, 400);
    }

    @Test
    public void sin_datos_se_dibuja_el_aviso_y_los_toques_se_ignoran() {
        vista.setData(null, null);

        dibujar();

        assertEquals(-1, seleccionado());
        assertFalse(vista.onTouchEvent(evento(MotionEvent.ACTION_DOWN, 300f)));
    }

    @Test
    public void lista_vacia_no_lanza_al_dibujar_y_no_selecciona() {
        vista.setData(Collections.emptyList(), Collections.emptyList());

        dibujar();

        assertEquals(-1, seleccionado());
    }

    @Test
    public void un_solo_punto_se_dibuja_sin_fallar() {
        vista.setData(Collections.singletonList(12.5), Collections.singletonList("Lun"));

        dibujar();

        assertTrue(vista.onTouchEvent(evento(MotionEvent.ACTION_DOWN, 400f)));
        assertEquals(0, seleccionado());
    }

    @Test
    public void valores_todos_a_cero_usan_el_maximo_minimo_y_no_fallan() {
        vista.setData(Arrays.asList(0.0, 0.0, 0.0), Arrays.asList("a", "b", "c"));

        dibujar();

        assertEquals(-1, seleccionado());
    }

    @Test
    public void tocar_el_punto_central_selecciona_ese_indice() {
        vista.setData(Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0), Arrays.asList("1", "2", "3", "4", "5"));
        float xCentro = PADDING_IZQ + 2 * paso(5);

        vista.onTouchEvent(evento(MotionEvent.ACTION_DOWN, xCentro));
        dibujar();

        assertEquals(2, seleccionado());
    }

    @Test
    public void mover_el_dedo_actualiza_la_seleccion_al_punto_mas_cercano() {
        vista.setData(Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0), Arrays.asList("1", "2", "3", "4", "5"));

        vista.onTouchEvent(evento(MotionEvent.ACTION_DOWN, PADDING_IZQ));
        vista.onTouchEvent(evento(MotionEvent.ACTION_MOVE, PADDING_IZQ + 3 * paso(5)));

        assertEquals(3, seleccionado());
    }

    @Test
    public void tocar_fuera_por_la_izquierda_o_la_derecha_acota_a_los_extremos() {
        vista.setData(Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0), Arrays.asList("1", "2", "3", "4", "5"));

        vista.onTouchEvent(evento(MotionEvent.ACTION_DOWN, 0f));
        assertEquals(0, seleccionado());

        vista.onTouchEvent(evento(MotionEvent.ACTION_MOVE, 5000f));
        assertEquals(4, seleccionado());
    }

    @Test
    public void levantar_el_dedo_quita_la_seleccion() {
        vista.setData(Arrays.asList(1.0, 2.0, 3.0), Arrays.asList("a", "b", "c"));
        vista.onTouchEvent(evento(MotionEvent.ACTION_DOWN, 300f));

        assertTrue(vista.onTouchEvent(evento(MotionEvent.ACTION_UP, 300f)));

        assertEquals(-1, seleccionado());
    }

    @Test
    public void cancelar_el_gesto_tambien_quita_la_seleccion() {
        vista.setData(Arrays.asList(1.0, 2.0, 3.0), Arrays.asList("a", "b", "c"));
        vista.onTouchEvent(evento(MotionEvent.ACTION_DOWN, 300f));

        vista.onTouchEvent(evento(MotionEvent.ACTION_CANCEL, 300f));

        assertEquals(-1, seleccionado());
    }

    @Test
    public void setData_nuevo_limpia_la_seleccion_previa() {
        vista.setData(Arrays.asList(1.0, 2.0, 3.0), Arrays.asList("a", "b", "c"));
        vista.onTouchEvent(evento(MotionEvent.ACTION_DOWN, 300f));

        vista.setData(Arrays.asList(4.0, 5.0), Arrays.asList("x", "y"));

        assertEquals(-1, seleccionado());
    }

    @Test
    public void con_muchos_dias_solo_se_etiquetan_algunos_y_se_dibuja_el_tooltip_sin_fallar() {
        Double[] valores = new Double[30];
        String[] etiquetas = new String[30];
        for (int i = 0; i < 30; i++) {
            valores[i] = (double) (i * 3);
            etiquetas[i] = "d" + i;
        }
        vista.setData(Arrays.asList(valores), Arrays.asList(etiquetas));
        vista.onTouchEvent(evento(MotionEvent.ACTION_DOWN, PADDING_IZQ + 10 * paso(30)));

        dibujar();

        assertEquals(10, seleccionado());
    }

    private float paso(int puntos) {
        return (ANCHO - PADDING_IZQ - PADDING_DER) / (puntos - 1);
    }

    private void dibujar() {
        Bitmap bitmap = Bitmap.createBitmap(ANCHO, 400, Bitmap.Config.ARGB_8888);
        vista.draw(new Canvas(bitmap));
    }

    private int seleccionado() {
        try {
            Field campo = SalesChartView.class.getDeclaredField("selectedIndex");
            campo.setAccessible(true);
            return campo.getInt(vista);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("SalesChartView ya no tiene selectedIndex: actualiza el test", e);
        }
    }

    private static MotionEvent evento(int accion, float x) {
        return MotionEvent.obtain(0L, 0L, accion, x, 100f, 0);
    }
}
