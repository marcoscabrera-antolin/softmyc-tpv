package com.softmyc.tpv.qa;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.softmyc.tpv.R;
import com.softmyc.tpv.presentation.AjustesActivity;
import com.softmyc.tpv.presentation.ArqueoCocinaActivity;
import com.softmyc.tpv.presentation.AyudaActivity;
import com.softmyc.tpv.presentation.EstadisticasActivity;
import com.softmyc.tpv.presentation.Fase2DemoActivity;
import com.softmyc.tpv.presentation.HistorialVentasActivity;
import com.softmyc.tpv.presentation.ListaProductosActivity;
import com.softmyc.tpv.presentation.LoginActivity;
import com.softmyc.tpv.presentation.MainActivity;
import com.softmyc.tpv.presentation.ModificarPreciosActivity;
import com.softmyc.tpv.presentation.PedidoActivity;
import com.softmyc.tpv.presentation.PedidosWebActivity;
import com.softmyc.tpv.presentation.SplashActivity;
import com.softmyc.tpv.presentation.StockActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/** Fase 3.1: todas las pantallas se inflan sin errores de recursos y se ven completas en la tablet. */
@RunWith(AndroidJUnit4.class)
public class PantallasInfladoTest {

    private final QaSupport qa = new QaSupport();

    @Before
    public void setUp() {
        qa.preparar(QaSupport.NEGOCIO_PRUEBA, "admin");
        try {   // la app es de tablet apaisada: las medidas de geometría se hacen siempre en horizontal
            androidx.test.uiautomator.UiDevice d = androidx.test.uiautomator.UiDevice.getInstance(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());
            d.setOrientationNatural();
            d.unfreezeRotation();
            if (d.getDisplayHeight() > d.getDisplayWidth()) d.setOrientationLeft();
        } catch (Exception ignorada) {
            // sin UiAutomator disponible se usa la orientación actual
        }
    }

    @After
    public void tearDown() {
        qa.restaurar();
    }

    private <A extends Activity> void inflaYSeVe(Class<A> clase, int idClave, String captura) throws Exception {
        try (ActivityScenario<A> scenario = ActivityScenario.launch(clase)) {
            QaSupport.esperar(5000, () -> scenario.getState() == Lifecycle.State.RESUMED);
            assertEquals(clase.getSimpleName() + " no llegó a RESUMED", Lifecycle.State.RESUMED, scenario.getState());
            onView(withId(idClave)).check(matches(isDisplayed()));
            AtomicReference<String> problema = new AtomicReference<>();
            scenario.onActivity(a -> problema.set(comprobarGeometria(a)));
            qa.captura(captura);
            assertTrue(clase.getSimpleName() + ": " + problema.get(), problema.get() == null);
        }
    }

    /** Ninguna vista visible se sale por los lados de la pantalla, salvo dentro de contenedores con desplazamiento. */
    private static String comprobarGeometria(Activity a) {
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        View raiz = a.findViewById(android.R.id.content);
        StringBuilder sb = new StringBuilder();
        revisar(raiz, dm.widthPixels, sb, false);
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void revisar(View v, int anchoPantalla, StringBuilder sb, boolean dentroDeScroll) {
        if (v.getVisibility() != View.VISIBLE) return;
        boolean scroll = dentroDeScroll || v instanceof android.widget.HorizontalScrollView
                || v instanceof androidx.recyclerview.widget.RecyclerView || v instanceof androidx.viewpager2.widget.ViewPager2;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        if (!scroll && v.getWidth() > 0 && (loc[0] + v.getWidth() > anchoPantalla + 2 || loc[0] < -2)) {
            String nombre;
            try {
                nombre = v.getResources().getResourceEntryName(v.getId());
            } catch (Exception e) {
                nombre = v.getClass().getSimpleName();
            }
            sb.append(nombre).append(" fuera de pantalla (x=").append(loc[0]).append(", ancho=").append(v.getWidth())
                    .append(", pantalla=").append(anchoPantalla).append("); ");
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) revisar(g.getChildAt(i), anchoPantalla, sb, scroll);
        }
    }

    /** La pantalla de inicio salta sola al login: solo se exige que se vea al arrancar, no que se quede. */
    @Test public void splash() throws Exception {
        try (ActivityScenario<SplashActivity> scenario = ActivityScenario.launch(SplashActivity.class)) {
            onView(withId(R.id.splashContainer)).check(matches(isDisplayed()));
            onView(withId(R.id.txtTitle)).check(matches(isDisplayed()));
            qa.captura("01_splash");
        }
    }
    @Test public void login() throws Exception {
        qa.restaurar();   // sin sesión: el login se queda en pantalla en vez de saltar a Main
        com.softmyc.tpv.data.auth.Auth.store(qa.ctx).clear();
        inflaYSeVe(LoginActivity.class, R.id.loginContainer, "02_login");
        qa.preparar(QaSupport.NEGOCIO_PRUEBA, "admin");
    }
    @Test public void principal() throws Exception { inflaYSeVe(MainActivity.class, R.id.gridView, "03_principal"); }
    @Test public void pedido() throws Exception { inflaYSeVe(PedidoActivity.class, R.id.listaPedido, "04_pedido"); }
    @Test public void ajustes() throws Exception { inflaYSeVe(AjustesActivity.class, R.id.etImpresoraIp, "05_ajustes"); }
    @Test public void stock() throws Exception { inflaYSeVe(StockActivity.class, R.id.recyclerStock, "06_stock"); }
    @Test public void historial() throws Exception { inflaYSeVe(HistorialVentasActivity.class, R.id.listaVentas, "07_historial"); }
    @Test public void estadisticas() throws Exception { inflaYSeVe(EstadisticasActivity.class, R.id.salesChartView, "08_estadisticas"); }
    @Test public void arqueoCocina() throws Exception { inflaYSeVe(ArqueoCocinaActivity.class, R.id.txtTotalCocina, "09_arqueo"); }
    @Test public void modificarPrecios() throws Exception { inflaYSeVe(ModificarPreciosActivity.class, R.id.inputID, "10_modificar_precios"); }
    @Test public void listaProductos() throws Exception { inflaYSeVe(ListaProductosActivity.class, R.id.listaProductos, "11_lista_productos"); }
    @Test public void ayuda() throws Exception { inflaYSeVe(AyudaActivity.class, R.id.viewPagerHelp, "12_ayuda"); }
    @Test public void simuladorFase2() throws Exception { inflaYSeVe(Fase2DemoActivity.class, R.id.itemsContainer, "13_fase2"); }
    @Test public void pedidosWeb() throws Exception { inflaYSeVe(PedidosWebActivity.class, R.id.dashboardLayout, "14_pedidos_web"); }

    /** BUG-QA-02: en vertical la barra inferior de la pantalla principal no se sale de la pantalla (es desplazable). */
    @Test public void principalEnVerticalNoSeSaleDeLaPantalla() throws Exception {
        androidx.test.uiautomator.UiDevice d = androidx.test.uiautomator.UiDevice.getInstance(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());
        d.setOrientationNatural();   // esta tablet es 1200x1920: natural = vertical
        try {
            inflaYSeVe(MainActivity.class, R.id.gridView, "15_principal_vertical");
        } finally {
            d.setOrientationLeft();
            d.unfreezeRotation();
        }
    }
}
