package com.softmyc.tpv.data.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.data.InMemorySharedPreferences;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class AuthHttpTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final String REFRESH_OK = "{\"success\":true,\"token\":\"at_nuevo\",\"refreshToken\":\"rt_nuevo\","
            + "\"expiresIn\":1800,\"refreshExpiresAt\":\"2027-10-23T12:00:00.000Z\",\"user\":{\"restaurantId\":\"fruteria\",\"role\":\"encargado\"}}";

    private FakeApiServer server;
    private final AtomicLong clock = new AtomicLong(NOW);
    private SessionStore store;
    private SessionManager manager;
    private OkHttpClient client;
    private final List<String> expired = new ArrayList<>();
    private final List<AuthSession> mirrored = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        server = new FakeApiServer();
        store = new SessionStore(new InMemorySharedPreferences());
        OkHttpClient plain = new OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).build();
        manager = new SessionManager(store, new AuthApi(plain, clock::get), () -> server.baseUrl(), clock::get);
        manager.setListener(new SessionManager.Listener() {
            @Override public void onSessionExpired() { expired.add("caducada"); }
            @Override public void onSessionChanged(AuthSession s) { mirrored.add(s); }
        });
        client = plain.newBuilder().addInterceptor(new AuthInterceptor(manager)).authenticator(new TokenAuthenticator(manager)).build();
    }

    @After
    public void tearDown() {
        server.close();
    }

    /** Responde 401 al token viejo y cuenta la petición: el test espera a que lleguen todas antes de liberar el refresco. */
    private static FakeApiServer.Reply rechazarYContar(CountDownLatch rechazos) {
        rechazos.countDown();
        return new FakeApiServer.Reply(401, "{\"success\":false}");
    }

    /** Espera a un latch dentro del servidor falso (hilo de atención, no el del test). */
    private static void esperar(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sessionWith(String access, long accessExp, long refreshExp) {
        store.save(new AuthSession(access, "rt_viejo", accessExp, refreshExp, "ana", "tpv_operator", "fruteria", false));
    }

    private int get(String path) throws Exception {
        try (Response r = client.newCall(new Request.Builder().url(server.baseUrl() + path).build()).execute()) {
            return r.code();
        }
    }

    /** /api/tpv/products devuelve 200 solo con el token válido; con cualquier otro, 401. */
    private void protectedEndpoint(String validToken) {
        server.on("/api/tpv/products", req -> ("Bearer " + validToken).equals(req.headers.get("authorization"))
                ? new FakeApiServer.Reply(200, "{\"success\":true}") : new FakeApiServer.Reply(401, "{\"success\":false}"));
    }

    @Test
    public void interceptor_addsTheBearerToTheApiRequests() throws Exception {
        sessionWith("at_vigente", NOW + 20 * 60_000L, NOW + 7 * DAY);
        protectedEndpoint("at_vigente");

        assertEquals(200, get("/api/tpv/products"));
        assertEquals("Bearer at_vigente", server.requests().get(0).headers.get("authorization"));
    }

    @Test
    public void interceptor_doesNotAttachTheTokenToTheLoginAndRefreshEndpoints() throws Exception {
        sessionWith("at_vigente", NOW + 20 * 60_000L, NOW + 7 * DAY);
        server.on("/api/tpv/auth/login", 401, "{}");

        get("/api/tpv/auth/login");

        assertNull(server.requests().get(0).headers.get("authorization"));
    }

    @Test
    public void interceptor_neverSendsTheTokenToAnotherHost() throws Exception {
        sessionWith("at_vigente", NOW + 20 * 60_000L, NOW + 7 * DAY);
        try (FakeApiServer other = new FakeApiServer()) {
            other.on("/api/tpv/products", 200, "{}");
            try (Response r = client.newCall(new Request.Builder().url(other.baseUrl() + "/api/tpv/products").build()).execute()) {
                assertEquals(200, r.code());
            }
            assertNull("un host distinto del configurado no recibe el token", other.requests().get(0).headers.get("authorization"));
        }
    }

    @Test
    public void withoutSession_theRequestGoesOutUnauthenticated() throws Exception {
        protectedEndpoint("at_x");

        assertEquals(401, get("/api/tpv/products"));
        assertNull(server.requests().get(0).headers.get("authorization"));
        assertEquals(0, server.countOf("/api/tpv/auth/refresh"));
    }

    @Test
    public void unauthorized_refreshesOnceAndRetriesWithTheNewToken() throws Exception {
        sessionWith("at_viejo", NOW + 20 * 60_000L, NOW + 7 * DAY); // el servidor ya lo ha revocado
        protectedEndpoint("at_nuevo");
        server.on("/api/tpv/auth/refresh", 200, REFRESH_OK);

        assertEquals(200, get("/api/tpv/products"));

        assertEquals(1, server.countOf("/api/tpv/auth/refresh"));
        assertEquals(2, server.countOf("/api/tpv/products"));
        assertEquals("at_nuevo", store.load().accessToken);
        assertEquals("rt_nuevo", store.load().refreshToken);
        assertEquals("el servidor puede cambiar el rol", "encargado", store.load().role);
        assertEquals(1, mirrored.size());
    }

    @Test
    public void manyConcurrentUnauthorizedRequests_shareASingleRefresh() throws Exception {
        final int peticiones = 8;
        sessionWith("at_viejo", NOW + 20 * 60_000L, NOW + 7 * DAY);
        CountDownLatch rechazosRecibidos = new CountDownLatch(peticiones);
        CountDownLatch liberarRefresco = new CountDownLatch(1);
        CountDownLatch refrescoEnVuelo = new CountDownLatch(1);
        server.on("/api/tpv/products", req -> "Bearer at_nuevo".equals(req.headers.get("authorization"))
                ? new FakeApiServer.Reply(200, "{\"success\":true}")
                : rechazarYContar(rechazosRecibidos));
        server.on("/api/tpv/auth/refresh", req -> {
            refrescoEnVuelo.countDown();
            esperar(liberarRefresco);
            return new FakeApiServer.Reply(200, REFRESH_OK);
        });
        ExecutorService pool = Executors.newFixedThreadPool(peticiones);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < peticiones; i++) {
            results.add(pool.submit(() -> { start.await(); return get("/api/tpv/products"); }));
        }
        start.countDown();
        // Todas las peticiones tienen que recibir su 401 antes de que el refresco pueda terminar: así todas
        // comparten la misma renovación en vuelo y ninguna la repite con el token ya rotado.
        assertTrue("las peticiones no llegaron con el token viejo", rechazosRecibidos.await(10, TimeUnit.SECONDS));
        assertTrue("el refresco no llegó al servidor", refrescoEnVuelo.await(10, TimeUnit.SECONDS));
        liberarRefresco.countDown();

        for (Future<Integer> f : results) assertEquals(Integer.valueOf(200), f.get(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals("el refresco rotatorio es de un solo uso: una única renovación", 1, server.countOf("/api/tpv/auth/refresh"));
    }

    @Test
    public void refreshRejectedByTheServer_clearsTheSessionAndNotifiesOnce() throws Exception {
        sessionWith("at_viejo", NOW + 20 * 60_000L, NOW + 7 * DAY);
        protectedEndpoint("at_nuevo");
        server.on("/api/tpv/auth/refresh", 401, "{\"success\":false}");

        assertEquals(401, get("/api/tpv/products"));

        assertNull(store.load());
        assertEquals(1, expired.size());
    }

    @Test
    public void refreshFailingForLackOfNetwork_keepsTheSession() throws Exception {
        sessionWith("at_viejo", NOW + 20 * 60_000L, NOW + 7 * DAY);
        protectedEndpoint("at_nuevo");
        server.on("/api/tpv/auth/refresh", 503, "{}");

        assertEquals(401, get("/api/tpv/products"));

        assertNotNull("un fallo de red o del servidor no cierra la sesión", store.load());
        assertTrue(expired.isEmpty());
    }

    @Test
    public void expiredAccessToken_isRenewedBeforeSendingTheRequest() throws Exception {
        sessionWith("at_viejo", NOW - 60_000L, NOW + 7 * DAY);
        protectedEndpoint("at_nuevo");
        server.on("/api/tpv/auth/refresh", 200, REFRESH_OK);

        assertEquals(200, get("/api/tpv/products"));

        assertEquals("sin viaje de ida y vuelta con un 401", 1, server.countOf("/api/tpv/products"));
        assertEquals(1, server.countOf("/api/tpv/auth/refresh"));
    }

    @Test
    public void expiredRefreshToken_isNotSentToTheServerAndNotifiesTheListener() throws Exception {
        sessionWith("at_viejo", NOW - 8 * DAY, NOW - 1_000L);
        protectedEndpoint("at_nuevo");

        assertEquals(401, get("/api/tpv/products"));

        assertEquals(0, server.countOf("/api/tpv/auth/refresh"));
        assertEquals(1, expired.size());
        assertNull(store.load());
    }

    @Test
    public void demoSession_neverTouchesTheNetwork() throws Exception {
        store.save(AuthSession.demo("fruteria", "tpv_operator", "Operador", NOW));
        protectedEndpoint("at_nuevo");

        assertEquals(401, get("/api/tpv/products"));

        assertNull(server.requests().get(0).headers.get("authorization"));
        assertEquals(0, server.countOf("/api/tpv/auth/refresh"));
        assertNotNull(store.load());
    }

    @Test
    public void login_savesTheSessionAndNotifiesTheListener() {
        server.on("/api/tpv/auth/login", 200, "{\"success\":true,\"token\":\"at_a\",\"refreshToken\":\"rt_a\",\"expiresIn\":1800,"
                + "\"refreshExpiresAt\":\"2027-10-23T12:00:00.000Z\",\"user\":{\"username\":\"ana\",\"restaurantId\":\"taller\",\"role\":\"admin\"}}");

        AuthApi.Result<AuthSession> r = manager.login("ana", "x", "taller");

        assertTrue(r.ok);
        assertEquals("at_a", store.load().accessToken);
        assertEquals("taller", store.load().restaurantId);
        assertEquals(1, mirrored.size());
    }

    @Test
    public void failedLogin_doesNotTouchTheStoredSession() {
        sessionWith("at_previa", NOW + 60_000L, NOW + 7 * DAY);
        server.on("/api/tpv/auth/login", 401, "{}");

        assertFalse(manager.login("ana", "mala", null).ok);

        assertEquals("at_previa", store.load().accessToken);
    }

    @Test
    public void logout_clearsLocallyFirstAndRevokesOnTheServer() {
        sessionWith("at_x", NOW + 60_000L, NOW + 7 * DAY);
        server.on("/api/tpv/auth/logout", 200, "{\"success\":true}");

        manager.logout();

        assertNull(store.load());
        assertEquals(1, server.countOf("/api/tpv/auth/logout"));
        assertEquals("Bearer at_x", server.requests().get(0).headers.get("authorization"));
    }

    @Test
    public void logout_withoutNetwork_stillEndsTheLocalSession() throws Exception {
        sessionWith("at_x", NOW + 60_000L, NOW + 7 * DAY);
        server.close();

        manager.logout();

        assertNull(store.load());
        server = new FakeApiServer();
    }

    @Test
    public void interceptor_dropsAnEmptyApiKeyHeaderButKeepsARealOne() throws Exception {
        sessionWith("at_vigente", NOW + 20 * 60_000L, NOW + 7 * DAY);
        protectedEndpoint("at_vigente");
        for (String key : new String[]{"", "tfg_clave_real"}) {
            try (Response r = client.newCall(new Request.Builder().url(server.baseUrl() + "/api/tpv/products").header("x-api-key", key).build()).execute()) {
                assertEquals(200, r.code());
            }
        }
        assertNull("una clave vacía no debe viajar", server.requests().get(0).headers.get("x-api-key"));
        assertEquals("tfg_clave_real", server.requests().get(1).headers.get("x-api-key"));
    }

    @Test
    public void switchRestaurant_adoptsTheNewSession() {
        sessionWith("at_admin", NOW + 20 * 60_000L, NOW + 7 * DAY);
        server.on("/api/admin/switch-restaurant", 200, "{\"success\":true,\"token\":\"at_taller\",\"refreshToken\":\"rt_taller\","
                + "\"expiresIn\":1800,\"refreshExpiresAt\":\"2027-10-23T12:00:00.000Z\",\"user\":{\"restaurantId\":\"taller\",\"role\":\"admin\"}}");

        AuthApi.Result<AuthSession> r = manager.switchRestaurant("taller");

        assertTrue(r.ok);
        assertEquals("at_taller", store.load().accessToken);
        assertEquals("taller", store.load().restaurantId);
        assertEquals(1, mirrored.size());
    }

    @Test
    public void switchRestaurant_withoutSession_failsWithoutCallingTheServer() {
        AuthApi.Result<AuthSession> r = manager.switchRestaurant("taller");

        assertFalse(r.ok);
        assertEquals(AuthApi.Error.SESSION_INVALID, r.error);
        assertEquals(0, server.requests().size());
    }

    @Test
    public void switchRestaurant_serverRejects_keepsTheCurrentSession() {
        sessionWith("at_admin", NOW + 20 * 60_000L, NOW + 7 * DAY);
        server.on("/api/admin/switch-restaurant", 403, "{}");

        assertFalse(manager.switchRestaurant("no-permitido").ok);

        assertEquals("at_admin", store.load().accessToken);
    }

    @Test
    public void logoutWhileARefreshIsInFlight_doesNotResurrectTheSession() throws Exception {
        sessionWith("at_viejo", NOW + 20 * 60_000L, NOW + 7 * DAY);
        CountDownLatch refrescoEnVuelo = new CountDownLatch(1);
        CountDownLatch liberarRefresco = new CountDownLatch(1);
        server.on("/api/tpv/auth/refresh", req -> {
            refrescoEnVuelo.countDown();
            esperar(liberarRefresco);
            return new FakeApiServer.Reply(200, REFRESH_OK);
        });
        Thread refresh = new Thread(() -> manager.refreshIfStale("at_viejo"));
        refresh.start();
        assertTrue("el refresco no llegó al servidor", refrescoEnVuelo.await(10, TimeUnit.SECONDS));

        store.clear();            // el usuario cierra sesión mientras el refresco está en vuelo
        liberarRefresco.countDown();
        refresh.join(5_000);

        assertNull("el refresco que termina después no puede reescribir los tokens", store.load());
    }

    @Test
    public void sessionOnlyTalksToTheServerThatIssuedIt() throws Exception {
        String emisor = server.baseUrl();
        store.save(new AuthSession("at_a", "rt_a", NOW + 20 * 60_000L, NOW + 7 * DAY, "ana", "tpv_operator", "fruteria", false, emisor));
        protectedEndpoint("at_a");
        try (FakeApiServer otro = new FakeApiServer()) {
            otro.on("/api/tpv/products", 200, "{}");
            // Ajustes pasa a apuntar a otro servidor con la sesión abierta
            SessionManager apuntandoAOtro = new SessionManager(store, new AuthApi(new OkHttpClient(), clock::get), otro::baseUrl, clock::get);
            OkHttpClient c = new OkHttpClient.Builder().addInterceptor(new AuthInterceptor(apuntandoAOtro)).build();
            try (Response r = c.newCall(new Request.Builder().url(otro.baseUrl() + "/api/tpv/products").build()).execute()) {
                assertEquals(200, r.code());
            }
            assertNull("el servidor nuevo no recibe el token", otro.requests().get(0).headers.get("authorization"));
            try (Response r = c.newCall(new Request.Builder().url(emisor + "/api/tpv/products").build()).execute()) {
                assertEquals(200, r.code());
            }
            assertEquals("Bearer at_a", server.requests().get(0).headers.get("authorization"));
        }
    }

    @Test
    public void refreshGoesToTheIssuerEvenIfTheConfiguredUrlChanged() throws Exception {
        store.save(new AuthSession("at_a", "rt_a", NOW - 1000L, NOW + 7 * DAY, "ana", "tpv_operator", "fruteria", false, server.baseUrl()));
        server.on("/api/tpv/auth/refresh", 200, REFRESH_OK);
        try (FakeApiServer otro = new FakeApiServer()) {
            SessionManager apuntandoAOtro = new SessionManager(store, new AuthApi(new OkHttpClient(), clock::get), otro::baseUrl, clock::get);

            apuntandoAOtro.refreshIfStale("at_a");

            assertEquals(1, server.countOf("/api/tpv/auth/refresh"));
            assertEquals("el refresco, de un solo uso, no viaja al servidor nuevo", 0, otro.requests().size());
            assertEquals(server.baseUrl(), store.load().issuer);
        }
    }
}
