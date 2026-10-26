package com.softmyc.tpv.data.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.OkHttpClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class AuthApiTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final String LOGIN_OK = "{\"success\":true,\"token\":\"at_acceso\",\"refreshToken\":\"rt_refresco\","
            + "\"expiresIn\":1800,\"refreshExpiresAt\":\"2026-10-23T12:00:00.000Z\","
            + "\"user\":{\"username\":\"ana\",\"restaurantId\":\"fruteria\",\"role\":\"encargado\"}}";

    private FakeApiServer server;
    private AuthApi api;

    @Before
    public void setUp() throws Exception {
        server = new FakeApiServer();
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build();
        api = new AuthApi(client, () -> NOW);
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void login_success_buildsTheSessionFromTheResponse() {
        server.on("/api/tpv/auth/login", 200, LOGIN_OK);

        AuthApi.Result<AuthSession> r = api.login(server.baseUrl(), "ana", "Clave-Segura-1", null);

        assertTrue(r.ok);
        AuthSession s = r.value;
        assertEquals("at_acceso", s.accessToken);
        assertEquals("rt_refresco", s.refreshToken);
        assertEquals(NOW + 1_800_000L, s.accessExpiresAtMs);
        assertEquals(java.time.Instant.parse("2026-10-23T12:00:00.000Z").toEpochMilli(), s.refreshExpiresAtMs);
        assertEquals("ana", s.username);
        assertEquals("encargado", s.role);
        assertEquals("fruteria", s.restaurantId);
        assertFalse(s.demo);
    }

    @Test
    public void login_sendsUsernamePasswordAndRestaurantAsJson() {
        server.on("/api/tpv/auth/login", 200, LOGIN_OK);

        api.login(server.baseUrl(), "ana", "Clave-Segura-1", "taller");

        FakeApiServer.Request req = server.requests().get(0);
        assertEquals("POST", req.method);
        JsonObject body = JsonParser.parseString(req.body).getAsJsonObject();
        assertEquals("ana", body.get("username").getAsString());
        assertEquals("Clave-Segura-1", body.get("password").getAsString());
        assertEquals("taller", body.get("restaurantId").getAsString());
        assertTrue(req.headers.get("content-type").startsWith("application/json"));
        assertNull("el acceso no lleva Authorization", req.headers.get("authorization"));
    }

    @Test
    public void login_withoutRestaurantDoesNotSendTheField() {
        server.on("/api/tpv/auth/login", 200, LOGIN_OK);

        api.login(server.baseUrl(), "ana", "x", null);

        assertFalse(JsonParser.parseString(server.requests().get(0).body).getAsJsonObject().has("restaurantId"));
    }

    @Test
    public void login_wrongCredentials_is401InvalidCredentials() {
        server.on("/api/tpv/auth/login", 401, "{\"success\":false,\"error\":\"Credenciales inválidas\"}");

        AuthApi.Result<AuthSession> r = api.login(server.baseUrl(), "ana", "mala", null);

        assertFalse(r.ok);
        assertEquals(AuthApi.Error.INVALID_CREDENTIALS, r.error);
    }

    @Test
    public void login_userWithSeveralBusinesses_returnsTheListToChooseFrom() {
        server.on("/api/tpv/auth/login", 400, "{\"success\":false,\"error\":\"Indica el negocio\",\"restaurants\":"
                + "[{\"id\":\"fruteria\",\"nombre\":\"Frutería\"},{\"id\":\"taller\",\"nombre\":\"Taller\"}]}");

        AuthApi.Result<AuthSession> r = api.login(server.baseUrl(), "ana", "x", null);

        assertEquals(AuthApi.Error.CHOOSE_TENANT, r.error);
        assertEquals(2, r.restaurants.size());
        assertEquals("taller", r.restaurants.get(1).id);
        assertEquals("Taller", r.restaurants.get(1).nombre);
    }

    @Test
    public void login_mapsEveryErrorStatus() {
        int[] status = {403, 429, 503, 500, 418};
        AuthApi.Error[] esperado = {AuthApi.Error.FORBIDDEN, AuthApi.Error.RATE_LIMITED, AuthApi.Error.BUSY,
                AuthApi.Error.SERVER, AuthApi.Error.SERVER};
        for (int i = 0; i < status.length; i++) {
            server.on("/api/tpv/auth/login", status[i], "{\"success\":false,\"error\":\"x\"}");
            assertEquals("HTTP " + status[i], esperado[i], api.login(server.baseUrl(), "a", "b", null).error);
        }
    }

    @Test
    public void login_malformedBodyOrMissingToken_isAServerError() {
        server.on("/api/tpv/auth/login", 200, "no es json");
        assertEquals(AuthApi.Error.SERVER, api.login(server.baseUrl(), "a", "b", null).error);
        server.on("/api/tpv/auth/login", 200, "{\"success\":true}");
        assertEquals(AuthApi.Error.SERVER, api.login(server.baseUrl(), "a", "b", null).error);
    }

    @Test
    public void login_serverUnreachable_isANetworkError() throws Exception {
        String url = server.baseUrl();
        server.close();

        AuthApi.Result<AuthSession> r = api.login(url, "ana", "x", null);

        assertEquals(AuthApi.Error.NETWORK, r.error);
        server = new FakeApiServer(); // para el tearDown
    }

    @Test
    public void refresh_keepsTheIdentityAndReplacesTheTokens() {
        server.on("/api/tpv/auth/refresh", 200, "{\"success\":true,\"token\":\"at_nuevo\",\"refreshToken\":\"rt_nuevo\","
                + "\"expiresIn\":1800,\"refreshExpiresAt\":\"2026-10-23T12:00:00.000Z\",\"user\":{\"restaurantId\":\"fruteria\",\"role\":\"encargado\"}}");
        AuthSession previous = new AuthSession("at_viejo", "rt_viejo", 1L, 2L, "ana", "tpv_operator", "fruteria", false);

        AuthApi.Result<AuthSession> r = api.refresh(server.baseUrl(), previous);

        assertTrue(r.ok);
        assertEquals("at_nuevo", r.value.accessToken);
        assertEquals("rt_nuevo", r.value.refreshToken);
        assertEquals("ana", r.value.username);
        assertEquals("encargado", r.value.role);
        assertEquals("el rol lo decide siempre el servidor", "encargado", r.value.role);
        JsonObject body = JsonParser.parseString(server.requests().get(0).body).getAsJsonObject();
        assertEquals("rt_viejo", body.get("refreshToken").getAsString());
    }

    @Test
    public void refresh_rejectedSession_isSessionInvalid() {
        server.on("/api/tpv/auth/refresh", 401, "{\"success\":false,\"error\":\"Sesión no válida\"}");

        AuthApi.Result<AuthSession> r = api.refresh(server.baseUrl(), new AuthSession("a", "r", 1L, 2L, "ana", "x", "y", false));

        assertEquals(AuthApi.Error.SESSION_INVALID, r.error);
    }

    @Test
    public void refresh_networkFailure_isNotConfusedWithAnInvalidSession() throws Exception {
        String url = server.baseUrl();
        server.close();

        assertEquals(AuthApi.Error.NETWORK, api.refresh(url, new AuthSession("a", "r", 1L, 2L, "ana", "x", "y", false)).error);
        server = new FakeApiServer();
    }

    @Test
    public void logout_withAccessToken_sendsItAsBearer() {
        server.on("/api/tpv/auth/logout", 200, "{\"success\":true}");

        AuthApi.Result<Void> r = api.logout(server.baseUrl(), new AuthSession("at_x", "rt_y", 1L, 2L, "ana", "r", "t", false));

        assertTrue(r.ok);
        FakeApiServer.Request req = server.requests().get(0);
        assertEquals("Bearer at_x", req.headers.get("authorization"));
        JsonObject body = JsonParser.parseString(req.body).getAsJsonObject();
        assertEquals("el refresco va también en el cuerpo por si el acceso ya caducó", "rt_y", body.get("refreshToken").getAsString());
    }

    @Test
    public void logout_failure_isReportedSoTheCallerCanRetryLater() {
        server.on("/api/tpv/auth/logout", 503, "{}");
        assertFalse(api.logout(server.baseUrl(), new AuthSession("a", "r", 1L, 2L, "u", "r", "t", false)).ok);
    }

    @Test
    public void rootUrlWithTrailingSlashIsAccepted() {
        server.on("/api/tpv/auth/login", 200, LOGIN_OK);
        assertNotNull(api.login(server.baseUrl() + "/", "ana", "x", null).value);
    }

    @Test
    public void switchRestaurant_sendsTheBearerAndBuildsTheNewSession() {
        server.on("/api/admin/switch-restaurant", 200, "{\"success\":true,\"token\":\"at_taller\",\"refreshToken\":\"rt_taller\","
                + "\"expiresIn\":1800,\"refreshExpiresAt\":\"2026-10-23T12:00:00.000Z\",\"user\":{\"restaurantId\":\"taller\",\"role\":\"admin\"}}");
        AuthSession admin = new AuthSession("at_admin", "rt_admin", NOW + 1000, NOW + 100000, "root", "admin", null, false);

        AuthApi.Result<AuthSession> r = api.switchRestaurant(server.baseUrl(), admin, "taller");

        assertTrue(r.ok);
        assertEquals("at_taller", r.value.accessToken);
        assertEquals("taller", r.value.restaurantId);
        assertEquals("root", r.value.username);
        FakeApiServer.Request req = server.requests().get(0);
        assertEquals("Bearer at_admin", req.headers.get("authorization"));
        assertEquals("taller", JsonParser.parseString(req.body).getAsJsonObject().get("restaurantId").getAsString());
    }

    @Test
    public void switchRestaurant_forbiddenBusiness_isForbidden() {
        server.on("/api/admin/switch-restaurant", 403, "{\"success\":false,\"error\":\"No tienes acceso a ese negocio\"}");

        AuthApi.Result<AuthSession> r = api.switchRestaurant(server.baseUrl(), new AuthSession("a", "r", 1L, 2L, "u", "admin", null, false), "x");

        assertEquals(AuthApi.Error.FORBIDDEN, r.error);
    }

    @Test
    public void refresh_withoutRestaurantInTheResponse_keepsThePreviousBusiness() {
        server.on("/api/tpv/auth/refresh", 200, "{\"success\":true,\"token\":\"at_n\",\"refreshToken\":\"rt_n\",\"expiresIn\":1800,"
                + "\"refreshExpiresAt\":\"2026-10-23T12:00:00.000Z\",\"user\":{\"role\":\"admin\"}}");
        AuthSession admin = new AuthSession("a", "r", 1L, 2L, "root", "admin", "taller", false);

        assertEquals("taller", api.refresh(server.baseUrl(), admin).value.restaurantId);
    }

    @Test
    public void login_withoutRestaurant_leavesItNull_forThePlatformAdmin() {
        server.on("/api/tpv/auth/login", 200, "{\"success\":true,\"token\":\"at_a\",\"refreshToken\":\"rt_a\",\"expiresIn\":1800,"
                + "\"refreshExpiresAt\":\"2026-10-23T12:00:00.000Z\",\"user\":{\"username\":\"root\",\"restaurantId\":null,\"role\":\"admin\"}}");

        assertNull(api.login(server.baseUrl(), "root", "x", null).value.restaurantId);
    }

    @Test
    public void everySessionRemembersTheServerThatIssuedIt() {
        server.on("/api/tpv/auth/login", 200, LOGIN_OK);

        assertEquals(server.baseUrl(), api.login(server.baseUrl() + "/", "ana", "x", null).value.issuer);
    }
}
