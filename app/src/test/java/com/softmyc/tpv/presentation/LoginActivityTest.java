package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.Intent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.FakeApiServer;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.function.BooleanSupplier;

/**
 * Pantalla de acceso: validación de campos, mensaje de credenciales incorrectas, entrada al TPV tras un login
 * correcto y envío con la tecla de la contraseña. El servidor es {@link FakeApiServer}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LoginActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String RUTA_LOGIN = "/api/tpv/auth/login";
    private static final String MENSAJE_CREDENCIALES = "Usuario o contraseña incorrectos.";
    private static final long TIMEOUT_MS = 8000;

    private final android.content.Context app = ApplicationProvider.getApplicationContext();
    private FakeApiServer servidor;
    private ActivityController<LoginActivity> controlador;

    @Before
    public void setUp() throws Exception {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        Auth.reset();
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        servidor = new FakeApiServer();
        SecurePreferencesHelper.getPrinterPrefs(app).edit()
                .putString("backend_base_url", servidor.baseUrl()).commit();
        controlador = Robolectric.buildActivity(LoginActivity.class).setup();
    }

    @After
    public void tearDown() {
        servidor.close();
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        Auth.reset();
    }

    @Test
    public void login_correcto_guarda_la_sesion_y_abre_el_menu_principal() throws Exception {
        servidor.on(RUTA_LOGIN, 200, "{\"success\":true,\"token\":\"at_ok\",\"refreshToken\":\"rt_ok\","
                + "\"expiresIn\":1800,\"refreshExpiresAt\":\"2100-01-01T00:00:00.000Z\","
                + "\"user\":{\"username\":\"ana\",\"role\":\"tpv_operator\",\"restaurantId\":\"fruteria\"}}");
        rellenar("ana", "clave");

        controlador.get().findViewById(R.id.btnLogin).performClick();
        esperarA(() -> Shadows.shadowOf(controlador.get()).peekNextStartedActivity() != null);

        Intent siguiente = Shadows.shadowOf(controlador.get()).getNextStartedActivity();
        assertEquals(MainActivity.class.getName(), siguiente.getComponent().getClassName());
        assertEquals("at_ok", Auth.store(app).load().accessToken);
        assertEquals("fruteria", Auth.store(app).load().restaurantId);
    }

    @Test
    public void con_campos_vacios_avisa_y_no_llama_al_servidor() {
        controlador.get().findViewById(R.id.btnLogin).performClick();
        ShadowLooper.idleMainLooper();

        assertEquals("Ingresa usuario y contraseña", ShadowToast.getTextOfLatestToast());
        assertEquals(0, servidor.requests().size());
    }

    @Test
    public void credenciales_incorrectas_muestran_el_mensaje_y_no_entran() throws Exception {
        servidor.on(RUTA_LOGIN, 401, "{\"error\":\"Credenciales incorrectas\"}");
        rellenar("ana", "mal");

        controlador.get().findViewById(R.id.btnLogin).performClick();
        esperarA(() -> MENSAJE_CREDENCIALES.equals(ShadowToast.getTextOfLatestToast()));

        assertEquals(1, servidor.countOf(RUTA_LOGIN));
        assertEquals(null, Shadows.shadowOf(controlador.get()).getNextStartedActivity());
    }

    @Test
    public void la_tecla_de_la_contrasena_tambien_envia_el_login() throws Exception {
        servidor.on(RUTA_LOGIN, 401, "{\"error\":\"Credenciales incorrectas\"}");
        rellenar("ana", "mal");

        ((EditText) controlador.get().findViewById(R.id.etPassword))
                .onEditorAction(EditorInfo.IME_ACTION_DONE);
        esperarA(() -> servidor.countOf(RUTA_LOGIN) == 1);

        assertEquals(1, servidor.countOf(RUTA_LOGIN));
    }

    private void rellenar(String usuario, String contrasena) {
        ((EditText) controlador.get().findViewById(R.id.etEmail)).setText(usuario);
        ((EditText) controlador.get().findViewById(R.id.etPassword)).setText(contrasena);
    }

    private static void esperarA(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando el login");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }
}
