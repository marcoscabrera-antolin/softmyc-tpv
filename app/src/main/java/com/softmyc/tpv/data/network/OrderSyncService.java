package com.softmyc.tpv.data.network;

import android.content.Context;
import android.util.Log;

import com.softmyc.tpv.BuildConfig;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.local.TenantContext;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Cliente de la API de pedidos y productos del servidor. Todas las peticiones se construyen en {@link #send}: una sola
 * validación de credenciales, de negocio y de cabeceras, y un único punto de fallo. Las URLs se derivan en
 * {@link OrderSyncUrls}.
 */
public class OrderSyncService {
    private static final String TAG = "OrderSyncService";
    private static final String DEFAULT_BASE_URL = BuildConfig.DEFAULT_BACKEND_URL + "/api/tpv/orders";
    private static final String NO_CREDENTIALS_MESSAGE = "Sin credenciales: inicia sesión o configura la clave de API en Ajustes";
    private static final String INTERNAL_ERROR_PREFIX = "Error interno al preparar petición: ";
    private static final String HEADER_API_KEY = "x-api-key";
    private static final String HEADER_TENANT = "x-tenant-id";
    private static final String QUERY_RESTAURANT = "restaurantId";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** Mensaje de la petición denegada cuando no hay un negocio válido activo. */
    public static final String NO_TENANT_MESSAGE = TenantContext.NO_TENANT_MESSAGE;

    /** Para las llamadas sin callback (tiempo de espera): solo dejan rastro en el log. */
    private static final Callback LOG_ONLY_CALLBACK = new Callback() {
        @Override
        public void onFailure(Call call, IOException e) {
            Log.e(TAG, "Petición sin callback fallida: " + e.getMessage());
        }

        @Override
        public void onResponse(Call call, Response response) {
            Log.d(TAG, "Petición sin callback respondida. Código HTTP: " + response.code());
            response.close();
        }
    };

    /** Sin contexto no hay ajustes ni sesión: como antes, todas las peticiones fallan por falta de credenciales. */
    private static final OrderSyncSettings NO_SETTINGS = new OrderSyncSettings() {
        @Override public String backendBaseUrl() { return null; }
        @Override public String apiKey() { return null; }
        @Override public String activeTenant() { return null; }
        @Override public boolean hasUserSession() { return false; }
    };

    private final OkHttpClient client;
    private final OrderSyncSettings settings;

    public OrderSyncService() {
        this(null);
    }

    public OrderSyncService(Context context) {
        // Con contexto se usa el cliente compartido: añade el token de la sesión y la renueva ante un 401.
        this(context != null ? Auth.client(context.getApplicationContext()) : new OkHttpClient(),
                context != null ? new ContextOrderSyncSettings(context.getApplicationContext()) : NO_SETTINGS);
    }

    OrderSyncService(OkHttpClient client, OrderSyncSettings settings) {
        this.client = client;
        this.settings = settings;
    }

    /**
     * Normaliza el id de negocio para la API (ver {@link TenantContext#canonicalId}).
     * Devuelve cadena vacía si falta o no es válido: nunca asume un negocio, y como no es un id real
     * nunca coincide con uno (los llamadores antiguos comparan con equals sin comprobar null).
     */
    public static String sanitizeTenantId(String rawTenant) {
        String id = TenantContext.canonicalId(rawTenant);
        return id != null ? id : "";
    }

    public static String cleanUrl(String url) {
        return OrderSyncUrls.cleanUrl(url);
    }

    /** Negocio de la petición: el pedido explícitamente o, si no, el activo. Null si ninguno es válido. */
    private String resolveTenantId(String requestedTenant) {
        boolean requested = requestedTenant != null && !requestedTenant.trim().isEmpty();
        return TenantContext.canonicalId(requested ? requestedTenant : settings.activeTenant());
    }

    private static IOException noTenantError() {
        return new IOException(NO_TENANT_MESSAGE);
    }

    private String getBaseUrl() {
        String url = settings.backendBaseUrl();
        if (url != null && !url.trim().isEmpty()) {
            return OrderSyncUrls.cleanUrl(url);
        }
        return OrderSyncUrls.cleanUrl(DEFAULT_BASE_URL);
    }

    private String getApiKey() {
        String key = settings.apiKey();
        if (key != null && !key.trim().isEmpty()) {
            return key.trim();
        }
        return null;
    }

    /**
     * ¿Hay con qué autenticarse? Una sesión de usuario real (el token lo añade el interceptor) o, durante la transición,
     * la clave de API configurada en Ajustes. Las sesiones de demostración no tienen credenciales para el servidor.
     */
    private boolean hasCredentials(String apiKey) {
        if (apiKey != null) return true;
        return settings.hasUserSession();
    }

    /** URL de pedidos configurada (termina en {@code /orders}, sin query). */
    private HttpUrl ordersUrl() {
        return OrderSyncUrls.parse(getBaseUrl());
    }

    /** Raíz de la API, para colgar el resto de rutas de ella. */
    private HttpUrl apiRoot() {
        return OrderSyncUrls.apiRootOf(ordersUrl());
    }

    /**
     * Cabeceras comunes: el negocio, si la petición lo lleva, y la clave de API heredada solo cuando no hay una sesión
     * de usuario real (SEG-APP-03): con sesión, la identidad va en el Bearer y la clave compartida no viaja.
     */
    private Request.Builder authorized(HttpUrl url, String apiKey, String tenantId) {
        Request.Builder builder = new Request.Builder().url(url);
        if (apiKey != null && !settings.hasUserSession()) {
            builder.addHeader(HEADER_API_KEY, apiKey);
        }
        if (tenantId != null) {
            builder.addHeader(HEADER_TENANT, tenantId);
        }
        return builder;
    }

    private static RequestBody jsonBody(JSONObject json) {
        return RequestBody.create(json.toString(), JSON_MEDIA_TYPE);
    }

    /** Construye una petición con la clave y el negocio ya validados. */
    private interface RequestFactory {
        Request create(String tenantId, String apiKey) throws Exception;
    }

    /**
     * Valida credenciales y, si la petición lo exige, el negocio; construye la petición y la envía. Cualquier fallo
     * llega por {@code callback.onFailure}.
     */
    private void send(String requestedTenant, boolean tenantRequired, Callback callback, RequestFactory factory) {
        Callback target = callback != null ? callback : LOG_ONLY_CALLBACK;
        String apiKey = getApiKey();
        if (!hasCredentials(apiKey)) {
            fail(target, new IOException(NO_CREDENTIALS_MESSAGE));
            return;
        }
        String tenantId = null;
        if (tenantRequired) {
            tenantId = resolveTenantId(requestedTenant);
            if (tenantId == null) {
                fail(target, noTenantError());
                return;
            }
        }
        try {
            Request request = factory.create(tenantId, apiKey);
            Log.d(TAG, request.method() + " " + request.url());
            client.newCall(request).enqueue(target);
        } catch (Exception e) {
            Log.e(TAG, "Error al preparar la petición: " + e.getMessage(), e);
            fail(target, new IOException(INTERNAL_ERROR_PREFIX + e.getMessage()));
        }
    }

    private static void fail(Callback callback, IOException e) {
        // Ningún llamador usa la llamada que recibe en onFailure, así que no hace falta una llamada ficticia.
        callback.onFailure(null, e);
    }

    public void getOrders(Callback callback) {
        getOrders(null, callback);
    }

    public void getOrders(String overrideTenantId, Callback callback) {
        send(overrideTenantId, true, callback, (tenantId, apiKey) -> {
            HttpUrl url = ordersUrl().newBuilder().addQueryParameter(QUERY_RESTAURANT, tenantId).build();
            return authorized(url, apiKey, tenantId).get().build();
        });
    }

    public void updateOrderStatus(String orderId, String newStatus, Callback callback) {
        updateOrderStatus(orderId, newStatus, null, callback);
    }

    public void updateOrderStatus(String orderId, String newStatus, String overrideTenantId, Callback callback) {
        send(overrideTenantId, true, callback, (tenantId, apiKey) -> {
            JSONObject json = new JSONObject();
            json.put("estado", newStatus);
            json.put("restaurantId", tenantId);
            HttpUrl url = ordersUrl().newBuilder()
                    .addPathSegment(orderId)
                    .addPathSegment("status")
                    .addQueryParameter(QUERY_RESTAURANT, tenantId)
                    .build();
            return authorized(url, apiKey, tenantId).put(jsonBody(json)).build();
        });
    }

    public void fetchProducts(String restaurantId, Callback callback) {
        send(restaurantId, true, callback, (tenantId, apiKey) -> {
            HttpUrl url = apiRoot().newBuilder()
                    .addPathSegments("api/tpv/products")
                    .addQueryParameter(QUERY_RESTAURANT, tenantId)
                    .build();
            return authorized(url, apiKey, tenantId).get().build();
        });
    }

    public void syncLocalOrders(String restaurantId, JSONArray ordersArray, Callback callback) {
        send(restaurantId, true, callback, (tenantId, apiKey) -> {
            JSONObject json = new JSONObject();
            json.put("restaurantId", tenantId);
            json.put("orders", ordersArray);
            HttpUrl url = apiRoot().newBuilder().addPathSegments("api/tpv/orders/sync").build();
            return authorized(url, apiKey, tenantId).post(jsonBody(json)).build();
        });
    }

    public void fetchTenants(Callback callback) {
        send(null, false, callback, (tenantId, apiKey) -> {
            HttpUrl url = apiRoot().newBuilder().addPathSegments("api/tpv/tenants").build();
            return authorized(url, apiKey, null).get().build();
        });
    }

    public void fetchAdminRestaurants(String authToken, Callback callback) {
        send(null, false, callback, (tenantId, apiKey) -> {
            HttpUrl url = apiRoot().newBuilder().addPathSegments("api/admin/restaurants").build();
            boolean explicitBearer = authToken != null && !authToken.trim().isEmpty();
            Request.Builder builder = authorized(url, explicitBearer ? null : apiKey, null);
            if (explicitBearer) {
                builder.addHeader("Authorization", "Bearer " + authToken.trim());
            }
            return builder.get().build();
        });
    }

    public void upsertProduct(ProductoLocal producto, Callback callback) {
        send(null, true, callback, (tenantId, apiKey) -> {
            JSONObject json = productJson(producto);
            HttpUrl url = apiRoot().newBuilder()
                    .addPathSegments("api/tpv/products")
                    .addQueryParameter(QUERY_RESTAURANT, tenantId)
                    .build();
            return authorized(url, apiKey, tenantId).post(jsonBody(json)).build();
        });
    }

    private static JSONObject productJson(ProductoLocal producto) throws Exception {
        JSONObject json = new JSONObject();
        json.put("codigo", producto.codigo);
        json.put("nombre", producto.nombre);
        json.put("precio", producto.precio);
        json.put("categoria", producto.categoria);
        JSONArray ingredientsJson = new JSONArray();
        if (producto.ingredientes != null) {
            for (String ing : producto.ingredientes) {
                ingredientsJson.put(ing);
            }
        }
        json.put("ingredientes", ingredientsJson);
        return json;
    }

    public void deleteProduct(int codigo, Callback callback) {
        send(null, true, callback, (tenantId, apiKey) -> {
            HttpUrl url = apiRoot().newBuilder()
                    .addPathSegments("api/tpv/products")
                    .addPathSegment(String.valueOf(codigo))
                    .addQueryParameter(QUERY_RESTAURANT, tenantId)
                    .build();
            return authorized(url, apiKey, tenantId).delete().build();
        });
    }

    public String getApiRootUrl() {
        return OrderSyncUrls.apiRootUrl(getBaseUrl());
    }

    public void getWaitTime(Callback callback) {
        send(null, true, callback, (tenantId, apiKey) -> {
            HttpUrl url = OrderSyncUrls.parse(getApiRootUrl()).newBuilder()
                    .addPathSegments("api/tpv/config/wait-time")
                    .addQueryParameter(QUERY_RESTAURANT, tenantId)
                    .build();
            Log.d(TAG, "Consultando tiempo de espera actual | Tenant: " + tenantId);
            return authorized(url, apiKey, tenantId).get().build();
        });
    }

    public void updateWaitTime(int mins, Callback callback) {
        send(null, true, callback, (tenantId, apiKey) -> {
            JSONObject json = new JSONObject();
            json.put("tiempo_espera", mins);
            json.put("tiempoEspera", mins);
            json.put("restaurantId", tenantId);
            HttpUrl url = OrderSyncUrls.parse(getApiRootUrl()).newBuilder()
                    .addPathSegments("api/tpv/config/wait-time")
                    .build();
            Log.d(TAG, "Sincronizando tiempo de espera estimado | Mins: " + mins + " | Tenant: " + tenantId);
            return authorized(url, apiKey, tenantId).put(jsonBody(json)).build();
        });
    }
}
