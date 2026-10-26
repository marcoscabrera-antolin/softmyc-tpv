package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Chip de selección ligero: estado marcado, aviso al listener solo en cambios reales (sin bucles), texto vacío
 * o nulo y accesibilidad como casilla marcable.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LightChipViewTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final int FONDO_ACTIVO = 0xFF0EA5E9;
    private static final int BORDE_ACTIVO = 0xFF38BDF8;
    private static final int TEXTO_ACTIVO = 0xFFFFFFFF;

    private Context context;
    private LightChipView chip;
    private List<Boolean> notificaciones;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        chip = new LightChipView(context, "Bebidas", FONDO_ACTIVO, BORDE_ACTIVO, TEXTO_ACTIVO);
        notificaciones = new ArrayList<>();
        chip.setOnCheckedChangeListener((vista, marcado) -> notificaciones.add(marcado));
    }

    @Test
    public void nace_sin_marcar_y_muestra_el_texto_recibido() {
        assertFalse(chip.isChecked());
        assertEquals("Bebidas", chip.getText().toString());
    }

    @Test
    public void texto_nulo_se_convierte_en_cadena_vacia() {
        LightChipView vacio = new LightChipView(context, null, FONDO_ACTIVO, BORDE_ACTIVO, TEXTO_ACTIVO);

        assertEquals("", vacio.getText().toString());
    }

    @Test
    public void setChecked_notifica_una_sola_vez_por_cambio_real() {
        chip.setChecked(true);
        chip.setChecked(true);
        chip.setChecked(false);

        assertEquals(List.of(true, false), notificaciones);
        assertFalse(chip.isChecked());
    }

    @Test
    public void pulsar_el_chip_alterna_el_estado_y_notifica() {
        chip.performClick();
        assertTrue(chip.isChecked());

        chip.performClick();
        assertFalse(chip.isChecked());

        assertEquals(List.of(true, false), notificaciones);
    }

    @Test
    public void toggle_invierte_el_estado() {
        chip.toggle();

        assertTrue(chip.isChecked());
        assertEquals(List.of(true), notificaciones);
    }

    @Test
    public void sin_listener_cambiar_el_estado_no_falla() {
        LightChipView sinOyente = new LightChipView(context, "Postres", FONDO_ACTIVO, BORDE_ACTIVO, TEXTO_ACTIVO);

        sinOyente.setChecked(true);

        assertTrue(sinOyente.isChecked());
    }

    @Test
    public void un_listener_que_vuelve_a_cambiar_el_chip_no_entra_en_bucle() {
        LightChipView reentrante = new LightChipView(context, "Tarta", FONDO_ACTIVO, BORDE_ACTIVO, TEXTO_ACTIVO);
        List<Boolean> llamadas = new ArrayList<>();
        reentrante.setOnCheckedChangeListener((vista, marcado) -> {
            llamadas.add(marcado);
            vista.setChecked(!marcado);
        });

        reentrante.setChecked(true);

        assertEquals(List.of(true), llamadas);
        assertFalse("el cambio reentrante se aplica sin volver a notificar", reentrante.isChecked());
    }

    @Test
    public void se_anuncia_como_casilla_marcable_con_su_estado() {
        chip.setChecked(true);
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();

        chip.onInitializeAccessibilityNodeInfo(info);

        assertTrue(info.isCheckable());
        assertTrue(info.isChecked());
        assertEquals(android.widget.CheckBox.class.getName(), chip.getAccessibilityClassName());
    }
}
