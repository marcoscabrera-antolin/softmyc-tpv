package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.auth.FakeApiServer;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.local.SalsaLocal;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.presentation.viewmodel.PedidoViewModel;
import com.softmyc.tpv.util.FalloImpresion;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Pantalla de pedido (cobro): carta, líneas, edición, hora de recogida, método de pago, recuperación de tickets y
 * diálogo de fallo de impresión. No se pulsa ningún método de pago para no disparar la impresora.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PedidoActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Negocio fuera de los catálogos iniciales: su base solo tiene lo que inserta el test. */
    private static final String NEGOCIO = "pedidoacttest";
    private static final long TIMEOUT_MS = 8000;

    private final Application app = ApplicationProvider.getApplicationContext();
    private final ExecutorService fondo = Executors.newSingleThreadExecutor();
    private FakeApiServer servidor;
    private ActivityController<PedidoActivity> controlador;
    private PedidoViewModel vm;

    @Before
    public void setUp() throws Exception {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        servidor = new FakeApiServer();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear()
                .putString("backend_base_url", servidor.baseUrl() + "/api/tpv/orders")
                .putString("backend_api_key", "clave-test")
                .commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO);
        Auth.store(app).save(AuthSession.demo(NEGOCIO, "admin", "ana", System.currentTimeMillis()));
        fondo.submit(() -> {
            AppDatabase db = AppDatabase.getDatabaseForTenant(app, NEGOCIO);
            db.productoDao().insertAll(Arrays.asList(
                    new ProductoLocal(1, "Café", 1.5, "Bebida", new ArrayList<>()),
                    new ProductoLocal(2, "Tarta", 4.0, "Postre", new ArrayList<>())));
            db.salsaDao().insertAll(Arrays.asList(new SalsaLocal("Alioli")));
            return null;
        }).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        controlador = Robolectric.buildActivity(PedidoActivity.class).setup();
        vm = new ViewModelProvider(controlador.get()).get(PedidoViewModel.class);
        esperarA(() -> vm.productosDisponibles.getValue() != null && vm.productosDisponibles.getValue().size() == 2);
    }

    @After
    public void tearDown() {
        controlador.pause().stop().destroy();
        servidor.close();
        fondo.shutdownNow();
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
    }

    @Test
    public void al_arrancar_muestra_total_a_cero_y_la_carta() {
        TextView total = controlador.get().findViewById(R.id.textoTotal);

        assertTrue(total.getText().toString().startsWith("TOTAL:"));
        assertEquals(2, vm.productosDisponibles.getValue().size());
    }

    @Test
    public void imprimir_con_el_pedido_vacio_avisa_y_no_abre_el_selector_de_pago() {
        controlador.get().findViewById(R.id.btnImprimir).performClick();
        ShadowLooper.idleMainLooper();

        assertEquals("Pedido vacío", ShadowToast.getTextOfLatestToast());
        assertEquals(null, ShadowDialog.getLatestDialog());
    }

    @Test
    public void imprimir_con_lineas_abre_el_selector_y_bloquea_el_boton_hasta_cerrarlo() {
        agregarLineaCafe(1);
        View imprimir = controlador.get().findViewById(R.id.btnImprimir);

        imprimir.performClick();
        android.app.AlertDialog selector = (android.app.AlertDialog) ShadowDialog.getLatestDialog();

        assertNotNull(selector);
        assertFalse(imprimir.isEnabled());
        assertEquals(0.5f, imprimir.getAlpha(), 1e-6f);
        selector.dismiss();
        ShadowLooper.idleMainLooper();
        assertTrue(imprimir.isEnabled());
        assertEquals(1f, imprimir.getAlpha(), 1e-6f);
    }

    @Test
    public void vaciar_un_pedido_vacio_avisa() {
        controlador.get().findViewById(R.id.btnVaciarPedido).performClick();

        assertEquals("El pedido ya está vacío", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void vaciar_con_lineas_pide_confirmacion_y_las_quita() {
        agregarLineaCafe(2);

        controlador.get().findViewById(R.id.btnVaciarPedido).performClick();
        androidx.appcompat.app.AlertDialog dialogo = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        dialogo.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();

        assertTrue(vm.pedido.getValue().isEmpty());
    }

    @Test
    public void cancelar_el_vaciado_conserva_las_lineas() {
        agregarLineaCafe(1);

        controlador.get().findViewById(R.id.btnVaciarPedido).performClick();
        ((androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog())
                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).performClick();

        assertEquals(1, vm.pedido.getValue().size());
    }

    @Test
    public void la_hora_de_recogida_se_pide_y_se_muestra() {
        controlador.get().findViewById(R.id.btnHora).performClick();
        android.app.AlertDialog dialogo = (android.app.AlertDialog) ShadowDialog.getLatestDialog();
        primerEditText(dialogo).setText("21:30");
        dialogo.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();

        TextView hora = controlador.get().findViewById(R.id.txtHoraRecogida);
        assertEquals("Hora: 21:30", hora.getText().toString());
    }

    @Test
    public void introducir_un_id_inexistente_avisa_y_uno_valido_lo_selecciona() {
        introducirId("999");
        assertEquals("ID 999 no encontrado", ShadowToast.getTextOfLatestToast());

        introducirId("1");
        assertEquals("Seleccionado: Café", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void introducir_un_id_vacio_no_hace_nada() {
        ShadowToast.reset();

        introducirId("");

        assertEquals(0, ShadowToast.shownToastCount());
    }

    @Test
    public void tocar_una_linea_ofrece_sumar_restar_y_eliminar() {
        agregarLineaCafe(2);
        ListView lista = controlador.get().findViewById(R.id.listaPedido);

        elegirOpcionDeLinea(lista, 1);   // +1
        assertEquals(3, vm.pedido.getValue().get(0).getCantidad());

        elegirOpcionDeLinea(lista, 2);   // -1
        assertEquals(2, vm.pedido.getValue().get(0).getCantidad());

        elegirOpcionDeLinea(lista, 3);   // eliminar (con deshacer)
        assertTrue(vm.pedido.getValue().isEmpty());
    }

    @Test
    public void editar_una_linea_la_deja_como_item_temporal() {
        agregarLineaCafe(1);
        ListView lista = controlador.get().findViewById(R.id.listaPedido);

        elegirOpcionDeLinea(lista, 0);   // editar

        assertNotNull(vm.itemTemporal.getValue());
    }

    @Test
    public void un_fallo_de_impresion_con_ticket_ofrece_reintentar_y_se_limpia_al_cerrar() throws Exception {
        publicarFallo(new FalloImpresion(7, "sin papel"));
        androidx.appcompat.app.AlertDialog dialogo = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();

        assertNotNull(dialogo);
        assertEquals(View.VISIBLE, dialogo.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).getVisibility());

        dialogo.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(null, vm.falloImpresion.getValue());
    }

    @Test
    public void un_fallo_sin_ticket_no_ofrece_reintentar() throws Exception {
        publicarFallo(new FalloImpresion(FalloImpresion.TICKET_DESCONOCIDO, "sin red"));
        androidx.appcompat.app.AlertDialog dialogo = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();

        assertNotNull(dialogo);
        assertEquals(View.GONE, dialogo.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).getVisibility());
    }

    @Test
    public void recuperar_un_ticket_inexistente_avisa() throws Exception {
        controlador.get().findViewById(R.id.btnRecuperarPedido).performClick();
        esperarA(() -> ShadowDialog.getLatestDialog() != null);
        android.app.AlertDialog dialogo = (android.app.AlertDialog) ShadowDialog.getLatestDialog();
        primerEditText(dialogo).setText("9999");

        dialogo.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        esperarA(() -> {
            CharSequence t = ShadowToast.getTextOfLatestToast();
            return t != null && t.toString().contains("No se encontró el número de pedido 9999");
        });

        assertTrue(vm.pedido.getValue().isEmpty());
    }

    @Test
    public void recuperar_con_un_numero_que_desborda_un_entero_avisa() throws Exception {
        controlador.get().findViewById(R.id.btnRecuperarPedido).performClick();
        esperarA(() -> ShadowDialog.getLatestDialog() != null);
        android.app.AlertDialog dialogo = (android.app.AlertDialog) ShadowDialog.getLatestDialog();
        primerEditText(dialogo).setText("99999999999");

        dialogo.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();

        assertEquals("Número no válido", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void abrir_la_personalizacion_de_un_producto_muestra_el_dialogo() throws Exception {
        Producto cafe = vm.productosDisponibles.getValue().get(0);
        java.lang.reflect.Method abrir = PedidoActivity.class.getDeclaredMethod("abrirModalPersonalizacion", Producto.class);
        abrir.setAccessible(true);

        abrir.invoke(controlador.get(), cafe);
        ShadowLooper.idleMainLooper();

        assertNotNull(controlador.get().getSupportFragmentManager().findFragmentByTag("PersonalizarProducto"));
    }

    @Test
    public void personalizar_un_producto_nulo_no_hace_nada() throws Exception {
        java.lang.reflect.Method abrir = PedidoActivity.class.getDeclaredMethod("abrirModalPersonalizacion", Producto.class);
        abrir.setAccessible(true);

        abrir.invoke(controlador.get(), (Object) null);

        assertEquals(null, controlador.get().getSupportFragmentManager().findFragmentByTag("PersonalizarProducto"));
    }

    // ---------------------------------------------------------------------------------------------------------

    private void agregarLineaCafe(int cantidad) {
        List<PedidoItem> items = new ArrayList<>();
        items.add(new PedidoItem(new Producto(1, "Café", 1.5, "Bebida", new ArrayList<>(), "UD"), cantidad));
        vm.setPedidoDirectamente(items);
        ShadowLooper.idleMainLooper();
    }

    private void introducirId(String texto) {
        controlador.get().findViewById(R.id.btnIntroducirID).performClick();
        android.app.AlertDialog dialogo = (android.app.AlertDialog) ShadowDialog.getLatestDialog();
        primerEditText(dialogo).setText(texto);
        dialogo.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
    }

    private void elegirOpcionDeLinea(ListView lista, int opcion) {
        lista.performItemClick(lista.getChildAt(0), 0, 0);
        android.app.AlertDialog dialogo = (android.app.AlertDialog) ShadowDialog.getLatestDialog();
        dialogo.getListView().performItemClick(null, opcion, opcion);
        ShadowLooper.idleMainLooper();
    }

    private void publicarFallo(FalloImpresion fallo) throws Exception {
        java.lang.reflect.Field campo = PedidoViewModel.class.getDeclaredField("_falloImpresion");
        campo.setAccessible(true);
        ((androidx.lifecycle.MutableLiveData<FalloImpresion>) campo.get(vm)).setValue(fallo);
        ShadowLooper.idleMainLooper();
    }

    private static EditText primerEditText(android.app.Dialog dialogo) {
        EditText e = buscarEditText(dialogo.getWindow().getDecorView());
        assertNotNull("el diálogo debe tener un campo de texto", e);
        return e;
    }

    private static EditText buscarEditText(View vista) {
        if (vista instanceof EditText) return (EditText) vista;
        if (vista instanceof ViewGroup) {
            ViewGroup grupo = (ViewGroup) vista;
            for (int i = 0; i < grupo.getChildCount(); i++) {
                EditText e = buscarEditText(grupo.getChildAt(i));
                if (e != null) return e;
            }
        }
        return null;
    }

    private static void esperarA(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando el resultado de la pantalla de pedido");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }
}
