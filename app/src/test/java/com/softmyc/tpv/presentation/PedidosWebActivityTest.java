package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.view.View;
import android.widget.Spinner;
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
import java.net.ServerSocket;
import java.util.function.BooleanSupplier;

/**
 * Monitor de cocina (pedidos web): reparto entre «en cocina» y «listos», filtrado por negocio, tiempo de espera,
 * cambio de estado y errores del servidor. La impresora apunta a un puerto cerrado: la impresión automática falla
 * al instante y no sale a la red.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PedidosWebActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String NEGOCIO = "pedidoswebtest";
    private static final String RUTA_PEDIDOS = "/api/tpv/orders";
    private static final String RUTA_ESPERA = "/api/tpv/config/wait-time";
    private static final long TIMEOUT_MS = 8000;

    private final Application app = ApplicationProvider.getApplicationContext();
    private FakeApiServer servidor;
    private ActivityController<PedidosWebActivity> controlador;

    @Before
    public void setUp() throws Exception {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        servidor = new FakeApiServer();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear()
                .putString("backend_base_url", servidor.baseUrl() + RUTA_PEDIDOS)
                .putString("backend_api_key", "clave-test")
                .putString("impresora_ip", "127.0.0.1")
                .putInt("impresora_puerto", puertoCerrado())
                .commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO);
        Auth.store(app).save(AuthSession.demo(NEGOCIO, "admin", "ana", System.currentTimeMillis()));
    }

    @After
    public void tearDown() {
        if (controlador != null) controlador.pause().stop().destroy();
        servidor.close();
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
    }

    @Test
    public void reparte_los_pedidos_del_negocio_entre_cocina_y_listos_y_descarta_los_de_otro() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[" + pedido("a1", NEGOCIO, "pendiente", 1) + ","
                + pedido("b2", NEGOCIO, "listo", 2) + "," + pedido("c3", "otronegocio", "pendiente", 3) + "]");
        servidor.on(RUTA_ESPERA, 200, "{\"tiempo_espera\":45}");

        abrir();

        esperarA(() -> "1".equals(texto(R.id.tvPendingCount)) && "1".equals(texto(R.id.tvReadyCount)));
        assertTrue(texto(R.id.tvOrderCountHeader).contains("2 pedidos activos"));
        assertEquals(View.VISIBLE, controlador.get().findViewById(R.id.dashboardLayout).getVisibility());
        assertEquals(View.GONE, controlador.get().findViewById(R.id.emptyStateView).getVisibility());
    }

    @Test
    public void sin_pedidos_muestra_el_estado_vacio() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[]");
        servidor.on(RUTA_ESPERA, 200, "{}");

        abrir();

        esperarA(() -> texto(R.id.tvOrderCountHeader).contains("0 pedidos activos"));
        assertEquals(View.VISIBLE, controlador.get().findViewById(R.id.emptyStateView).getVisibility());
    }

    @Test
    public void un_error_del_servidor_al_pedir_pedidos_se_avisa() throws Exception {
        servidor.on(RUTA_PEDIDOS, 500, "{}");
        servidor.on(RUTA_ESPERA, 200, "{}");

        abrir();

        esperarA(() -> ultimoToastContiene("Error del servidor: 500"));
    }

    @Test
    public void una_respuesta_que_no_es_una_lista_no_rompe_la_pantalla() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "esto no es json");
        servidor.on(RUTA_ESPERA, 200, "{}");

        abrir();

        esperarA(() -> servidor.countOf(RUTA_PEDIDOS) > 0);
        ShadowLooper.idleMainLooper();
        assertEquals(View.VISIBLE, controlador.get().findViewById(R.id.rvPendingOrders).getVisibility());
    }

    @Test
    public void el_tiempo_de_espera_del_servidor_se_refleja_en_el_selector_y_se_guarda() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[]");
        servidor.on(RUTA_ESPERA, 200, "{\"tiempo_espera\":45}");

        abrir();

        Spinner selector = controlador.get().findViewById(R.id.spinnerTiempoEsperaCocina);
        esperarA(() -> selector.getSelectedItemPosition() == 5);
        assertEquals(45, SecurePreferencesHelper.getSessionPrefs(app).getInt("tiempo_espera_estimado", -1));
    }

    @Test
    public void un_tiempo_de_espera_sin_dato_valido_deja_el_valor_actual() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[]");
        servidor.on(RUTA_ESPERA, 200, "{\"tiempoEspera\":0}");

        abrir();

        esperarA(() -> servidor.countOf(RUTA_ESPERA) > 0);
        Spinner selector = controlador.get().findViewById(R.id.spinnerTiempoEsperaCocina);
        assertEquals(3, selector.getSelectedItemPosition());   // 30 min por defecto
    }

    @Test
    public void cambiar_el_tiempo_de_espera_lo_envia_al_servidor() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        abrir();
        esperarA(() -> servidor.countOf(RUTA_ESPERA) > 0);

        elegirTiempoHastaQueSeEnvie();

        esperarA(() -> ultimoToastContiene("Tiempo de espera web actualizado"));
    }

    @Test
    public void si_el_servidor_rechaza_el_nuevo_tiempo_se_avisa() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[]");
        servidor.on(RUTA_ESPERA, request -> "PUT".equals(request.method)
                ? new FakeApiServer.Reply(500, "{}") : new FakeApiServer.Reply(200, "{}"));
        abrir();
        esperarA(() -> servidor.countOf(RUTA_ESPERA) > 0);

        elegirTiempoHastaQueSeEnvie();

        esperarA(() -> ultimoToastContiene("Error del servidor (500)"));
    }

    @Test
    public void marcar_un_pedido_como_listo_envia_el_estado_y_recarga() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[" + pedido("a1", NEGOCIO, "preparando", 1) + "]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        servidor.on(RUTA_PEDIDOS + "/a1/status", 200, "{}");
        abrir();
        esperarA(() -> "1".equals(texto(R.id.tvPendingCount)));

        View tarjeta = tarjetaDe(R.id.rvPendingOrders);
        tarjeta.findViewById(R.id.btnMarkReady).performClick();

        esperarA(() -> ultimoToastContiene("Estado actualizado"));
        assertTrue(servidor.countOf(RUTA_PEDIDOS + "/a1/status") >= 1);
    }

    @Test
    public void si_el_servidor_rechaza_el_cambio_de_estado_se_avisa() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[" + pedido("a1", NEGOCIO, "preparando", 1) + "]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        servidor.on(RUTA_PEDIDOS + "/a1/status", 500, "{}");
        abrir();
        esperarA(() -> "1".equals(texto(R.id.tvPendingCount)));

        tarjetaDe(R.id.rvPendingOrders).findViewById(R.id.btnMarkReady).performClick();

        esperarA(() -> ultimoToastContiene("Error al actualizar: 500"));
    }

    @Test
    public void reimprimir_el_ticket_con_la_impresora_caida_avisa() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[" + pedido("a1", NEGOCIO, "preparando", 1) + "]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        abrir();
        esperarA(() -> "1".equals(texto(R.id.tvPendingCount)));

        tarjetaDe(R.id.rvPendingOrders).findViewById(R.id.btnPrintTicket).performClick();

        esperarA(() -> ultimoToastContiene("Ticket de cocina no impreso"));
    }

    @Test
    public void un_pedido_pendiente_se_intenta_imprimir_solo_y_si_falla_se_avisa() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[" + pedido("p9", NEGOCIO, "pendiente", 1) + "]");
        servidor.on(RUTA_ESPERA, 200, "{}");

        abrir();

        esperarA(() -> ultimoToastContiene("Se reintentará"));
    }

    @Test
    public void el_boton_actualizar_vuelve_a_pedir_pedidos_y_tiempo() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        abrir();
        esperarA(() -> servidor.countOf(RUTA_PEDIDOS) >= 1 && servidor.countOf(RUTA_ESPERA) >= 1);
        int pedidosAntes = servidor.countOf(RUTA_PEDIDOS);

        controlador.get().findViewById(R.id.btnRefreshOrders).performClick();

        esperarA(() -> servidor.countOf(RUTA_PEDIDOS) > pedidosAntes);
    }

    @Test
    public void el_boton_atras_cierra_la_pantalla() throws Exception {
        servidor.on(RUTA_PEDIDOS, 200, "[]");
        servidor.on(RUTA_ESPERA, 200, "{}");
        abrir();

        controlador.get().findViewById(R.id.btnBack).performClick();

        assertTrue(controlador.get().isFinishing());
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

    /** Las dos primeras selecciones del selector se descartan por diseño (arranque y sincronización): se insiste. */
    private void elegirTiempoHastaQueSeEnvie() throws Exception {
        Spinner selector = controlador.get().findViewById(R.id.spinnerTiempoEsperaCocina);
        int[] posiciones = {7, 1, 6, 2};
        for (int pos : posiciones) {
            selector.setSelection(pos);
            ShadowLooper.idleMainLooper();
            if (puts() > 0) return;
            Thread.sleep(100);
            if (puts() > 0) return;
        }
        esperarA(() -> puts() > 0);
    }

    private int puts() {
        int n = 0;
        for (FakeApiServer.Request r : servidor.requests()) {
            if ("PUT".equals(r.method) && RUTA_ESPERA.equals(r.path)) n++;
        }
        return n;
    }

    private static String pedido(String id, String negocio, String estado, int fecha) {
        return "{\"_id\":\"" + id + "\",\"restaurantId\":\"" + negocio + "\",\"estado\":\"" + estado
                + "\",\"fecha\":" + fecha + ",\"total\":9.5,\"cliente\":{\"nombre\":\"Ana\",\"telefono\":\"600\"},"
                + "\"items\":[{\"nombre\":\"Bocadillo\",\"cantidad\":1,\"precio\":4.5}]}";
    }

    private static int puertoCerrado() throws IOException {
        try (ServerSocket temporal = new ServerSocket(0)) {
            return temporal.getLocalPort();
        }
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
}
