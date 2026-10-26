package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.widget.TextView;

import com.softmyc.tpv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

/**
 * Pantalla de arranque: el subtítulo se escribe letra a letra y, pasados unos 3,5 s, se pasa al login. Si la
 * pantalla se destruye antes, no debe navegar.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SplashActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String TEXTO_COMPLETO = "Soluciones_Tecnológicas";

    @Test
    public void el_subtitulo_se_escribe_letra_a_letra_hasta_el_texto_completo() {
        ActivityController<SplashActivity> controlador = Robolectric.buildActivity(SplashActivity.class).setup();
        TextView subtitulo = controlador.get().findViewById(R.id.txtSubtitle);

        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        String parcial = subtitulo.getText().toString();
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);

        assertTrue("a mitad del efecto el texto aún no está completo", parcial.length() < TEXTO_COMPLETO.length());
        assertEquals(TEXTO_COMPLETO, subtitulo.getText().toString());
    }

    @Test
    public void tras_unos_tres_segundos_y_medio_pasa_al_login_y_se_cierra() {
        ActivityController<SplashActivity> controlador = Robolectric.buildActivity(SplashActivity.class).setup();

        ShadowLooper.idleMainLooper(5, TimeUnit.SECONDS);
        Intent siguiente = Shadows.shadowOf(controlador.get()).getNextStartedActivity();

        assertNotNull("debe abrirse el login", siguiente);
        assertEquals(LoginActivity.class.getName(), siguiente.getComponent().getClassName());
        assertTrue(controlador.get().isFinishing());
    }

    @Test
    public void si_la_pantalla_se_destruye_antes_no_navega_al_login() {
        ActivityController<SplashActivity> controlador = Robolectric.buildActivity(SplashActivity.class).setup();
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);

        controlador.pause().stop().destroy();
        ShadowLooper.idleMainLooper(5, TimeUnit.SECONDS);

        assertNotEquals(LoginActivity.class.getName(), siguienteDe(controlador));
    }

    private static String siguienteDe(ActivityController<SplashActivity> controlador) {
        Intent intent = Shadows.shadowOf(controlador.get()).getNextStartedActivity();
        return intent == null ? "" : intent.getComponent().getClassName();
    }
}
