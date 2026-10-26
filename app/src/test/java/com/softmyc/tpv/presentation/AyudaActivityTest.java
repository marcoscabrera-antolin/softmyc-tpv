package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.Button;

import androidx.test.core.app.ApplicationProvider;
import androidx.viewpager2.widget.ViewPager2;

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
 * Asistente de ayuda: navegación entre diapositivas con los botones inferiores, texto del último paso y cierre.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AyudaActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final int DIAPOSITIVAS = 6;

    private ActivityController<AyudaActivity> controlador;

    @Before
    public void setUp() {
        android.content.Context app = ApplicationProvider.getApplicationContext();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        Auth.store(app).save(AuthSession.demo("lanube", "admin", "ana", System.currentTimeMillis()));
        controlador = Robolectric.buildActivity(AyudaActivity.class).setup();
    }

    @After
    public void tearDown() {
        android.content.Context app = ApplicationProvider.getApplicationContext();
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
    }

    @Test
    public void la_primera_diapositiva_no_muestra_atras_y_el_boton_dice_siguiente() {
        assertEquals(DIAPOSITIVAS, pager().getAdapter().getItemCount());
        assertEquals(View.INVISIBLE, boton(R.id.btnHelpAtras).getVisibility());
        assertEquals("Siguiente", boton(R.id.btnHelpSiguiente).getText().toString());
    }

    @Test
    public void siguiente_avanza_una_diapositiva_y_muestra_atras() {
        boton(R.id.btnHelpSiguiente).performClick();
        ShadowLooper.idleMainLooper();

        assertEquals(1, pager().getCurrentItem());
        assertEquals(View.VISIBLE, boton(R.id.btnHelpAtras).getVisibility());
    }

    @Test
    public void atras_retrocede_y_no_pasa_de_la_primera_diapositiva() {
        boton(R.id.btnHelpSiguiente).performClick();
        ShadowLooper.idleMainLooper();

        boton(R.id.btnHelpAtras).performClick();
        ShadowLooper.idleMainLooper();
        boton(R.id.btnHelpAtras).performClick();
        ShadowLooper.idleMainLooper();

        assertEquals(0, pager().getCurrentItem());
        assertEquals(View.INVISIBLE, boton(R.id.btnHelpAtras).getVisibility());
    }

    @Test
    public void en_la_ultima_diapositiva_el_boton_dice_entendido_y_cierra_la_ayuda() {
        for (int i = 0; i < DIAPOSITIVAS - 1; i++) {
            boton(R.id.btnHelpSiguiente).performClick();
            ShadowLooper.idleMainLooper();
        }
        assertEquals(DIAPOSITIVAS - 1, pager().getCurrentItem());
        assertEquals("Entendido", boton(R.id.btnHelpSiguiente).getText().toString());

        boton(R.id.btnHelpSiguiente).performClick();

        assertTrue(controlador.get().isFinishing());
    }

    @Test
    public void el_boton_cerrar_termina_la_pantalla_desde_cualquier_diapositiva() {
        pager().setCurrentItem(2, false);
        ShadowLooper.idleMainLooper();

        controlador.get().findViewById(R.id.btnCerrarAyuda).performClick();

        assertTrue(controlador.get().isFinishing());
    }

    private ViewPager2 pager() {
        return controlador.get().findViewById(R.id.viewPagerHelp);
    }

    private Button boton(int id) {
        return controlador.get().findViewById(id);
    }
}
