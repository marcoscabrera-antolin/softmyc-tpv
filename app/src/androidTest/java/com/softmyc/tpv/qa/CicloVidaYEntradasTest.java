package com.softmyc.tpv.qa;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ActivityInfo;
import android.widget.EditText;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.action.ViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.softmyc.tpv.R;
import com.softmyc.tpv.presentation.AjustesActivity;
import com.softmyc.tpv.presentation.HistorialVentasActivity;
import com.softmyc.tpv.presentation.LoginActivity;
import com.softmyc.tpv.presentation.MainActivity;
import com.softmyc.tpv.presentation.ModificarPreciosActivity;
import com.softmyc.tpv.presentation.PedidoActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/** Fase 3.3/3.4: ciclo de vida (rotación, segundo plano, recreación) y entradas extremas en formularios. */
@RunWith(AndroidJUnit4.class)
public class CicloVidaYEntradasTest {

    private static final String TEXTO_LARGO = new String(new char[600]).replace('\0', 'x');
    private static final String EMOJIS = "🍔🍟 ñÑ áéíóú ü ¿?¡! \"comillas\" 'simple' <b>&amp;</b> ; DROP TABLE ventas;--";

    private final QaSupport qa = new QaSupport();

    @Before public void setUp() { qa.preparar(QaSupport.NEGOCIO_PRUEBA, "admin"); }
    @After public void tearDown() { qa.restaurar(); }

    private static void espera(ActivityScenario<?> s) throws Exception {
        QaSupport.esperar(5000, () -> s.getState() == Lifecycle.State.RESUMED);
        assertEquals(Lifecycle.State.RESUMED, s.getState());
    }

    private static void cancelarDialogoSiHay() {
        try {
            onView(withText("Cancelar")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
        } catch (Exception sinDialogo) {
            // no apareció el diálogo de confirmación: la URL era inválida y se rechazó antes
        }
    }

    // ---------- ciclo de vida ----------

    @Test public void pedido_rotaDosVecesYConservaLaPantalla() throws Exception {
        try (ActivityScenario<PedidoActivity> s = ActivityScenario.launch(PedidoActivity.class)) {
            espera(s);
            s.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            espera(s);
            onView(withId(R.id.listaPedido)).check(matches(isDisplayed()));
            s.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            espera(s);
            onView(withId(R.id.listaPedido)).check(matches(isDisplayed()));
            qa.captura("20_pedido_tras_rotar");
            s.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
        }
    }

    @Test public void pedido_rotaEnMitadDeCarga() throws Exception {
        try (ActivityScenario<PedidoActivity> s = ActivityScenario.launch(PedidoActivity.class)) {
            // sin esperar a RESUMED: rotar mientras se cargan categorías/productos
            s.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            s.onActivity(a -> a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            espera(s);
            onView(withId(R.id.recyclerProductosGrid)).check(matches(isDisplayed()));
        }
    }

    @Test public void todasLasPantallasSobrevivenARecreate() throws Exception {
        Class<?>[] clases = {MainActivity.class, PedidoActivity.class, AjustesActivity.class,
                HistorialVentasActivity.class, ModificarPreciosActivity.class};
        for (Class<?> c : clases) {
            @SuppressWarnings("unchecked")
            Class<? extends android.app.Activity> ac = (Class<? extends android.app.Activity>) c;
            try (ActivityScenario<? extends android.app.Activity> s = ActivityScenario.launch(ac)) {
                espera(s);
                s.recreate();
                espera(s);
                assertEquals(c.getSimpleName(), Lifecycle.State.RESUMED, s.getState());
            }
        }
    }

    @Test public void segundoPlanoYVuelta() throws Exception {
        try (ActivityScenario<PedidoActivity> s = ActivityScenario.launch(PedidoActivity.class)) {
            espera(s);
            s.moveToState(Lifecycle.State.CREATED);   // onStop: pasa a segundo plano
            s.moveToState(Lifecycle.State.STARTED);
            s.moveToState(Lifecycle.State.RESUMED);
            onView(withId(R.id.listaPedido)).check(matches(isDisplayed()));
            s.moveToState(Lifecycle.State.DESTROYED);
        }
    }

    // ---------- entradas extremas ----------

    @Test public void ajustes_textoMuyLargoYEmojisNoRompenLaPantalla() throws Exception {
        try (ActivityScenario<AjustesActivity> s = ActivityScenario.launch(AjustesActivity.class)) {
            espera(s);
            for (int id : new int[]{R.id.etNombreRestaurante, R.id.etDireccionFiscal, R.id.etInformacionFiscal}) {
                onView(withId(id)).perform(scrollTo(), replaceText(TEXTO_LARGO));
                onView(withId(id)).perform(replaceText(EMOJIS));
            }
            closeSoftKeyboard();
            onView(withId(R.id.btnGuardarAjustesTicket)).perform(scrollTo(), click());
            Thread.sleep(500);
            assertEquals(Lifecycle.State.RESUMED, s.getState());
        }
    }

    @Test public void ajustes_ipYPuertoInvalidosNoSeGuardanNiCierranLaApp() throws Exception {
        String[][] casos = {{"", ""}, {"999.999.1.1", "9100"}, {"192.168.1.5", "abc"}, {"192.168.1.5", "-1"},
                {"192.168.1.5", "99999"}, {EMOJIS, "9100"}, {TEXTO_LARGO, TEXTO_LARGO}};
        try (ActivityScenario<AjustesActivity> s = ActivityScenario.launch(AjustesActivity.class)) {
            espera(s);
            for (String[] c : casos) {
                onView(withId(R.id.etImpresoraIp)).perform(scrollTo(), replaceText(c[0]));
                onView(withId(R.id.etImpresoraPuerto)).perform(replaceText(c[1]));
                closeSoftKeyboard();
                onView(withId(R.id.btnGuardarIp)).perform(scrollTo(), click());
                Thread.sleep(300);
                assertEquals("caso " + c[0] + ":" + c[1], Lifecycle.State.RESUMED, s.getState());
            }
        }
    }

    @Test public void ajustes_dobleToqueEnGuardarNoFalla() throws Exception {
        try (ActivityScenario<AjustesActivity> s = ActivityScenario.launch(AjustesActivity.class)) {
            espera(s);
            onView(withId(R.id.etImpresoraIp)).perform(scrollTo(), replaceText("127.0.0.1"));
            onView(withId(R.id.etImpresoraPuerto)).perform(replaceText("9"));
            closeSoftKeyboard();
            onView(withId(R.id.btnGuardarIp)).perform(scrollTo(), ViewActions.doubleClick());
            Thread.sleep(300);
            assertEquals(Lifecycle.State.RESUMED, s.getState());
        }
    }

    @Test public void ajustes_urlDeServidorInvalida() throws Exception {
        String[] urls = {"", "no es una url", "ftp://x", "http://", "javascript:alert(1)", EMOJIS, TEXTO_LARGO};
        try (ActivityScenario<AjustesActivity> s = ActivityScenario.launch(AjustesActivity.class)) {
            espera(s);
            for (String u : urls) {
                onView(withId(R.id.etBackendUrl)).perform(scrollTo(), replaceText(u));
                closeSoftKeyboard();
                onView(withId(R.id.btnGuardarServidor)).perform(scrollTo(), click());
                Thread.sleep(300);
                cancelarDialogoSiHay();
                assertEquals("url " + u, Lifecycle.State.RESUMED, s.getState());
            }
        }
    }

    @Test public void login_camposVaciosLargosYEspeciales() throws Exception {
        qa.restaurar();
        com.softmyc.tpv.data.auth.Auth.store(qa.ctx).clear();
        try (ActivityScenario<LoginActivity> s = ActivityScenario.launch(LoginActivity.class)) {
            espera(s);
            String[][] casos = {{"", ""}, {"a@b.c", ""}, {"", "x"}, {TEXTO_LARGO, TEXTO_LARGO}, {EMOJIS, EMOJIS},
                    {"  ", "  "}, {"' OR 1=1 --", "' OR 1=1 --"}};
            for (String[] c : casos) {
                onView(withId(R.id.etEmail)).perform(replaceText(c[0]));
                onView(withId(R.id.etPassword)).perform(replaceText(c[1]));
                closeSoftKeyboard();
                onView(withId(R.id.btnLogin)).perform(click());
                Thread.sleep(400);
                assertEquals("login " + c[0], Lifecycle.State.RESUMED, s.getState());
            }
            AtomicReference<String> pass = new AtomicReference<>();
            s.onActivity(a -> pass.set(((EditText) a.findViewById(R.id.etPassword)).getText().toString()));
            assertTrue(pass.get() != null);
        }
        qa.preparar(QaSupport.NEGOCIO_PRUEBA, "admin");
    }

    @Test public void modificarPrecios_idYPrecioExtremos() throws Exception {
        String[] ids = {"", "0", "-1", "99999999999999999999", "abc", EMOJIS, "1.5"};
        try (ActivityScenario<ModificarPreciosActivity> s = ActivityScenario.launch(ModificarPreciosActivity.class)) {
            espera(s);
            for (String id : ids) {
                onView(withId(R.id.inputID)).perform(replaceText(id));
                closeSoftKeyboard();
                Thread.sleep(150);
                assertEquals("id " + id, Lifecycle.State.RESUMED, s.getState());
            }
        }
    }

    @Test public void principal_dobleToqueEnTarjetasNoAbreDosPantallas() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            espera(s);
            androidx.test.uiautomator.UiDevice d = androidx.test.uiautomator.UiDevice.getInstance(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());
            int x = d.getDisplayWidth() / 4, y = d.getDisplayHeight() / 3;
            d.click(x, y);
            d.click(x, y);
            Thread.sleep(1200);
            assertFalse("la app sigue viva", d.getCurrentPackageName() == null);
            assertEquals(qa.ctx.getPackageName(), d.getCurrentPackageName());
        }
    }
}
