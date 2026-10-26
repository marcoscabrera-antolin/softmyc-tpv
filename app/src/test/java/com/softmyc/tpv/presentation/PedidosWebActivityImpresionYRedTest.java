package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.auth.FakeApiServer;
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
import org.robolectric.shadows.ShadowToast;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Monitor de cocina: huecos que deja {@link PedidosWebActivityTest}. Impresión correcta contra una impresora
 * simulada (manual y automática), reparto de estados terminales y errores de red (servidor caído).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PedidosWebActivityImpresionYRedTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String NEGOCIO = "pedidoswebtest2";
    private static final String RUTA_PEDIDOS = "/api/tpv/orders";
    private static final String RUTA_ESPERA = "/api/tpv/config/wait-time";
    private static final long TIMEOUT_MS = 8000;

    private final Application app = ApplicationProvider.getApplicationContext();
    private FakeApiServer servidor;
    private ImpresoraSimulada impresora;
    private ActivityController<PedidosWebActivity> controlador;

    @Before
    public void setUp() throws Exception {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        servidor = new FakeApiServer();
        impresora = new ImpresoraSimulada();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear()
                .putString("backend_base_url", servidor.baseUrl() + RUTA_PEDIDOS)
                .putString("backend_api_key", "clave-test")
                .putString("impresora_ip", "127.0.0.1")
                .putInt("impresora_puerto", impresora.puerto)
                .commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO);
        Auth.store(app).save(AuthSession.demo(NEGOCIO, "admin", "ana", System.currentTimeMillis()));
    }

    @After
    public void tearDown() {
        if (controlador != null) controlador.pause().stop().destroy();
        impresora.cerrar();
        servidor.close();
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
    }

    @Test
    public void reimprimir_el_ticket_de_cocina_con_la_impresora_disponible_lo_envia_y_avisa() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[" + pedido("a1", "preparando") + "]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        abrir();
        esperarA(() -> "1".equals(texto(R.id.tvPendingCount)));

        tarjetaDe(R.id.rvPendingOrders).findViewById(R.id.btnPrintTicket).performClick();

        esperarA(() -> ultimoToastContiene("Ticket de cocina impreso"));
        assertTrue(impresora.bytesRecibidos() > 0);
    }

    @Test
    public void un_pedido_pendiente_se_imprime_solo_y_se_marca_como_impreso_en_el_servidor() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[" + pedido("p1", "pendiente") + "]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        servidor.on(RUTA_PEDIDOS + "/p1/status", 200, "{}");

        abrir();

        esperarA(() -> servidor.countOf(RUTA_PEDIDOS + "/p1/status") >= 1);
        assertTrue(impresora.bytesRecibidos() > 0);
        FakeApiServer.Request cambio = ultimaPeticionA(RUTA_PEDIDOS + "/p1/status");
        assertTrue(cambio.body.contains("impreso"));
    }

    @Test
    public void los_pedidos_entregados_y_cancelados_no_aparecen_en_el_monitor() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[" + pedido("e1", "entregado") + "," + pedido("c1", "cancelado") + ","
                + pedido("r1", "retrasado") + "]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        servidor.on(RUTA_PEDIDOS + "/r1/status", 200, "{}");

        abrir();

        esperarA(() -> "1".equals(texto(R.id.tvPendingCount)));
        assertEquals("0", texto(R.id.tvReadyCount));
    }

    @Test
    public void entregar_un_pedido_listo_envia_el_estado_entregado() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[" + pedido("l1", "listo") + "]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        servidor.on(RUTA_PEDIDOS + "/l1/status", 200, "{}");
        abrir();
        esperarA(() -> "1".equals(texto(R.id.tvReadyCount)));

        tarjetaDe(R.id.rvReadyOrders).findViewById(R.id.btnMarkReady).performClick();

        esperarA(() -> ultimoToastContiene("Estado actualizado: Entregado"));
        assertTrue(ultimaPeticionA(RUTA_PEDIDOS + "/l1/status").body.contains("entregado"));
    }

    @Test
    public void retrasar_un_pedido_en_cocina_envia_el_estado_retrasado() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[" + pedido("a1", "preparando") + "]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        servidor.on(RUTA_PEDIDOS + "/a1/status", 200, "{}");
        abrir();
        esperarA(() -> "1".equals(texto(R.id.tvPendingCount)));

        tarjetaDe(R.id.rvPendingOrders).findViewById(R.id.btnMarkDelay).performClick();

        esperarA(() -> servidor.countOf(RUTA_PEDIDOS + "/a1/status") >= 1);
        assertTrue(ultimaPeticionA(RUTA_PEDIDOS + "/a1/status").body.contains("retrasado"));
    }

    @Test
    public void con_el_servidor_caido_se_avisa_del_error_de_conexion_y_la_pantalla_sigue_viva() throws Exception {
        servidor.close();

        abrir();

        esperarA(() -> ultimoToastContiene("⚠️"));
        assertEquals("0", texto(R.id.tvPendingCount));
    }

    @Test
    public void cambiar_el_estado_con_el_servidor_caido_avisa_del_error_de_red() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[" + pedido("a1", "preparando") + "]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        abrir();
        esperarA(() -> "1".equals(texto(R.id.tvPendingCount)));
        View tarjeta = tarjetaDe(R.id.rvPendingOrders);
        servidor.close();

        tarjeta.findViewById(R.id.btnMarkReady).performClick();

        esperarA(() -> ultimoToastContiene("Error de red al actualizar estado"));
    }

    @Test
    public void el_sondeo_periodico_vuelve_a_pedir_los_pedidos() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        abrir();
        esperarA(() -> servidor.countOf(RUTA_PEDIDOS) >= 1);
        int antes = servidor.countOf(RUTA_PEDIDOS);

        ShadowLooper.idleMainLooper(11, java.util.concurrent.TimeUnit.SECONDS);

        esperarA(() -> servidor.countOf(RUTA_PEDIDOS) > antes);
    }

    // ---------------------------------------------------------------------------------------------------------

    private void abrir() {
        controlador = Robolectric.buildActivity(PedidosWebActivity.class).setup();
    }

    private String texto(int id) {
        return ((TextView) controlador.get().findViewById(id)).getText().toString();
    }

    private boolean ultimoToastContiene(String fragmento) {
        CharSequence t = ShadowToast.getTextOfLatestToast();
        return t != null && t.toString().contains(fragmento);
    }

    private FakeApiServer.Request ultimaPeticionA(String ruta) {
        FakeApiServer.Request ultima = null;
        for (FakeApiServer.Request r : servidor.requests()) {
            if (ruta.equals(r.path)) ultima = r;
        }
        assertNotNull("no hubo ninguna petición a " + ruta, ultima);
        return ultima;
    }

    /** Fuerza el diseño del RecyclerView (Robolectric no lo hace solo) y devuelve la vista de la primera tarjeta. */
    private View tarjetaDe(int idRecycler) {
        RecyclerView rv = controlador.get().findViewById(idRecycler);
        rv.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
        rv.layout(0, 0, 1200, 1600);
        RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(0);
        assertNotNull("la tarjeta del pedido debe estar dibujada", holder);
        return holder.itemView;
    }

    private static String pedido(String id, String estado) {
        return "{\"_id\":\"" + id + "\",\"restaurantId\":\"" + NEGOCIO + "\",\"estado\":\"" + estado
                + "\",\"fecha\":1,\"total\":9.5,\"cliente\":{\"nombre\":\"Ana\",\"telefono\":\"600\"},"
                + "\"items\":[{\"nombre\":\"Bocadillo\",\"cantidad\":1,\"precio\":4.5}]}";
    }

    private static void esperarA(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando el resultado de la pantalla de pedidos web");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }

    /** Impresora térmica falsa: acepta conexiones TCP y cuenta los bytes recibidos. */
    private static final class ImpresoraSimulada {
        final int puerto;
        private final ServerSocket socket;
        private final AtomicInteger bytes = new AtomicInteger();

        ImpresoraSimulada() throws IOException {
            socket = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
            puerto = socket.getLocalPort();
            Thread hilo = new Thread(this::atender, "impresora-simulada-web");
            hilo.setDaemon(true);
            hilo.start();
        }

        private void atender() {
            while (!socket.isClosed()) {
                try (Socket cliente = socket.accept(); InputStream in = cliente.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        bytes.addAndGet(n);
                    }
                } catch (IOException e) {
                    return;
                }
            }
        }

        int bytesRecibidos() {
            return bytes.get();
        }

        void cerrar() {
            try {
                socket.close();
            } catch (IOException ignorada) {
                // cierre en limpieza: nada que hacer
            }
        }
    }
}
