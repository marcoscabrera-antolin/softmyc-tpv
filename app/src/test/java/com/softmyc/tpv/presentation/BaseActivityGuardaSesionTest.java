package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.auth.SessionStore;
import com.softmyc.tpv.data.local.TenantContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;

/**
 * Guardas de sesión de {@link BaseActivity} (al arrancar y al volver a la pantalla). La decisión pura está en
 * {@link SessionPolicy}; aquí se prueba su efecto: limpiar la sesión, redirigir a LoginActivity o fijar el negocio.
 *
 * Los márgenes de tiempo son de horas o días, así que no dependen de la velocidad de la máquina. La red es la de
 * Robolectric por defecto (sin conexión), por lo que una sesión a punto de caducar no dispara el refresco en segundo plano.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BaseActivityGuardaSesionTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final long UNA_HORA_MS = 60L * 60L * 1000L;
    private static final long UN_DIA_MS = 24L * UNA_HORA_MS;
    private static final long SEGUNDOS_HASTA_CADUCAR_ACCESO = 10L;
    private static final String NEGOCIO = "fruteria";

    private final Context context = ApplicationProvider.getApplicationContext();

    @Before
    public void limpiarAlmacenes() {
        borrarPrefs();
    }

    @After
    public void restaurarAlmacenes() {
        borrarPrefs();
        GuardaActivity.exigeSesion = true;
    }

    @Test
    public void sin_sesion_guardada_redirige_al_login() {
        ActivityController<GuardaActivity> controller = arrancar(true);

        assertRedirigidoAlLogin(controller);
    }

    @Test
    public void sesion_incompleta_sin_refresco_se_trata_como_sin_sesion() {
        SecurePreferencesHelper.getSessionPrefs(context).edit().putString("access_token", "solo_acceso").commit();

        ActivityController<GuardaActivity> controller = arrancar(true);

        assertRedirigidoAlLogin(controller);
    }

    @Test
    public void sesion_con_refresco_caducado_borra_la_sesion_y_redirige_al_login() {
        long ahora = System.currentTimeMillis();
        guardarSesion(new AuthSession("acceso", "refresco", ahora - UNA_HORA_MS, ahora - 1000L,
                "ana", "tpv_operator", NEGOCIO, false));
        prepararNegocioActivo();

        ActivityController<GuardaActivity> controller = arrancar(true);

        assertRedirigidoAlLogin(controller);
        assertNull("la sesión caducada debe borrarse", Auth.store(context).load());
    }

    @Test
    public void sesion_valida_con_negocio_activo_se_queda_en_la_pantalla() {
        long ahora = System.currentTimeMillis();
        guardarSesion(new AuthSession("acceso", "refresco", ahora + UNA_HORA_MS, ahora + UN_DIA_MS,
                "ana", "tpv_operator", NEGOCIO, false));
        prepararNegocioActivo();

        ActivityController<GuardaActivity> controller = arrancar(true);

        assertFalse(controller.get().isFinishing());
        assertNull(Shadows.shadowOf(controller.get()).getNextStartedActivity());
    }

    @Test
    public void administrador_sin_negocio_elegido_redirige_al_login_para_elegirlo() {
        long ahora = System.currentTimeMillis();
        guardarSesion(new AuthSession("acceso", "refresco", ahora + UNA_HORA_MS, ahora + UN_DIA_MS,
                "admin", "admin", null, false));

        ActivityController<GuardaActivity> controller = arrancar(true);

        assertRedirigidoAlLogin(controller);
        assertNotNull(Auth.store(context).load());
    }

    @Test
    public void sesion_con_negocio_pero_sin_negocio_activo_fija_el_de_la_sesion() {
        long ahora = System.currentTimeMillis();
        guardarSesion(new AuthSession("acceso", "refresco", ahora + UNA_HORA_MS, ahora + UN_DIA_MS,
                "ana", "tpv_operator", NEGOCIO, false));

        ActivityController<GuardaActivity> controller = arrancar(true);

        assertFalse(controller.get().isFinishing());
        assertTrue(TenantContext.hasActive(SecurePreferencesHelper.getPrinterPrefs(context)));
        assertEquals(NEGOCIO, SecurePreferencesHelper.getPrinterPrefs(context).getString(TenantContext.KEY_RESTAURANT_ID, null));
    }

    @Test
    public void acceso_a_punto_de_caducar_sin_red_mantiene_la_sesion_valida() {
        long ahora = System.currentTimeMillis();
        guardarSesion(new AuthSession("acceso", "refresco", ahora + SEGUNDOS_HASTA_CADUCAR_ACCESO * 1000L,
                ahora + UN_DIA_MS, "ana", "tpv_operator", NEGOCIO, false));
        prepararNegocioActivo();

        ActivityController<GuardaActivity> controller = arrancar(true);

        assertFalse("sin red se sigue vendiendo con la sesión local", controller.get().isFinishing());
        assertNull(Shadows.shadowOf(controller.get()).getNextStartedActivity());
        assertEquals("acceso", Auth.store(context).load().accessToken);
    }

    @Test
    public void pantalla_que_no_exige_sesion_no_comprueba_nada() {
        ActivityController<GuardaActivity> controller = arrancar(false);

        assertFalse(controller.get().isFinishing());
        assertNull(Shadows.shadowOf(controller.get()).getNextStartedActivity());
    }

    private void prepararNegocioActivo() {
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(context), NEGOCIO);
    }

    private void guardarSesion(AuthSession sesion) {
        Auth.store(context).save(sesion);
    }

    private static void assertRedirigidoAlLogin(ActivityController<GuardaActivity> controller) {
        Intent siguiente = Shadows.shadowOf(controller.get()).getNextStartedActivity();
        assertTrue("debe abrir LoginActivity", siguiente != null
                && LoginActivity.class.getName().equals(siguiente.getComponent().getClassName()));
        assertTrue("la pantalla debe cerrarse", controller.get().isFinishing());
    }

    private ActivityController<GuardaActivity> arrancar(boolean exigeSesion) {
        GuardaActivity.exigeSesion = exigeSesion;
        return Robolectric.buildActivity(GuardaActivity.class).create().start().resume().visible();
    }

    private void borrarPrefs() {
        SecurePreferencesHelper.getSessionPrefs(context).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(context).edit().clear().commit();
    }

    /** Pantalla mínima que hereda la guarda; {@code exigeSesion} decide si la aplica. */
    public static class GuardaActivity extends BaseActivity {
        static boolean exigeSesion = true;

        @Override
        protected boolean requiresSession() {
            return exigeSesion;
        }
    }
}
