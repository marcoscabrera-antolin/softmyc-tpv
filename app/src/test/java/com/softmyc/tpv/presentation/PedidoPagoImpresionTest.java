package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.view.View;
import android.widget.Button;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.auth.FakeApiServer;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;
import com.softmyc.tpv.data.local.VentaLocal;
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Cobro de extremo a extremo con venta real en Room y una impresora simulada por un {@link ServerSocket} local:
 * selector de pago, guardado, impresión correcta, fallo de impresión con reintento y reimpresión de tickets.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PedidoPagoImpresionTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String NEGOCIO = "pedidopagotest";
    private static final long TIMEOUT_MS = 10000;
    private static final int EFECTIVO = 0;
    private static final int TARJETA = 1;

    private final Application app = ApplicationProvider.getApplicationContext();
    private final ExecutorService fondo = Executors.newSingleThreadExecutor();
    private FakeApiServer servidor;
    private ImpresoraSimulada impresora;
    private ActivityController<PedidoActivity> controlador;
    private PedidoViewModel vm;

    @Before
    public void setUp() throws Exception {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        servidor = new FakeApiServer();
        impresora = new ImpresoraSimulada();
        configurarPreferencias(impresora.puerto);
        Auth.store(app).save(AuthSession.demo(NEGOCIO, "admin", "ana", System.currentTimeMillis()));
        ejecutarEnBaseLocal(() -> AppDatabase.getDatabaseForTenant(app, NEGOCIO).productoDao().insertAll(Arrays.asList(
                new ProductoLocal(1, "Café", 1.5, "Bebida", new ArrayList<>()))));
        controlador = Robolectric.buildActivity(PedidoActivity.class).setup();
        vm = new ViewModelProvider(controlador.get()).get(PedidoViewModel.class);
        esperarA(() -> vm.productosDisponibles.getValue() != null && vm.productosDisponibles.getValue().size() == 1);
    }

    @After
    public void tearDown() {
        controlador.pause().stop().destroy();
        impresora.cerrar();
        servidor.close();
        fondo.shutdownNow();
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
    }

    @Test
    public void pagar_en_efectivo_guarda_la_venta_imprime_el_ticket_y_vacia_el_pedido() throws Exception {
        agregarLineaCafe(2);

        elegirMetodoDePago(EFECTIVO);
        esperarA(() -> vm.pedido.getValue().isEmpty() && impresora.texto().toUpperCase().contains("CAF"));

        List<VentaLocal> ventas = ventasGuardadas();
        assertEquals(1, ventas.size());
        assertEquals("Efectivo", ventas.get(0).metodoPago);
        assertEquals(3.0, ventas.get(0).total, 1e-6);
        assertTrue(impresora.texto().toUpperCase().contains("CAF"));
        assertNull(vm.falloImpresion.getValue());
    }

    @Test
    public void pagar_con_tarjeta_registra_el_metodo_de_pago_elegido() throws Exception {
        agregarLineaCafe(1);

        elegirMetodoDePago(TARJETA);
        esperarA(() -> vm.pedido.getValue().isEmpty());

        assertEquals("Tarjeta", ventasGuardadas().get(0).metodoPago);
    }

    @Test
    public void tras_cobrar_el_boton_imprimir_vuelve_a_estar_activo() throws Exception {
        agregarLineaCafe(1);
        View imprimir = controlador.get().findViewById(R.id.btnImprimir);

        elegirMetodoDePago(EFECTIVO);
        esperarA(() -> vm.pedido.getValue().isEmpty());

        assertTrue(imprimir.isEnabled());
    }

    @Test
    public void con_la_impresora_caida_la_venta_se_guarda_y_se_ofrece_reintentar() throws Exception {
        impresora.cerrar();
        agregarLineaCafe(1);

        elegirMetodoDePago(EFECTIVO);
        esperarA(() -> vm.falloImpresion.getValue() != null);

        assertEquals(1, ventasGuardadas().size());
        FalloImpresion fallo = vm.falloImpresion.getValue();
        assertTrue(fallo.getTicketId() > 0);
        androidx.appcompat.app.AlertDialog dialogo = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialogo);
        assertEquals(View.VISIBLE, dialogo.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).getVisibility());
    }

    @Test
    public void reintentar_desde_el_dialogo_reimprime_el_ticket_cuando_la_impresora_vuelve() throws Exception {
        int puertoCaido = impresora.puerto;
        impresora.cerrar();
        agregarLineaCafe(1);
        elegirMetodoDePago(EFECTIVO);
        esperarA(() -> vm.falloImpresion.getValue() != null);
        impresora = new ImpresoraSimulada(puertoCaido);

        ((androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog())
                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
        esperarA(() -> impresora.texto().toUpperCase().contains("CAF"));

        assertNull(vm.falloImpresion.getValue());
        assertTrue(impresora.texto().toUpperCase().contains("CAF"));
    }

    @Test
    public void una_segunda_pulsacion_mientras_hay_una_venta_en_curso_no_duplica_la_venta() throws Exception {
        agregarLineaCafe(1);

        vm.imprimirYGuardarVenta(app, "Bar", "Efectivo");
        vm.imprimirYGuardarVenta(app, "Bar", "Efectivo");
        esperarA(() -> vm.pedido.getValue().isEmpty());

        assertEquals(1, ventasGuardadas().size());
    }

    @Test
    public void reimprimir_un_ticket_guardado_lo_envia_a_la_impresora_y_avisa() throws Exception {
        int id = guardarVenta("Tarjeta", jsonDeUnCafe());

        vm.reimprimirTicket(app, "Bar", id);
        esperarA(() -> impresora.texto().toUpperCase().contains("CAF"));
        esperarA(() -> ("Ticket n.º " + id + " reimpreso").equals(String.valueOf(ShadowToast.getTextOfLatestToast())));

        assertTrue(impresora.texto().toUpperCase().contains("CAF"));
    }

    @Test
    public void reimprimir_un_ticket_inexistente_avisa_de_que_no_se_encontro() throws Exception {
        vm.reimprimirTicket(app, "Bar", 4242);

        esperarA(() -> "No se encontró la venta n.º 4242".equals(String.valueOf(ShadowToast.getTextOfLatestToast())));

        assertEquals(0, impresora.bytesRecibidos());
    }

    @Test
    public void reimprimir_con_la_impresora_caida_publica_el_fallo_con_el_numero_de_ticket() throws Exception {
        int id = guardarVenta("Efectivo", jsonDeUnCafe());
        impresora.cerrar();

        vm.reimprimirTicket(app, "Bar", id);
        esperarA(() -> vm.falloImpresion.getValue() != null);

        assertEquals(id, vm.falloImpresion.getValue().getTicketId());
    }

    @Test
    public void reimprimir_una_venta_con_lineas_ilegibles_ofrece_reintentar_con_su_numero() throws Exception {
        int id = guardarVenta("Efectivo", "esto no es json");

        vm.reimprimirTicket(app, "Bar", id);
        esperarA(() -> vm.falloImpresion.getValue() != null);

        assertEquals(id, vm.falloImpresion.getValue().getTicketId());
        assertEquals(0, impresora.bytesRecibidos());
    }

    @Test
    public void recuperar_muestra_los_tickets_recientes_y_pulsar_uno_carga_su_pedido() throws Exception {
        int id = guardarVenta("Efectivo", jsonDeUnCafe());

        android.app.AlertDialog dialogo = abrirRecuperar();
        Button reciente = botonQueEmpieza(dialogo.getWindow().getDecorView(), "Ticket #" + id);
        assertNotNull(reciente);
        reciente.performClick();
        esperarA(() -> !vm.pedido.getValue().isEmpty());

        assertEquals(2, vm.pedido.getValue().get(0).getCantidad());
        assertEquals(Integer.valueOf(id), vm.getRecuperadoVentaId());
        assertTrue(String.valueOf(ShadowToast.getTextOfLatestToast()).contains("Pedido #" + id + " cargado"));
    }

    @Test
    public void recuperar_escribiendo_el_numero_carga_el_pedido() throws Exception {
        int id = guardarVenta("Tarjeta", jsonDeUnCafe());

        android.app.AlertDialog dialogo = abrirRecuperar();
        primerEditText(dialogo).setText(String.valueOf(id));
        dialogo.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        esperarA(() -> !vm.pedido.getValue().isEmpty());

        assertEquals(Integer.valueOf(id), vm.getRecuperadoVentaId());
    }

    @Test
    public void recuperar_una_venta_sin_lineas_avisa_de_que_esta_vacia() throws Exception {
        int id = guardarVenta("Efectivo", "[]");

        android.app.AlertDialog dialogo = abrirRecuperar();
        primerEditText(dialogo).setText(String.valueOf(id));
        dialogo.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        esperarA(() -> String.valueOf(ShadowToast.getTextOfLatestToast()).contains("vacío"));

        assertTrue(vm.pedido.getValue().isEmpty());
        assertNull(vm.getRecuperadoVentaId());
    }

    @Test
    public void cobrar_un_pedido_recuperado_sobrescribe_la_venta_en_vez_de_crear_otra() throws Exception {
        int id = guardarVenta("Efectivo", jsonDeUnCafe());
        android.app.AlertDialog dialogo = abrirRecuperar();
        primerEditText(dialogo).setText(String.valueOf(id));
        dialogo.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        esperarA(() -> !vm.pedido.getValue().isEmpty());

        elegirMetodoDePago(TARJETA);
        esperarA(() -> vm.pedido.getValue().isEmpty());

        List<VentaLocal> ventas = ventasGuardadas();
        assertEquals(1, ventas.size());
        assertEquals("Tarjeta", ventas.get(0).metodoPago);
    }

    // ---------------------------------------------------------------------------------------------------------

    private android.app.AlertDialog abrirRecuperar() throws Exception {
        controlador.get().findViewById(R.id.btnRecuperarPedido).performClick();
        esperarA(() -> ShadowDialog.getLatestDialog() != null);
        return (android.app.AlertDialog) ShadowDialog.getLatestDialog();
    }

    private static android.widget.EditText primerEditText(android.app.Dialog dialogo) {
        return (android.widget.EditText) buscar(dialogo.getWindow().getDecorView(), android.widget.EditText.class, null);
    }

    private static Button botonQueEmpieza(View raiz, String prefijo) {
        return (Button) buscar(raiz, Button.class, prefijo);
    }

    private static View buscar(View vista, Class<? extends View> tipo, String prefijoTexto) {
        if (tipo.isInstance(vista) && (prefijoTexto == null
                || ((android.widget.TextView) vista).getText().toString().startsWith(prefijoTexto))) {
            return vista;
        }
        if (vista instanceof android.view.ViewGroup) {
            android.view.ViewGroup grupo = (android.view.ViewGroup) vista;
            for (int i = 0; i < grupo.getChildCount(); i++) {
                View e = buscar(grupo.getChildAt(i), tipo, prefijoTexto);
                if (e != null) return e;
            }
        }
        return null;
    }

    private void configurarPreferencias(int puertoImpresora) {
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear()
                .putString("backend_base_url", servidor.baseUrl() + "/api/tpv/orders")
                .putString("backend_api_key", "clave-test")
                .putString("impresora_ip", "127.0.0.1")
                .putInt("impresora_puerto", puertoImpresora)
                .commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO);
    }

    private void agregarLineaCafe(int cantidad) {
        List<PedidoItem> items = new ArrayList<>();
        items.add(new PedidoItem(new Producto(1, "Café", 1.5, "Bebida", new ArrayList<>(), "UD"), cantidad));
        vm.setPedidoDirectamente(items);
        ShadowLooper.idleMainLooper();
    }

    private void elegirMetodoDePago(int opcion) {
        controlador.get().findViewById(R.id.btnImprimir).performClick();
        android.app.AlertDialog selector = (android.app.AlertDialog) ShadowDialog.getLatestDialog();
        selector.getListView().performItemClick(null, opcion, opcion);
        selector.dismiss();
        ShadowLooper.idleMainLooper();
    }

    private String jsonDeUnCafe() {
        List<PedidoItem> items = new ArrayList<>();
        items.add(new PedidoItem(new Producto(1, "Café", 1.5, "Bebida", new ArrayList<>(), "UD"), 2));
        return com.softmyc.tpv.data.sync.SyncManager.serializarItems(items);
    }

    private int guardarVenta(String metodo, String itemsJson) throws Exception {
        long id = enBaseLocal(() -> AppDatabase.getDatabaseForTenant(app, NEGOCIO).ventaDao()
                .insert(new VentaLocal(System.currentTimeMillis(), 3.0, itemsJson, true, metodo)));
        return (int) id;
    }

    private List<VentaLocal> ventasGuardadas() throws Exception {
        return enBaseLocal(() -> AppDatabase.getDatabaseForTenant(app, NEGOCIO).ventaDao().getAll());
    }

    private <T> T enBaseLocal(java.util.concurrent.Callable<T> tarea) throws Exception {
        return fondo.submit(tarea).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void ejecutarEnBaseLocal(Runnable tarea) throws Exception {
        fondo.submit(tarea).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static void esperarA(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando el resultado del cobro");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }

    /** Impresora térmica falsa: acepta una conexión TCP y guarda todo lo que recibe hasta que el cliente la cierra. */
    private static final class ImpresoraSimulada {
        final int puerto;
        private final ServerSocket socket;
        private final ByteArrayOutputStream recibido = new ByteArrayOutputStream();
        private final AtomicInteger bytes = new AtomicInteger();

        ImpresoraSimulada() throws Exception {
            this(0);
        }

        ImpresoraSimulada(int puertoDeseado) throws Exception {
            socket = new ServerSocket(puertoDeseado, 5, java.net.InetAddress.getByName("127.0.0.1"));
            puerto = socket.getLocalPort();
            Thread hilo = new Thread(this::atender, "impresora-simulada");
            hilo.setDaemon(true);
            hilo.start();
        }

        private void atender() {
            while (!socket.isClosed()) {
                try (Socket cliente = socket.accept(); InputStream in = cliente.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        synchronized (recibido) {
                            recibido.write(buffer, 0, n);
                        }
                        bytes.addAndGet(n);
                    }
                } catch (java.io.IOException e) {
                    return;
                }
            }
        }

        int bytesRecibidos() {
            return bytes.get();
        }

        String texto() {
            synchronized (recibido) {
                return new String(recibido.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1);
            }
        }

        void cerrar() {
            try {
                socket.close();
            } catch (java.io.IOException ignorada) {
                // cierre en limpieza: nada que hacer
            }
        }
    }
}
