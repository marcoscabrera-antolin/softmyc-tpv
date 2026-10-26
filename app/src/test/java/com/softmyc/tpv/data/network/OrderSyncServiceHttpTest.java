package com.softmyc.tpv.data.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.data.auth.FakeApiServer;
import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.local.TenantContext;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * Tests de caracterización del cliente de pedidos: ruta, método, query, cabeceras y cuerpo de cada petición contra un
 * servidor HTTP real ({@link FakeApiServer}). Fijan el contrato con la API antes y después de refactorizar la construcción.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class OrderSyncServiceHttpTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String TENANT = "lanube";
    private static final String API_KEY = "clave-heredada";
    private static final long TIMEOUT_SECONDS = 5;

    private FakeApiServer server;
    private FakeSettings settings;
    private OrderSyncService service;

    /** Resultado de una petición: código HTTP y cuerpo, o el fallo. */
    private static final class Outcome {
        final int code;
        final IOException failure;

        Outcome(int code, IOException failure) {
            this.code = code;
            this.failure = failure;
        }
    }

    /** Ajustes en memoria. Por defecto: URL de pedidos del servidor de prueba, clave, negocio y sin sesión de usuario. */
    private static final class FakeSettings implements OrderSyncSettings {
        String baseUrl;
        String apiKey = API_KEY;
        String tenant = TENANT;
        boolean userSession;

        @Override public String backendBaseUrl() { return baseUrl; }
        @Override public String apiKey() { return apiKey; }
        @Override public String activeTenant() { return tenant; }
        @Override public boolean hasUserSession() { return userSession; }
    }

    @Before
    public void setUp() throws IOException {
        server = new FakeApiServer();
        settings = new FakeSettings();
        settings.baseUrl = server.baseUrl() + "/api/tpv/orders";
        service = new OrderSyncService(new OkHttpClient(), settings);
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void getOrdersSendsGetToOrdersEndpointWithTenantQueryAndHeaders() throws Exception {
        server.on("/api/tpv/orders", 200, "[]");

        Outcome outcome = await(callback -> service.getOrders(callback));

        assertEquals(200, outcome.code);
        FakeApiServer.Request request = onlyRequest();
        assertEquals("GET", request.method);
        assertEquals("/api/tpv/orders", request.path);
        assertEquals("restaurantId=" + TENANT, request.query);
        assertEquals(API_KEY, request.headers.get("x-api-key"));
        assertEquals(TENANT, request.headers.get("x-tenant-id"));
    }

    @Test
    public void getOrdersWithOverrideTenantUsesThatTenantInQueryAndHeader() throws Exception {
        server.on("/api/tpv/orders", 200, "[]");

        await(callback -> service.getOrders("otro", callback));

        FakeApiServer.Request request = onlyRequest();
        assertEquals("restaurantId=otro", request.query);
        assertEquals("otro", request.headers.get("x-tenant-id"));
    }

    @Test
    public void updateOrderStatusPutsEstadoAndTenantToOrderStatusEndpoint() throws Exception {
        server.on("/api/tpv/orders/ORD-1/status", 200, "{}");

        await(callback -> service.updateOrderStatus("ORD-1", "listo", callback));

        FakeApiServer.Request request = onlyRequest();
        assertEquals("PUT", request.method);
        assertEquals("/api/tpv/orders/ORD-1/status", request.path);
        assertEquals("restaurantId=" + TENANT, request.query);
        assertEquals(TENANT, request.headers.get("x-tenant-id"));
        JSONObject body = new JSONObject(request.body);
        assertEquals("listo", body.getString("estado"));
        assertEquals(TENANT, body.getString("restaurantId"));
    }

    @Test
    public void fetchProductsUsesRootWithoutOrdersSuffixWhenBaseHasPathPrefix() throws Exception {
        settings.baseUrl = server.baseUrl() + "/prefijo/api/tpv/orders";
        server.on("/prefijo/api/tpv/products", 200, "[]");

        await(callback -> service.fetchProducts(TENANT, callback));

        FakeApiServer.Request request = onlyRequest();
        assertEquals("GET", request.method);
        assertEquals("/prefijo/api/tpv/products", request.path);
        assertEquals("restaurantId=" + TENANT, request.query);
        assertEquals(API_KEY, request.headers.get("x-api-key"));
        assertEquals(TENANT, request.headers.get("x-tenant-id"));
    }

    @Test
    public void syncLocalOrdersPostsOrdersArrayWithTenantInBody() throws Exception {
        server.on("/api/tpv/orders/sync", 200, "{}");
        JSONArray orders = new JSONArray(Collections.singletonList(new JSONObject().put("uuid", "v-1")));

        await(callback -> service.syncLocalOrders(TENANT, orders, callback));

        FakeApiServer.Request request = onlyRequest();
        assertEquals("POST", request.method);
        assertEquals("/api/tpv/orders/sync", request.path);
        JSONObject body = new JSONObject(request.body);
        assertEquals(TENANT, body.getString("restaurantId"));
        assertEquals("v-1", body.getJSONArray("orders").getJSONObject(0).getString("uuid"));
        assertEquals(TENANT, request.headers.get("x-tenant-id"));
    }

    @Test
    public void fetchTenantsGetsTenantListWithoutTenantHeader() throws Exception {
        server.on("/api/tpv/tenants", 200, "[]");

        await(callback -> service.fetchTenants(callback));

        FakeApiServer.Request request = onlyRequest();
        assertEquals("GET", request.method);
        assertEquals("/api/tpv/tenants", request.path);
        assertEquals(API_KEY, request.headers.get("x-api-key"));
        assertNull(request.headers.get("x-tenant-id"));
    }

    @Test
    public void fetchAdminRestaurantsSendsBearerWhenTokenIsGiven() throws Exception {
        server.on("/api/admin/restaurants", 200, "[]");

        await(callback -> service.fetchAdminRestaurants("  tok-123 ", callback));

        FakeApiServer.Request request = onlyRequest();
        assertEquals("/api/admin/restaurants", request.path);
        assertEquals("Bearer tok-123", request.headers.get("authorization"));
    }

    @Test
    public void fetchAdminRestaurantsOmitsBearerWhenTokenIsBlank() throws Exception {
        server.on("/api/admin/restaurants", 200, "[]");

        await(callback -> service.fetchAdminRestaurants("", callback));

        assertNull(onlyRequest().headers.get("authorization"));
    }

    @Test
    public void upsertProductPostsProductJsonWithTenantQuery() throws Exception {
        server.on("/api/tpv/products", 200, "{}");
        ProductoLocal producto = new ProductoLocal(7, "Bocata", 3.5, "Bocatas", Arrays.asList("pan", "jamón"));

        await(callback -> service.upsertProduct(producto, callback));

        FakeApiServer.Request request = onlyRequest();
        assertEquals("POST", request.method);
        assertEquals("/api/tpv/products", request.path);
        assertEquals("restaurantId=" + TENANT, request.query);
        JSONObject body = new JSONObject(request.body);
        assertEquals(7, body.getInt("codigo"));
        assertEquals("Bocata", body.getString("nombre"));
        assertEquals(3.5, body.getDouble("precio"), 0.0001);
        assertEquals("Bocatas", body.getString("categoria"));
        assertEquals("pan", body.getJSONArray("ingredientes").getString(0));
        assertEquals("jamón", body.getJSONArray("ingredientes").getString(1));
    }

    @Test
    public void deleteProductSendsDeleteWithCodeInPath() throws Exception {
        server.on("/api/tpv/products/42", 200, "{}");

        await(callback -> service.deleteProduct(42, callback));

        FakeApiServer.Request request = onlyRequest();
        assertEquals("DELETE", request.method);
        assertEquals("/api/tpv/products/42", request.path);
        assertEquals("restaurantId=" + TENANT, request.query);
    }

    @Test
    public void getWaitTimeUsesServerRootWithoutPathPrefix() throws Exception {
        settings.baseUrl = server.baseUrl() + "/prefijo/api/tpv/orders";
        server.on("/api/tpv/config/wait-time", 200, "{}");

        await(callback -> service.getWaitTime(callback));

        FakeApiServer.Request request = onlyRequest();
        assertEquals("GET", request.method);
        assertEquals("/api/tpv/config/wait-time", request.path);
        assertEquals("restaurantId=" + TENANT, request.query);
    }

    @Test
    public void updateWaitTimeSendsBothMinuteKeysAndTenant() throws Exception {
        server.on("/api/tpv/config/wait-time", 200, "{}");

        await(callback -> service.updateWaitTime(15, callback));

        FakeApiServer.Request request = onlyRequest();
        assertEquals("PUT", request.method);
        assertEquals("/api/tpv/config/wait-time", request.path);
        JSONObject body = new JSONObject(request.body);
        assertEquals(15, body.getInt("tiempo_espera"));
        assertEquals(15, body.getInt("tiempoEspera"));
        assertEquals(TENANT, body.getString("restaurantId"));
    }

    @Test
    public void requestWithoutCredentialsFailsWithoutCallingServer() throws Exception {
        settings.apiKey = null;

        Outcome outcome = await(callback -> service.getOrders(callback));

        assertNotNull(outcome.failure);
        assertTrue(outcome.failure.getMessage().startsWith("Sin credenciales"));
        assertTrue(server.requests().isEmpty());
    }

    @Test
    public void requestWithoutActiveTenantFailsWithoutCallingServer() throws Exception {
        settings.tenant = null;

        Outcome outcome = await(callback -> service.getOrders(callback));

        assertEquals(TenantContext.NO_TENANT_MESSAGE, outcome.failure.getMessage());
        assertTrue(server.requests().isEmpty());
    }

    @Test
    public void defaultApiKeyIsSentWhenNoUserSessionIsActive() throws Exception {
        server.on("/api/tpv/orders", 200, "[]");

        await(callback -> service.getOrders(callback));

        assertEquals(API_KEY, onlyRequest().headers.get("x-api-key"));
    }

    @Test
    public void apiKeyIsNotSentWhenRealUserSessionIsActive() throws Exception {
        server.on("/api/tpv/orders", 200, "[]");
        settings.userSession = true;

        await(callback -> service.getOrders(callback));

        assertFalse(onlyRequest().headers.containsKey("x-api-key"));
    }

    @Test
    public void userSessionWithoutApiKeySendsNoEmptyApiKeyHeader() throws Exception {
        server.on("/api/tpv/orders", 200, "[]");
        settings.userSession = true;
        settings.apiKey = null;

        await(callback -> service.getOrders(callback));

        assertFalse(onlyRequest().headers.containsKey("x-api-key"));
    }

    @Test
    public void apiKeyIsNotSentToAdminRestaurantsWhenBearerTokenIsGiven() throws Exception {
        server.on("/api/admin/restaurants", 200, "[]");

        await(callback -> service.fetchAdminRestaurants("tok", callback));

        assertFalse(onlyRequest().headers.containsKey("x-api-key"));
    }

    private FakeApiServer.Request onlyRequest() {
        assertEquals(1, server.requests().size());
        return server.requests().get(0);
    }

    private Outcome await(Consumer<Callback> action) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Outcome> result = new AtomicReference<>();
        action.accept(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                result.set(new Outcome(-1, e));
                done.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    result.set(new Outcome(response.code(), null));
                } finally {
                    response.close();
                    done.countDown();
                }
            }
        });
        assertTrue("La petición no terminó a tiempo", done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        return result.get();
    }
}
