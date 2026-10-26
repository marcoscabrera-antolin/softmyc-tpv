package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Intent;
import android.widget.Button;
import android.widget.ListView;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.local.SalsaLocal;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Carta por categorías: la categoría inicial viene del Intent (o es hamburguesas), los productos se ordenan por
 * código, la categoría Salsas lista los nombres de salsa con precio cero y una categoría sin productos queda vacía.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ListaProductosActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Negocio fuera de los catálogos iniciales: su base no se siembra en segundo plano. */
    private static final String NEGOCIO = "listatest";
    private static final long TIMEOUT_MS = 8000;

    private final android.content.Context app = ApplicationProvider.getApplicationContext();
    private final ExecutorService fondo = Executors.newSingleThreadExecutor();

    @Before
    public void setUp() throws Exception {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO);
        Auth.store(app).save(AuthSession.demo(NEGOCIO, "admin", "ana", System.currentTimeMillis()));
        fondo.submit(() -> {
            AppDatabase db = AppDatabase.getDatabaseForTenant(app, NEGOCIO);
            db.productoDao().insertAll(Arrays.asList(
                    new ProductoLocal(10, "Pollo", 4.0, "Bocadillo", new ArrayList<>()),
                    new ProductoLocal(3, "Ternera", 4.5, "Bocadillo", new ArrayList<>()),
                    new ProductoLocal(20, "Cheeseburger", 6.0, "Hamburguesa", new ArrayList<>()),
                    new ProductoLocal(30, "Agua", 1.0, "Otros", new ArrayList<>())));
            db.salsaDao().insertAll(Arrays.asList(new SalsaLocal("Alioli"), new SalsaLocal("Ketchup")));
            return null;
        }).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @After
    public void tearDown() {
        fondo.shutdownNow();
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
    }

    @Test
    public void sin_categoria_en_el_intent_muestra_hamburguesas_y_resalta_su_boton() throws Exception {
        ActivityController<ListaProductosActivity> controlador =
                Robolectric.buildActivity(ListaProductosActivity.class).setup();

        ListView lista = esperarLista(controlador, 1);

        assertEquals("Cheeseburger", ((com.softmyc.tpv.domain.Producto) lista.getItemAtPosition(0)).getNombre());
        assertEquals(1.0f, boton(controlador, R.id.btnCatHamburguesas).getAlpha(), 1e-6f);
        assertEquals(0.5f, boton(controlador, R.id.btnCatOtros).getAlpha(), 1e-6f);
    }

    @Test
    public void la_categoria_del_intent_se_carga_ordenada_por_codigo() throws Exception {
        Intent intent = new Intent();
        intent.putExtra("CATEGORIA", "bocadillo");
        ActivityController<ListaProductosActivity> controlador =
                Robolectric.buildActivity(ListaProductosActivity.class, intent).setup();

        ListView lista = esperarLista(controlador, 2);

        assertEquals("Ternera", ((com.softmyc.tpv.domain.Producto) lista.getItemAtPosition(0)).getNombre());
        assertEquals("Pollo", ((com.softmyc.tpv.domain.Producto) lista.getItemAtPosition(1)).getNombre());
    }

    @Test
    public void pulsar_otros_cambia_la_lista_y_el_resaltado() throws Exception {
        ActivityController<ListaProductosActivity> controlador =
                Robolectric.buildActivity(ListaProductosActivity.class).setup();
        esperarLista(controlador, 1);

        boton(controlador, R.id.btnCatOtros).performClick();
        ListView lista = esperarLista(controlador, 1, "Agua");

        assertEquals("Agua", ((com.softmyc.tpv.domain.Producto) lista.getItemAtPosition(0)).getNombre());
        assertEquals(1.0f, boton(controlador, R.id.btnCatOtros).getAlpha(), 1e-6f);
        assertEquals(0.5f, boton(controlador, R.id.btnCatHamburguesas).getAlpha(), 1e-6f);
    }

    @Test
    public void la_categoria_salsas_lista_las_salsas_con_precio_cero() throws Exception {
        ActivityController<ListaProductosActivity> controlador =
                Robolectric.buildActivity(ListaProductosActivity.class).setup();
        esperarLista(controlador, 1);

        boton(controlador, R.id.btnCatSalsas).performClick();
        ListView lista = esperarLista(controlador, 2, "Alioli");

        com.softmyc.tpv.domain.Producto primera = (com.softmyc.tpv.domain.Producto) lista.getItemAtPosition(0);
        assertEquals("Alioli", primera.getNombre());
        assertEquals(0.0, primera.getPrecio(), 1e-9);
        assertEquals("Ketchup", ((com.softmyc.tpv.domain.Producto) lista.getItemAtPosition(1)).getNombre());
    }

    @Test
    public void una_categoria_sin_productos_deja_la_lista_vacia() throws Exception {
        Intent intent = new Intent();
        intent.putExtra("CATEGORIA", "postres");
        ActivityController<ListaProductosActivity> controlador =
                Robolectric.buildActivity(ListaProductosActivity.class, intent).setup();

        esperarA(() -> listaSinEsperar(controlador) != null && listaSinEsperar(controlador).getAdapter() != null);
        assertEquals(0, listaSinEsperar(controlador).getCount());
    }

    private static ListView listaSinEsperar(ActivityController<ListaProductosActivity> controlador) {
        return controlador.get().findViewById(R.id.listaProductos);
    }

    private static Button boton(ActivityController<ListaProductosActivity> controlador, int id) {
        Button b = controlador.get().findViewById(id);
        assertNotNull("botón " + id, b);
        return b;
    }

    private ListView esperarLista(ActivityController<ListaProductosActivity> controlador, int cantidad)
            throws InterruptedException {
        esperarA(() -> listaSinEsperar(controlador).getCount() == cantidad);
        return listaSinEsperar(controlador);
    }

    private ListView esperarLista(ActivityController<ListaProductosActivity> controlador, int cantidad, String primero)
            throws InterruptedException {
        esperarA(() -> listaSinEsperar(controlador).getCount() == cantidad
                && primero.equals(((com.softmyc.tpv.domain.Producto)
                listaSinEsperar(controlador).getItemAtPosition(0)).getNombre()));
        return listaSinEsperar(controlador);
    }

    private static void esperarA(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando la carta");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }
}
