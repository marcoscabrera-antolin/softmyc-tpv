package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Selector de negocio del administrador: solo para el rol admin, lista del servidor o la local si no hay conexión
 * o la respuesta no sirve, y cambio local cuando la sesión es de demostración.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AdminTenantSelectorDialogTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final long TIMEOUT_MS = 5000;
    private static final int NEGOCIOS_LOCALES = 6;
    private static final String NEGOCIO_NUEVO = "cambiotest";

    private FakeApiServer server;
    private ActivityController<Anfitrion> controlador;

    /** Activity anfitriona con el tema Material que usan los diálogos. */
    public static class Anfitrion extends AppCompatActivity {
        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            setTheme(R.style.Theme_MyApplication);
            super.onCreate(savedInstanceState);
        }
    }

    @Before
    public void setUp() throws Exception {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(ApplicationProvider.getApplicationContext());
        server = new FakeApiServer();
        Auth.store(ApplicationProvider.getApplicationContext()).clear();
        SecurePreferencesHelper.getPrinterPrefs(ApplicationProvider.getApplicationContext()).edit().clear()
                .putString("backend_base_url", server.baseUrl() + "/api/tpv/orders")
                .putString("backend_api_key", "clave-test")
                .putString("user_role", "admin")
                .putString("restaurant_id", TenantContext.LA_NUBE)
                .commit();
        controlador = Robolectric.buildActivity(Anfitrion.class).create().start().resume().visible();
    }

    @After
    public void tearDown() {
        server.close();
        Auth.store(ApplicationProvider.getApplicationContext()).clear();
        SecurePreferencesHelper.getSessionPrefs(ApplicationProvider.getApplicationContext()).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(ApplicationProvider.getApplicationContext()).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void usuario_que_no_es_admin_no_puede_abrir_el_selector() throws Exception {
        SecurePreferencesHelper.getPrinterPrefs(ApplicationProvider.getApplicationContext()).edit()
                .putString("user_role", "tpv_operator").commit();

        DialogFragment dialogo = mostrar();
        ShadowLooper.idleMainLooper();

        assertFalse(dialogo.getDialog() != null && dialogo.getDialog().isShowing());
    }

    @Test
    public void admin_con_servidor_disponible_ve_los_negocios_que_devuelve_la_api() throws Exception {
        server.on("/api/admin/restaurants", 200,
                "{\"success\":true,\"restaurants\":["
                        + "{\"id\":\"" + TenantContext.LA_NUBE + "\",\"nombre\":\"La Nube\",\"activo\":true},"
                        + "{\"id\":\"fruteria\",\"nombre\":\"Frutería\",\"activo\":true}]}");

        DialogFragment dialogo = mostrar();
        esperarCarga(dialogo);

        assertEquals(2, tarjetas(dialogo).getChildCount());
        assertEquals(View.GONE, dialogo.getView().findViewById(R.id.tvErrorBanner).getVisibility());
    }

    @Test
    public void sin_conexion_se_muestra_la_lista_local_y_el_aviso_de_modo_offline() throws Exception {
        server.close();

        DialogFragment dialogo = mostrar();
        esperarCarga(dialogo);

        assertEquals(NEGOCIOS_LOCALES, tarjetas(dialogo).getChildCount());
        TextView aviso = dialogo.getView().findViewById(R.id.tvErrorBanner);
        assertEquals(View.VISIBLE, aviso.getVisibility());
        assertTrue(aviso.getText().toString().startsWith("Modo offline"));
    }

    @Test
    public void respuesta_sin_exito_de_la_api_cae_a_la_lista_local() throws Exception {
        server.on("/api/admin/restaurants", 200, "{\"success\":false}");

        DialogFragment dialogo = mostrar();
        esperarCarga(dialogo);

        assertEquals(NEGOCIOS_LOCALES, tarjetas(dialogo).getChildCount());
    }

    @Test
    public void lista_vacia_de_la_api_cae_a_la_lista_local() throws Exception {
        server.on("/api/admin/restaurants", 200, "{\"success\":true,\"restaurants\":[]}");

        DialogFragment dialogo = mostrar();
        esperarCarga(dialogo);

        assertEquals(NEGOCIOS_LOCALES, tarjetas(dialogo).getChildCount());
    }

    @Test
    public void respuesta_que_no_es_json_cae_a_la_lista_local_sin_fallar() throws Exception {
        server.on("/api/admin/restaurants", 200, "<html>mantenimiento</html>");

        DialogFragment dialogo = mostrar();
        esperarCarga(dialogo);

        assertEquals(NEGOCIOS_LOCALES, tarjetas(dialogo).getChildCount());
    }

    @Test
    public void con_sesion_de_demostracion_el_cambio_es_local_y_avisa_al_llamante() throws Exception {
        Auth.store(ApplicationProvider.getApplicationContext()).save(
                AuthSession.demo(TenantContext.LA_NUBE, "admin", "ana", System.currentTimeMillis()));
        server.on("/api/admin/restaurants", 200,
                "{\"success\":true,\"restaurants\":[{\"id\":\"" + NEGOCIO_NUEVO + "\",\"nombre\":\"Prueba\",\"activo\":true}]}");
        DialogFragment dialogo = mostrar();
        esperarCarga(dialogo);
        List<String> cambios = new ArrayList<>();
        ((AdminTenantSelectorDialog) dialogo).setOnTenantSwitchedListener((id, nombre) -> cambios.add(id));

        tarjetas(dialogo).getChildAt(0).findViewById(R.id.cardTenantItem).performClick();
        dialogo.getView().findViewById(R.id.btnConfirmTenantSelect).performClick();
        esperarA(() -> !cambios.isEmpty());

        assertEquals(NEGOCIO_NUEVO, cambios.get(0));
        assertEquals(NEGOCIO_NUEVO, TenantContext.read(SecurePreferencesHelper.getPrinterPrefs(
                ApplicationProvider.getApplicationContext())));
    }

    @Test
    public void con_sesion_real_el_cambio_lo_pide_al_servidor_y_avisa_con_el_nuevo_negocio() throws Exception {
        long ahora = System.currentTimeMillis();
        Auth.store(ApplicationProvider.getApplicationContext()).save(new AuthSession("token-admin", "refresco-admin",
                ahora + 3_600_000L, ahora + 86_400_000L, "ana", "admin", TenantContext.LA_NUBE, false));
        server.on("/api/admin/restaurants", 200,
                "{\"success\":true,\"restaurants\":[{\"id\":\"" + NEGOCIO_NUEVO + "\",\"nombre\":\"Prueba\",\"activo\":true}]}");
        server.on("/api/admin/switch-restaurant", 200,
                "{\"success\":true,\"token\":\"token-nuevo\",\"refreshToken\":\"refresco-nuevo\","
                        + "\"expiresIn\":1800,\"refreshExpiresAt\":\"2100-01-01T00:00:00.000Z\","
                        + "\"user\":{\"username\":\"ana\",\"role\":\"admin\",\"restaurantId\":\"" + NEGOCIO_NUEVO + "\"}}");
        DialogFragment dialogo = mostrar();
        esperarCarga(dialogo);
        java.util.List<String> cambios = new ArrayList<>();
        ((AdminTenantSelectorDialog) dialogo).setOnTenantSwitchedListener((id, nombre) -> cambios.add(id));

        tarjetas(dialogo).getChildAt(0).findViewById(R.id.cardTenantItem).performClick();
        dialogo.getView().findViewById(R.id.btnConfirmTenantSelect).performClick();
        esperarA(() -> !cambios.isEmpty());

        assertEquals(NEGOCIO_NUEVO, cambios.get(0));
        assertEquals("token-nuevo", Auth.store(ApplicationProvider.getApplicationContext()).load().accessToken);
        assertEquals(1, server.countOf("/api/admin/switch-restaurant"));
    }

    private DialogFragment mostrar() {
        AdminTenantSelectorDialog dialogo = AdminTenantSelectorDialog.newInstance();
        dialogo.show(controlador.get().getSupportFragmentManager(), "selector-test");
        controlador.get().getSupportFragmentManager().executePendingTransactions();
        return dialogo;
    }

    private static ViewGroup tarjetas(DialogFragment dialogo) {
        return dialogo.getView().findViewById(R.id.containerTenants);
    }

    /** Espera a que termine la carga inicial (la respuesta del servidor llega desde otro hilo). */
    private static void esperarCarga(DialogFragment dialogo) throws InterruptedException {
        assertNotNull(dialogo.getView());
        esperarA(() -> dialogo.getView().findViewById(R.id.layoutLoading).getVisibility() == View.GONE);
    }

    private static void esperarA(java.util.function.BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando al selector de negocio");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }
}
