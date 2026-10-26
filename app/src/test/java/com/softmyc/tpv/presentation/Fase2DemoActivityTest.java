package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Simulador multi-sector de la Fase 2: cambio de sector, ajuste de cantidades, modificadores y ticket en vivo.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Fase2DemoActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private ActivityController<Fase2DemoActivity> controlador;
    private Fase2DemoActivity actividad;

    @Before
    public void setUp() {
        android.content.Context app = ApplicationProvider.getApplicationContext();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        Auth.store(app).save(AuthSession.demo("lanube", "admin", "ana", System.currentTimeMillis()));
        controlador = Robolectric.buildActivity(Fase2DemoActivity.class).setup();
        actividad = controlador.get();
    }

    @After
    public void tearDown() {
        android.content.Context app = ApplicationProvider.getApplicationContext();
        controlador.pause().stop().destroy();
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
    }

    @Test
    public void al_arrancar_carga_el_taller_con_dos_lineas_y_su_ticket() {
        assertTrue(texto(R.id.tvStrategyBadge).contains("TimePricingStrategy"));
        assertEquals(3, items().getChildCount());
        assertTrue(texto(R.id.tvThermalReceipt).contains("TALLERES MEC"));
        assertTrue(texto(R.id.tvLiveTotal).endsWith("€"));
    }

    @Test
    public void cada_pestana_carga_su_sector_con_estrategia_y_comercio() {
        pulsar(R.id.tabFruteria);
        assertTrue(texto(R.id.tvStrategyBadge).contains("WeightPricingStrategy"));
        assertTrue(texto(R.id.tvThermalReceipt).contains("HUERTA"));

        pulsar(R.id.tabRestaurante);
        assertTrue(texto(R.id.tvStrategyBadge).contains("Multi-IVA"));
        assertTrue(texto(R.id.tvThermalReceipt).contains("BURGER"));

        pulsar(R.id.tabMayorista);
        assertTrue(texto(R.id.tvStrategyBadge).contains("TieredVolume"));
        assertTrue(texto(R.id.tvThermalReceipt).contains("CASH"));

        pulsar(R.id.tabTaller);
        assertEquals(3, items().getChildCount());
    }

    @Test
    public void sumar_una_unidad_en_un_articulo_sin_decimales_sube_el_total() {
        String antes = texto(R.id.tvLiveTotal);

        pulsar(tarjeta(1), R.id.btnPlus);

        assertNotEquals(antes, texto(R.id.tvLiveTotal));
        assertTrue(texto(tarjeta(1), R.id.tvQuantity).startsWith("3"));
    }

    @Test
    public void restar_en_un_articulo_baja_la_cantidad_y_no_baja_de_cero() {
        pulsar(tarjeta(1), R.id.btnMinus);
        pulsar(tarjeta(1), R.id.btnMinus);
        assertTrue(texto(tarjeta(1), R.id.tvQuantity).startsWith("0"));

        pulsar(tarjeta(1), R.id.btnMinus);

        assertTrue(texto(tarjeta(1), R.id.tvQuantity).startsWith("0"));
    }

    @Test
    public void un_articulo_con_cantidad_cero_se_incorpora_al_ticket_al_sumarlo() {
        String antes = texto(R.id.tvThermalReceipt);

        pulsar(tarjeta(2), R.id.btnPlus);

        assertNotEquals(antes, texto(R.id.tvThermalReceipt));
        assertTrue(texto(R.id.tvThermalReceipt).contains("Discos"));
    }

    @Test
    public void los_articulos_por_horas_muestran_pasos_rapidos_y_suman_decimales() {
        View manoDeObra = tarjeta(0);
        assertEquals(View.VISIBLE, manoDeObra.findViewById(R.id.quickStepsContainer).getVisibility());
        assertEquals(View.GONE, tarjeta(1).findViewById(R.id.quickStepsContainer).getVisibility());
        String inicial = texto(manoDeObra, R.id.tvQuantity);

        pulsar(manoDeObra, R.id.btnStepSmall);
        String trasSmall = texto(manoDeObra, R.id.tvQuantity);
        pulsar(manoDeObra, R.id.btnStepMedium);
        String trasMedium = texto(manoDeObra, R.id.tvQuantity);
        pulsar(manoDeObra, R.id.btnStepLarge);
        String trasLarge = texto(manoDeObra, R.id.tvQuantity);

        assertNotEquals(inicial, trasSmall);
        assertNotEquals(trasSmall, trasMedium);
        assertNotEquals(trasMedium, trasLarge);
        pulsar(manoDeObra, R.id.btnPlus);
        pulsar(manoDeObra, R.id.btnMinus);
        assertEquals(trasLarge, texto(manoDeObra, R.id.tvQuantity));
    }

    @Test
    public void marcar_y_desmarcar_un_modificador_cambia_y_restaura_el_total() {
        String base = texto(R.id.tvLiveTotal);
        Chip urgencia = (Chip) ((ChipGroup) tarjeta(0).findViewById(R.id.chipGroupModifiers)).getChildAt(0);

        urgencia.setChecked(true);
        String conUrgencia = texto(R.id.tvLiveTotal);
        urgencia.setChecked(false);

        assertNotEquals(base, conUrgencia);
        assertEquals(base, texto(R.id.tvLiveTotal));
    }

    @Test
    public void cambiar_de_sector_reinicia_los_modificadores_marcados() {
        pulsar(R.id.tabRestaurante);
        String base = texto(R.id.tvLiveTotal);
        Chip bacon = (Chip) ((ChipGroup) tarjeta(0).findViewById(R.id.chipGroupModifiers)).getChildAt(0);
        bacon.setChecked(true);
        assertNotEquals(base, texto(R.id.tvLiveTotal));

        pulsar(R.id.btnResetDemo);

        assertEquals(base, texto(R.id.tvLiveTotal));
        Chip nuevo = (Chip) ((ChipGroup) tarjeta(0).findViewById(R.id.chipGroupModifiers)).getChildAt(0);
        assertFalse(nuevo.isChecked());
    }

    @Test
    public void el_desglose_de_impuestos_del_restaurante_separa_iva_reducido_y_general() {
        pulsar(R.id.tabRestaurante);

        LinearLayout desglose = actividad.findViewById(R.id.taxBreakdownContainer);

        assertEquals(2, desglose.getChildCount());
    }

    @Test
    public void en_el_mayorista_el_precio_por_tramos_aplica_la_cantidad_inicial() {
        pulsar(R.id.tabMayorista);

        assertEquals(2, items().getChildCount());
        assertTrue(texto(R.id.tvThermalReceipt).contains("Bater"));
    }

    @Test
    public void volver_cierra_la_actividad() {
        pulsar(R.id.btnBack);

        assertTrue(actividad.isFinishing());
    }

    // ---------------------------------------------------------------------------------------------------------

    private LinearLayout items() {
        return actividad.findViewById(R.id.itemsContainer);
    }

    private View tarjeta(int indice) {
        return items().getChildAt(indice);
    }

    private String texto(int id) {
        return ((TextView) actividad.findViewById(id)).getText().toString();
    }

    private static String texto(View padre, int id) {
        return ((TextView) padre.findViewById(id)).getText().toString();
    }

    private void pulsar(int id) {
        actividad.findViewById(id).performClick();
        ShadowLooper.idleMainLooper();
    }

    private static void pulsar(View padre, int id) {
        padre.findViewById(id).performClick();
        ShadowLooper.idleMainLooper();
    }
}
