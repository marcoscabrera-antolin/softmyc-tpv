package com.softmyc.tpv.presentation.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.auth.FakeApiServer;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Pantalla de inicio de sesión contra el servidor falso ({@link FakeApiServer}) con el cliente HTTP real.
 *
 * En tests {@code BuildConfig.DEBUG} es true (compilación debug): los casos de demostración local existen aquí y se
 * documentan como comportamiento de depuración. Sin red no hay contingencia en ninguna compilación.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LoginViewModelTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final long ESPERA_SEGUNDOS = 15;
    private static final long EXPIRACION_REFRESCO_MS = 4_102_444_800_000L; // 2100-01-01: vigente para cualquier ejecución
    private static final String MENSAJE_CREDENCIALES = "Usuario o contraseña incorrectos.";
    private static final String MENSAJE_SERVIDOR = "No se pudo iniciar sesión (error del servidor).";
    private static final String MENSAJE_RED = "No se pudo conectar con el servidor. Comprueba la conexión e inténtalo de nuevo.";
    private static final String MENSAJE_SIN_RED = "No se pudo conectar con el servidor. Comprueba la conexión e inténtalo de nuevo.";
    private static final String RUTA_LOGIN = "/api/tpv/auth/login";

    @Rule
    public InstantTaskExecutorRule executorInstantaneo = new InstantTaskExecutorRule();

    private final Application app = ApplicationProvider.getApplicationContext();
    private FakeApiServer servidor;
    private LoginViewModel viewModel;

    @Before
    public void arrancarServidorYLimpiarAlmacenes() throws Exception {
        borrarAlmacenes();
        servidor = new FakeApiServer();
        apuntarAlServidor(servidor.baseUrl());
        viewModel = new LoginViewModel(app, Runnable::run);
    }

    @After
    public void cerrarServidorYLimpiarAlmacenes() throws Exception {
        servidor.close();
        borrarAlmacenes();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
    }

    @Test
    public void login_correcto_guarda_la_sesion_y_entra_al_negocio() throws Exception {
        servidor.on(RUTA_LOGIN, 200, sesionJson("at_ok", "fruteria", "tpv_operator"));
        Espera<Boolean> exito = observar(viewModel.loginSuccess, true);

        viewModel.loginEnServidor("Ana", "clave");

        assertTrue(exito.esperar());
        assertEquals("ana", viewModel.currentUser.getValue());
        assertTrue(servidor.requests().get(0).body.contains("\"username\":\"ana\""));
        AuthSession guardada = Auth.store(app).load();
        assertEquals("at_ok", guardada.accessToken);
        assertEquals("fruteria", guardada.restaurantId);
        assertFalse(guardada.demo);
        assertEquals("fruteria", TenantContext.read(SecurePreferencesHelper.getPrinterPrefs(app)));
    }

    @Test
    public void credenciales_incorrectas_muestran_mensaje_y_no_guardan_sesion() throws Exception {
        servidor.on(RUTA_LOGIN, 401, "{\"error\":\"Credenciales incorrectas\"}");
        Espera<String> error = observar(viewModel.errorMessage, MENSAJE_CREDENCIALES);

        viewModel.loginEnServidor("ana", "mal");

        assertEquals(MENSAJE_CREDENCIALES, error.esperar());
        assertNull(Auth.store(app).load());
        assertNull(viewModel.loginSuccess.getValue());
    }

    @Test
    public void error_del_servidor_500_muestra_mensaje_generico() throws Exception {
        servidor.on(RUTA_LOGIN, 500, "{\"error\":\"fallo interno\"}");
        Espera<String> error = observar(viewModel.errorMessage, MENSAJE_SERVIDOR);

        viewModel.loginEnServidor("ana", "clave");

        assertEquals(MENSAJE_SERVIDOR, error.esperar());
        assertNull(Auth.store(app).load());
    }

    @Test
    public void demasiados_intentos_429_avisa_de_esperar() throws Exception {
        servidor.on(RUTA_LOGIN, 429, "{\"error\":\"rate\"}");
        Espera<String> error = observar(viewModel.errorMessage,
                "Demasiados intentos. Espera unos minutos antes de volver a probar.");

        viewModel.loginEnServidor("ana", "clave");

        assertEquals("Demasiados intentos. Espera unos minutos antes de volver a probar.", error.esperar());
    }

    @Test
    public void servidor_ocupado_503_pide_reintentar() throws Exception {
        servidor.on(RUTA_LOGIN, 503, "{\"error\":\"busy\"}");
        Espera<String> error = observar(viewModel.errorMessage,
                "El servidor está ocupado. Inténtalo de nuevo en unos segundos.");

        viewModel.loginEnServidor("ana", "clave");

        assertEquals("El servidor está ocupado. Inténtalo de nuevo en unos segundos.", error.esperar());
    }

    @Test
    public void sin_acceso_al_negocio_403_muestra_el_motivo_del_servidor() throws Exception {
        servidor.on(RUTA_LOGIN, 403, "{\"error\":\"Sin acceso a ese negocio\"}");
        Espera<String> error = observar(viewModel.errorMessage, "Sin acceso a ese negocio");

        viewModel.loginEnServidor("ana", "clave", "taller");

        assertEquals("Sin acceso a ese negocio", error.esperar());
    }

    @Test
    public void administrador_sin_negocio_abre_el_selector_y_no_entra_directo() throws Exception {
        servidor.on(RUTA_LOGIN, 200, sesionJson("at_admin", null, "admin"));
        Espera<Boolean> selector = observar(viewModel.showTenantSelector, true);

        viewModel.loginEnServidor("root", "clave");

        assertTrue(selector.esperar());
        assertNull(viewModel.loginSuccess.getValue());
        assertNull(Auth.store(app).load().restaurantId);
    }

    @Test
    public void varios_negocios_piden_elegir_y_la_eleccion_repite_el_acceso() throws Exception {
        servidor.on(RUTA_LOGIN, request -> request.body.contains("\"restaurantId\"")
                ? new FakeApiServer.Reply(200, sesionJson("at_taller", "taller", "tpv_operator"))
                : new FakeApiServer.Reply(400, "{\"error\":\"Elige negocio\",\"restaurants\":["
                + "{\"id\":\"fruteria\",\"nombre\":\"Frutería\"},{\"id\":\"taller\",\"nombre\":\"Taller\"}]}"));
        Espera<java.util.List<com.softmyc.tpv.data.auth.AuthApi.Restaurant>> lista = observarNoNulo(viewModel.chooseTenant);
        Espera<Boolean> exito = observar(viewModel.loginSuccess, true);

        viewModel.loginEnServidor("ana", "clave");

        assertEquals(2, lista.esperar().size());
        viewModel.elegirNegocio("taller");
        assertTrue(exito.esperar());
        assertEquals("taller", Auth.store(app).load().restaurantId);
        assertEquals(2, servidor.countOf(RUTA_LOGIN));
    }

    @Test
    public void sin_red_muestra_error_y_no_concede_sesion() throws Exception {
        // Sin contingencia de depuración: sin servidor nunca se concede sesión sin credenciales válidas.
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), "fruteria");
        apuntarAlServidor("http://127.0.0.1:" + puertoLibre());
        Espera<String> error = observar(viewModel.errorMessage, MENSAJE_RED);

        viewModel.loginEnServidor("ana", "clave");

        assertEquals(MENSAJE_RED, error.esperar());
        AuthSession sesion = Auth.store(app).load();
        assertTrue(sesion == null || !sesion.demo);
    }

    @Test
    public void usuario_de_demostracion_entra_sin_llamar_a_la_api() throws Exception {
        Espera<Boolean> exito = observar(viewModel.loginSuccess, true);

        viewModel.loginEnServidor("fruteria", "cualquiera");

        assertTrue(exito.esperar());
        assertEquals(0, servidor.requests().size());
        assertTrue(Auth.store(app).load().demo);
    }

    @Test
    public void cada_usuario_de_demostracion_abre_la_sesion_de_su_sector() {
        String[][] casos = {{"taller", "taller"}, {"operador_taller", "taller"}, {"paqueteria", "paqueteria"},
                {"operador_paqueteria", "paqueteria"}, {"mayorista", "mayorista"}, {"operador_mayorista", "mayorista"},
                {"lanube", "lanube"}, {"operador_nube", "lanube"}, {"elbocata", "elbocata"}, {"operador_bocata", "elbocata"}};
        for (String[] caso : casos) {
            Auth.store(app).clear();

            viewModel.loginEnServidor(caso[0], "");

            AuthSession sesion = Auth.store(app).load();
            assertTrue(caso[0], sesion.demo);
            assertEquals(caso[0], caso[1], sesion.restaurantId);
        }
        assertEquals(0, servidor.requests().size());
    }

    @Test
    public void usuario_demo_administrador_abre_el_selector_de_negocio() {
        viewModel.loginEnServidor("admin", "");

        assertEquals(Boolean.TRUE, viewModel.showTenantSelector.getValue());
        assertEquals("admin", Auth.store(app).load().role);
    }

    @Test
    public void cancelarEleccionNegocio_vacia_la_lista_de_negocios() {
        viewModel.cancelarEleccionNegocio();

        assertNull(viewModel.chooseTenant.getValue());
    }

    @Test
    public void elegirNegocio_sin_acceso_pendiente_no_llama_al_servidor() {
        viewModel.elegirNegocio("fruteria");

        assertEquals(0, servidor.requests().size());
    }

    @Test
    public void onCleared_libera_el_ejecutor_y_olvida_las_credenciales_pendientes() {
        viewModel.onCleared();

        viewModel.elegirNegocio("fruteria");
        assertEquals(0, servidor.requests().size());
    }

    @Test
    public void checkSession_con_sesion_sin_negocio_abre_el_selector() {
        guardarSesion(sesionVigente(null));

        viewModel.checkSession(true);

        assertEquals(Boolean.TRUE, viewModel.showTenantSelector.getValue());
        assertNull(viewModel.navigateToMainDirectly.getValue());
    }

    @Test
    public void onBiometricSuccess_con_sesion_sin_negocio_abre_el_selector() {
        guardarSesion(sesionVigente(null));

        viewModel.onBiometricSuccess();

        assertEquals(Boolean.TRUE, viewModel.showTenantSelector.getValue());
        assertNull(viewModel.loginSuccess.getValue());
    }

    @Test
    public void tenantInfo_guarda_id_y_nombre() {
        LoginViewModel.TenantInfo info = new LoginViewModel.TenantInfo("taller", "Taller");

        assertEquals("taller", info.id);
        assertEquals("Taller", info.nombre);
    }

    @Test
    public void checkSession_sin_sesion_no_navega() {
        viewModel.checkSession(true);

        assertNull(viewModel.navigateToMainDirectly.getValue());
        assertNull(viewModel.currentUser.getValue());
    }

    @Test
    public void checkSession_con_sesion_vigente_entra_directo_al_negocio() {
        guardarSesion(sesionVigente("fruteria"));

        viewModel.checkSession(false);

        assertEquals(Boolean.TRUE, viewModel.navigateToMainDirectly.getValue());
        assertEquals("ana", viewModel.currentUser.getValue());
        assertTrue(TenantContext.hasActive(SecurePreferencesHelper.getPrinterPrefs(app)));
    }

    @Test
    public void checkSession_con_refresco_caducado_no_entra() {
        guardarSesion(new AuthSession("at", "rt", 1L, 2L, "ana", "tpv_operator", "fruteria", false));

        viewModel.checkSession(true);

        assertNull(viewModel.navigateToMainDirectly.getValue());
    }

    @Test
    public void onBiometricSuccess_sin_sesion_no_concede_acceso() {
        viewModel.onBiometricSuccess();

        assertNull(viewModel.loginSuccess.getValue());
        assertEquals("La sesión ha caducado: inicia sesión con usuario y contraseña.",
                viewModel.errorMessage.getValue());
    }

    @Test
    public void onBiometricSuccess_con_sesion_vigente_concede_acceso() {
        guardarSesion(sesionVigente("fruteria"));

        viewModel.onBiometricSuccess();

        assertEquals(Boolean.TRUE, viewModel.loginSuccess.getValue());
    }

    @Test
    public void selectTenant_con_id_invalido_muestra_error_sin_cambiar_de_negocio() {
        for (String invalido : new String[]{null, "", "   ", "../fruteria", "negocio con espacios!"}) {
            viewModel.selectTenant(invalido);

            assertEquals("id inválido: '" + invalido + "'", TenantContext.NO_TENANT_MESSAGE, viewModel.errorMessage.getValue());
        }
        assertFalse(TenantContext.hasActive(SecurePreferencesHelper.getPrinterPrefs(app)));
    }

    private static String sesionJson(String token, String restaurantId, String role) {
        String negocio = restaurantId == null ? "null" : "\"" + restaurantId + "\"";
        return "{\"success\":true,\"token\":\"" + token + "\",\"refreshToken\":\"rt_" + token + "\","
                + "\"expiresIn\":1800,\"refreshExpiresAt\":\"2100-01-01T00:00:00.000Z\","
                + "\"user\":{\"username\":\"ana\",\"role\":\"" + role + "\",\"restaurantId\":" + negocio + "}}";
    }

    private static AuthSession sesionVigente(String negocio) {
        long ahora = System.currentTimeMillis();
        return new AuthSession("acceso", "refresco", ahora + 60L * 60L * 1000L, EXPIRACION_REFRESCO_MS,
                "ana", "tpv_operator", negocio, false);
    }

    private void guardarSesion(AuthSession sesion) {
        Auth.store(app).save(sesion);
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), sesion.restaurantId);
    }

    private void apuntarAlServidor(String url) {
        SecurePreferencesHelper.getPrinterPrefs(app).edit().putString("backend_base_url", url).commit();
    }

    private void borrarAlmacenes() {
        Auth.reset();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
    }

    private static int puertoLibre() throws IOException {
        try (ServerSocket temporal = new ServerSocket(0)) {
            return temporal.getLocalPort();
        }
    }

    private static <T> Espera<T> observar(LiveData<T> dato, T esperado) {
        Espera<T> espera = new Espera<>(valor -> valor != null && valor.equals(esperado));
        dato.observeForever(espera);
        return espera;
    }

    private static <T> Espera<T> observarNoNulo(LiveData<T> dato) {
        Espera<T> espera = new Espera<>(valor -> valor != null);
        dato.observeForever(espera);
        return espera;
    }

    /** Observador de un único valor: el ViewModel publica en un hilo de fondo y el test espera a que llegue. */
    private static final class Espera<T> implements Observer<T> {
        private final java.util.function.Predicate<T> cumple;
        private final CountDownLatch recibido = new CountDownLatch(1);
        private volatile T valor;

        Espera(java.util.function.Predicate<T> cumple) {
            this.cumple = cumple;
        }

        @Override
        public void onChanged(T nuevo) {
            if (recibido.getCount() > 0 && cumple.test(nuevo)) {
                valor = nuevo;
                recibido.countDown();
            }
        }

        T esperar() throws InterruptedException {
            assertTrue("el ViewModel no publicó el resultado a tiempo", recibido.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS));
            assertNotNull(valor);
            return valor;
        }
    }
}
