package com.softmyc.tpv.qa;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.presentation.PedidoActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Fase 3.2: flujo de cobro de extremo a extremo con la impresora inaccesible (127.0.0.1:9). */
@RunWith(AndroidJUnit4.class)
public class FlujoCobroTest {

    private final QaSupport qa = new QaSupport();

    private int maxIdInicial;

    @Before public void setUp() throws Exception {
        qa.preparar(QaSupport.NEGOCIO_PRUEBA, "admin");
        maxIdInicial = qa.enDb(() -> {
            android.database.Cursor c = AppDatabase.getDatabase(qa.ctx).getOpenHelper().getReadableDatabase()
                    .query("SELECT COALESCE(MAX(id),0) FROM ventas");
            try { c.moveToFirst(); return c.getInt(0); } finally { c.close(); }
        });
    }

    /** Las ventas creadas por la prueba se borran: la base de la tablet queda como estaba. */
    @After public void tearDown() throws Exception {
        qa.enDb(() -> {
            AppDatabase.getDatabase(qa.ctx).getOpenHelper().getWritableDatabase()
                    .execSQL("DELETE FROM ventas WHERE id > ?", new Object[]{maxIdInicial});
            return null;
        });
        qa.restaurar();
    }

    private int ventasGuardadas() throws Exception {
        return qa.enDb(() -> AppDatabase.getDatabase(qa.ctx).ventaDao().getAll().size());
    }

    @Test public void cobrarSinLineasNoGuardaNada() throws Exception {
        int antes = ventasGuardadas();
        try (ActivityScenario<PedidoActivity> s = ActivityScenario.launch(PedidoActivity.class)) {
            QaSupport.esperar(5000, () -> s.getState() == Lifecycle.State.RESUMED);
            onView(withId(R.id.btnImprimir)).perform(click());
            Thread.sleep(800);
            assertEquals(Lifecycle.State.RESUMED, s.getState());
        }
        assertEquals("un pedido vacío no debe guardar una venta", antes, ventasGuardadas());
    }

    @Test public void anadirProductoYCobrarConImpresoraCaida_dobleToque() throws Exception {
        UiDevice d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try (ActivityScenario<PedidoActivity> s = ActivityScenario.launch(PedidoActivity.class)) {
            QaSupport.esperar(5000, () -> s.getState() == Lifecycle.State.RESUMED);
            Thread.sleep(1500);   // deja que cargue el catálogo sembrado
            onView(withId(R.id.recyclerProductosGrid)).perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
            Thread.sleep(800);
            qa.captura("30_tras_elegir_producto");
            onView(androidx.test.espresso.matcher.ViewMatchers.withText(
                    org.hamcrest.Matchers.containsString("AÑADIR AL CARRITO"))).perform(click());
            Thread.sleep(800);
            qa.captura("31_pedido_con_linea");
            onView(withId(R.id.btnImprimir)).perform(androidx.test.espresso.action.ViewActions.doubleClick());
            Thread.sleep(2500);
            qa.captura("32_tras_cobrar_impresora_caida");
            assertTrue("la app sigue en primer plano", qa.ctx.getPackageName().equals(d.getCurrentPackageName()));
        }
    }

    /** Cobro completo en efectivo con la impresora caída: la venta queda guardada y se avisa del fallo, sin cerrar la app. */
    @Test public void cobroEnEfectivoConImpresoraCaida_guardaLaVentaYAvisa() throws Exception {
        UiDevice d = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        int antes = ventasGuardadas();
        try (ActivityScenario<PedidoActivity> s = ActivityScenario.launch(PedidoActivity.class)) {
            QaSupport.esperar(5000, () -> s.getState() == Lifecycle.State.RESUMED);
            Thread.sleep(1500);
            onView(withId(R.id.recyclerProductosGrid)).perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
            Thread.sleep(600);
            onView(androidx.test.espresso.matcher.ViewMatchers.withText(
                    org.hamcrest.Matchers.containsString("AÑADIR AL CARRITO"))).perform(click());
            Thread.sleep(600);
            onView(withId(R.id.btnImprimir)).perform(click());
            Thread.sleep(600);
            onView(androidx.test.espresso.matcher.ViewMatchers.withText("Efectivo")).perform(click());
            Thread.sleep(4000);
            qa.captura("33_cobro_efectivo_resultado");
            assertTrue("la app sigue en primer plano", qa.ctx.getPackageName().equals(d.getCurrentPackageName()));
        }
        assertEquals("el cobro guarda exactamente una venta aunque falle la impresora", antes + 1, ventasGuardadas());
    }
}
