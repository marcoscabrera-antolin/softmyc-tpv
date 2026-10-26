package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
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
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

/**
 * Ajustes: datos del ticket, impresora y servidor. Valida los datos antes de guardar (nombre obligatorio, puerto
 * entre 1 y 65535, URL http/https) y pide confirmación cuando el cambio de servidor cierra la sesión.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AjustesActivityTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String SERVIDOR_ACTUAL = "https://servidor-actual.ejemplo.com";
    private static final String SERVIDOR_NUEVO = "https://servidor-nuevo.ejemplo.com";
    /** La app guarda la URL normalizada con el endpoint de pedidos. */
    private static final String ENDPOINT = "/api/tpv/orders";

    private final android.content.Context app = ApplicationProvider.getApplicationContext();
    private ActivityController<AjustesActivity> controlador;

    @Before
    public void setUp() {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), TenantContext.LA_NUBE);
        Auth.store(app).save(AuthSession.demo(TenantContext.LA_NUBE, "admin", "ana", System.currentTimeMillis()));
        SecurePreferencesHelper.getPrinterPrefs(app).edit()
                .putString("nombre_restaurante", "Bar Sol")
                .putString("direccion_fiscal", "Calle Mayor 1")
                .putString("informacion_fiscal", "NIF B12345678")
                .putInt("tamanio_letra", 2)
                .putString("impresora_ip", "10.1.1.5")
                .putInt("impresora_puerto", 9200)
                .putString("backend_base_url", SERVIDOR_ACTUAL)
                .putString("backend_api_key", "clave-actual")
                .putString("simbolo_moneda", "EUR")
                .commit();
        controlador = Robolectric.buildActivity(AjustesActivity.class).setup();
    }

    @After
    public void tearDown() {
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
    }

    @Test
    public void al_destruirse_libera_el_hilo_de_segundo_plano() throws Exception {
        java.lang.reflect.Field campo = AjustesActivity.class.getDeclaredField("executor");
        campo.setAccessible(true);
        java.util.concurrent.ExecutorService hilo = (java.util.concurrent.ExecutorService) campo.get(controlador.get());

        controlador.pause().stop().destroy();

        assertTrue(hilo.isShutdown());
    }

    @Test
    public void muestra_los_valores_guardados_de_ticket_impresora_y_servidor() {
        assertEquals("Bar Sol", campo(R.id.etNombreRestaurante).getText().toString());
        assertEquals("Calle Mayor 1", campo(R.id.etDireccionFiscal).getText().toString());
        assertEquals(2, ((Spinner) controlador.get().findViewById(R.id.spinnerTamanioLetra)).getSelectedItemPosition());
        assertEquals("10.1.1.5", campo(R.id.etImpresoraIp).getText().toString());
        assertEquals("9200", campo(R.id.etImpresoraPuerto).getText().toString());
        assertEquals(SERVIDOR_ACTUAL, campo(R.id.etBackendUrl).getText().toString());
        assertEquals("EUR", campo(R.id.etSimboloMoneda).getText().toString());
    }

    @Test
    public void ticket_sin_nombre_no_guarda_y_avisa() {
        campo(R.id.etNombreRestaurante).setText("   ");

        controlador.get().findViewById(R.id.btnGuardarAjustesTicket).performClick();

        assertEquals("Escribe un nombre para el establecimiento", ShadowToast.getTextOfLatestToast());
        assertEquals("Bar Sol", prefs().getString("nombre_restaurante", null));
    }

    @Test
    public void ticket_valido_guarda_nombre_direccion_informacion_y_tamano_de_letra() {
        campo(R.id.etNombreRestaurante).setText("  Bar Luna ");
        campo(R.id.etDireccionFiscal).setText("Plaza 2");
        campo(R.id.etInformacionFiscal).setText("");
        ((Spinner) controlador.get().findViewById(R.id.spinnerTamanioLetra)).setSelection(0);

        controlador.get().findViewById(R.id.btnGuardarAjustesTicket).performClick();

        assertEquals("Bar Luna", prefs().getString("nombre_restaurante", null));
        assertEquals("Plaza 2", prefs().getString("direccion_fiscal", null));
        assertEquals("", prefs().getString("informacion_fiscal", null));
        assertEquals(0, prefs().getInt("tamanio_letra", -1));
        assertEquals("Ajustes de ticket guardados con éxito", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void impresora_con_campos_vacios_avisa_y_no_guarda() {
        campo(R.id.etImpresoraIp).setText("");

        controlador.get().findViewById(R.id.btnGuardarIp).performClick();

        assertEquals("Completa la dirección IP y puerto", ShadowToast.getTextOfLatestToast());
        assertEquals("10.1.1.5", prefs().getString("impresora_ip", null));
    }

    @Test
    public void impresora_con_puerto_fuera_de_rango_avisa_y_no_guarda() {
        campo(R.id.etImpresoraPuerto).setText("70000");

        controlador.get().findViewById(R.id.btnGuardarIp).performClick();

        assertEquals("El puerto debe ser un número entre 1 y 65535", ShadowToast.getTextOfLatestToast());
        assertEquals(9200, prefs().getInt("impresora_puerto", -1));
    }

    @Test
    public void impresora_valida_guarda_ip_y_puerto() {
        campo(R.id.etImpresoraIp).setText("192.168.0.42");
        campo(R.id.etImpresoraPuerto).setText("9100");

        controlador.get().findViewById(R.id.btnGuardarIp).performClick();

        assertEquals("192.168.0.42", prefs().getString("impresora_ip", null));
        assertEquals(9100, prefs().getInt("impresora_puerto", -1));
        assertEquals("Configuración guardada", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void puerto_valido_acepta_solo_numeros_entre_uno_y_65535() {
        assertEquals(1, com.softmyc.tpv.domain.port.ValidadorImpresora.puertoValido("1"));
        assertEquals(65535, com.softmyc.tpv.domain.port.ValidadorImpresora.puertoValido("65535"));
        assertEquals(-1, com.softmyc.tpv.domain.port.ValidadorImpresora.puertoValido("0"));
        assertEquals(-1, com.softmyc.tpv.domain.port.ValidadorImpresora.puertoValido("65536"));
        assertEquals(-1, com.softmyc.tpv.domain.port.ValidadorImpresora.puertoValido("-5"));
        assertEquals(-1, com.softmyc.tpv.domain.port.ValidadorImpresora.puertoValido("abc"));
        assertEquals(-1, com.softmyc.tpv.domain.port.ValidadorImpresora.puertoValido(""));
    }

    @Test
    public void servidor_con_url_que_no_es_http_se_rechaza_sin_tocar_la_configuracion() {
        campo(R.id.etBackendUrl).setText("?solo-parametros");

        controlador.get().findViewById(R.id.btnGuardarServidor).performClick();

        assertEquals("⚠️ URL del servidor no válida", ShadowToast.getTextOfLatestToast());
        assertEquals(SERVIDOR_ACTUAL, prefs().getString("backend_base_url", null));
    }

    @Test
    public void servidor_sin_cambio_de_raiz_guarda_directamente_y_normaliza_el_simbolo() {
        campo(R.id.etBackendUrl).setText(SERVIDOR_ACTUAL + "/");
        campo(R.id.etApiKey).setText("clave-nueva");
        campo(R.id.etSimboloMoneda).setText("€");

        controlador.get().findViewById(R.id.btnGuardarServidor).performClick();

        assertEquals(SERVIDOR_ACTUAL + ENDPOINT, prefs().getString("backend_base_url", null));
        assertEquals("clave-nueva", prefs().getString("backend_api_key", null));
        assertEquals("EUR", prefs().getString("simbolo_moneda", null));
        assertNull("sin cambio de servidor no hay confirmación", ShadowDialog.getLatestDialog());
    }

    @Test
    public void cambiar_de_servidor_pide_confirmacion_y_si_se_cancela_no_cambia_nada() {
        campo(R.id.etBackendUrl).setText(SERVIDOR_NUEVO);

        controlador.get().findViewById(R.id.btnGuardarServidor).performClick();
        AlertDialog confirmacion = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull("debe pedir confirmación al cambiar de servidor", confirmacion);
        confirmacion.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();

        assertEquals(SERVIDOR_ACTUAL, prefs().getString("backend_base_url", null));
    }

    @Test
    public void cambiar_de_servidor_confirmado_guarda_la_nueva_url_y_la_clave() {
        campo(R.id.etBackendUrl).setText(SERVIDOR_NUEVO);
        campo(R.id.etApiKey).setText("clave-del-nuevo");

        controlador.get().findViewById(R.id.btnGuardarServidor).performClick();
        AlertDialog confirmacion = (AlertDialog) ShadowDialog.getLatestDialog();
        confirmacion.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();

        assertEquals(SERVIDOR_NUEVO + ENDPOINT, prefs().getString("backend_base_url", null));
        assertEquals("clave-del-nuevo", prefs().getString("backend_api_key", null));
        assertTrue(prefs().getString("backend_base_url", "").startsWith("https://"));
    }

    private EditText campo(int id) {
        return controlador.get().findViewById(id);
    }

    private SharedPreferences prefs() {
        return SecurePreferencesHelper.getPrinterPrefs(app);
    }
}
