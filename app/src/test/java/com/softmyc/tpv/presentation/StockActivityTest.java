package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.ProductoLocal;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Pantalla de stock: carga del catálogo, búsqueda por nombre o código, filtro de críticos, ajustes con +/−,
 * edición directa y la regla de no bajar de cero ni tocar los productos sin control de stock.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StockActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Negocio fuera de los catálogos iniciales: su base no se siembra en segundo plano. */
    private static final String NEGOCIO = "stocktest";
    private static final long TIMEOUT_MS = 8000;

    private final android.content.Context app = ApplicationProvider.getApplicationContext();
    private final ExecutorService fondo = Executors.newSingleThreadExecutor();
    private ActivityController<StockActivity> controlador;

    @Before
    public void setUp() throws Exception {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO);
        Auth.store(app).save(AuthSession.demo(NEGOCIO, "admin", "ana", System.currentTimeMillis()));
        sembrar(
                new ProductoLocal(1, "Pan", 1.0, "Panadería", new ArrayList<>()),
                new ProductoLocal(2, "Café", 1.5, "Bebidas", new ArrayList<>()),
                new ProductoLocal(3, "Agua", 1.0, "Bebidas", new ArrayList<>()),
                new ProductoLocal(4, "Vaso", 0.1, "Otros", new ArrayList<>()));
        setStock(1, 9);
        setStock(2, 0);
        setStock(3, 3);
        setStock(4, -1);
        controlador = Robolectric.buildActivity(StockActivity.class).setup();
        esperarA(() -> lista().getAdapter() != null && lista().getAdapter().getItemCount() == 4);
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
    public void muestra_todos_los_productos_del_catalogo() {
        assertEquals(4, lista().getAdapter().getItemCount());
        assertEquals(View.GONE, vacio().getVisibility());
    }

    @Test
    public void buscar_por_nombre_no_distingue_mayusculas() throws Exception {
        escribirBusqueda("CAFÉ");

        assertEquals(1, lista().getAdapter().getItemCount());
        assertEquals("#2  Café", texto(vistaFila(0), R.id.txtStockNombre));
    }

    @Test
    public void buscar_por_codigo_encuentra_el_producto_con_ese_numero() throws Exception {
        escribirBusqueda("3");

        assertEquals(1, lista().getAdapter().getItemCount());
        assertEquals("#3  Agua", texto(vistaFila(0), R.id.txtStockNombre));
    }

    @Test
    public void busqueda_sin_coincidencias_muestra_el_aviso_de_lista_vacia() throws Exception {
        escribirBusqueda("zzz");

        assertEquals(0, lista().getAdapter().getItemCount());
        assertEquals(View.VISIBLE, vacio().getVisibility());
    }

    @Test
    public void solo_criticos_deja_agotados_y_stock_bajo() throws Exception {
        ((CheckBox) controlador.get().findViewById(R.id.chkSoloCriticos)).setChecked(true);
        ShadowLooper.idleMainLooper();

        assertEquals(2, lista().getAdapter().getItemCount());
    }

    @Test
    public void el_estado_de_cada_fila_refleja_su_nivel_de_stock() throws Exception {
        ((CheckBox) controlador.get().findViewById(R.id.chkSoloCriticos)).setChecked(true);
        ShadowLooper.idleMainLooper();

        String estados = texto(vistaFila(0), R.id.txtStockEstado) + "|" + texto(vistaFila(1), R.id.txtStockEstado);

        assertTrue(estados.contains("Agotado"));
        assertTrue(estados.contains("Stock bajo"));
    }

    @Test
    public void el_boton_mas_suma_una_unidad_y_la_guarda() throws Exception {
        assertEquals("9", texto(vistaDeProducto(1), R.id.edtStockCantidad));

        vistaDeProducto(1).findViewById(R.id.btnStockMas).performClick();
        esperarA(() -> stockEnBd(1) == 10);

        assertEquals(10, stockEnBd(1));
        assertEquals("10", texto(vistaDeProducto(1), R.id.edtStockCantidad));
    }

    @Test
    public void el_boton_menos_no_baja_de_cero() throws Exception {
        vistaDeProducto(2).findViewById(R.id.btnStockMenos).performClick();
        ShadowLooper.idleMainLooper();

        assertEquals(0, stockEnBd(2));
        assertEquals("0", texto(vistaDeProducto(2), R.id.edtStockCantidad));
    }

    @Test
    public void sumar_a_un_producto_sin_control_de_stock_empieza_en_una_unidad() throws Exception {
        vistaDeProducto(4).findViewById(R.id.btnStockMas).performClick();
        esperarA(() -> stockEnBd(4) == 1);

        assertEquals(1, stockEnBd(4));
    }

    @Test
    public void escribir_la_cantidad_y_pulsar_hecho_guarda_ese_valor() throws Exception {
        EditText cantidad = (EditText) vistaDeProducto(1).findViewById(R.id.edtStockCantidad);
        cantidad.setText("12");

        cantidad.onEditorAction(EditorInfo.IME_ACTION_DONE);
        esperarA(() -> stockEnBd(1) == 12);

        assertEquals(12, stockEnBd(1));
    }

    @Test
    public void escribir_un_texto_no_numerico_deja_el_producto_sin_control_de_stock() throws Exception {
        EditText cantidad = (EditText) vistaDeProducto(1).findViewById(R.id.edtStockCantidad);
        cantidad.setText("muchas");

        cantidad.onEditorAction(EditorInfo.IME_ACTION_DONE);
        esperarA(() -> stockEnBd(1) == -1);

        assertEquals(-1, stockEnBd(1));
    }

    private void escribirBusqueda(String texto) throws InterruptedException {
        ((EditText) controlador.get().findViewById(R.id.edtBuscarStock)).setText(texto);
        ShadowLooper.idleMainLooper();
    }

    private RecyclerView lista() {
        return controlador.get().findViewById(R.id.recyclerStock);
    }

    private TextView vacio() {
        return controlador.get().findViewById(R.id.txtStockVacio);
    }

    /** Vista de la fila en la posición visible (fuerza la medida y el diseño del listado). */
    private View vistaFila(int posicion) {
        RecyclerView recycler = lista();
        recycler.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        recycler.layout(0, 0, 800, 1600);
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(posicion);
        assertNotNull("fila " + posicion + " no visible", holder);
        return holder.itemView;
    }

    /** Fila del producto con ese código (requiere que la lista esté sin filtrar). */
    private View vistaDeProducto(int codigo) {
        RecyclerView recycler = lista();
        recycler.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        recycler.layout(0, 0, 800, 1600);
        for (int i = 0; i < recycler.getAdapter().getItemCount(); i++) {
            RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(i);
            if (holder != null && texto(holder.itemView, R.id.txtStockNombre).startsWith("#" + codigo + " ")) {
                return holder.itemView;
            }
        }
        throw new AssertionError("no hay fila visible para el producto " + codigo);
    }

    private static String texto(View raiz, int id) {
        return ((TextView) raiz.findViewById(id)).getText().toString();
    }

    private void sembrar(ProductoLocal... productos) throws Exception {
        fondo.submit(() -> AppDatabase.getDatabaseForTenant(app, NEGOCIO).productoDao()
                .insertAll(java.util.Arrays.asList(productos))).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void setStock(int codigo, int stock) throws Exception {
        fondo.submit(() -> AppDatabase.getDatabaseForTenant(app, NEGOCIO).productoDao().setStock(codigo, stock))
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private int stockEnBd(int codigo) {
        try {
            Future<Integer> consulta = fondo.submit(() ->
                    AppDatabase.getDatabaseForTenant(app, NEGOCIO).productoDao().getByCodigo(codigo).stock);
            return consulta.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer el stock de " + codigo, e);
        }
    }

    private static void esperarA(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando la pantalla de stock");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }
}
