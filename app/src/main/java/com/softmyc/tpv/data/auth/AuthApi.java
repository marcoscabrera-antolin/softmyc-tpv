package com.softmyc.tpv.data.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Cliente de la API de acceso del servidor (login, refresco y cierre de sesión). Las llamadas son síncronas y deben
 * hacerse fuera del hilo de la interfaz. Se construye con un cliente HTTP sin interceptores de autenticación, para no
 * entrar en un bucle al renovar el token.
 */
public final class AuthApi {

    public enum Error {
        /** Sin conexión o tiempo de espera agotado: no dice nada sobre la validez de la sesión. */
        NETWORK,
        INVALID_CREDENTIALS,
        /** El servidor rechaza el refresco: la sesión ya no sirve y hay que volver a iniciar sesión. */
        SESSION_INVALID,
        FORBIDDEN,
        /** El usuario tiene varios negocios y debe elegir uno (ver {@link Result#restaurants}). */
        CHOOSE_TENANT,
        RATE_LIMITED,
        BUSY,
        SERVER
    }

    public static final class Restaurant {
        public final String id;
        public final String nombre;

        Restaurant(String id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }
    }

    public static final class Result<T> {
        public final boolean ok;
        public final T value;
        public final Error error;
        public final String message;
        public final List<Restaurant> restaurants;

        private Result(boolean ok, T value, Error error, String message, List<Restaurant> restaurants) {
            this.ok = ok;
            this.value = value;
            this.error = error;
            this.message = message;
            this.restaurants = restaurants;
        }

        static <T> Result<T> success(T value) {
            return new Result<>(true, value, null, null, Collections.emptyList());
        }

        static <T> Result<T> failure(Error error, String message) {
            return new Result<>(false, null, error, message, Collections.emptyList());
        }

        static <T> Result<T> chooseTenant(String message, List<Restaurant> restaurants) {
            return new Result<>(false, null, Error.CHOOSE_TENANT, message, restaurants);
        }
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final LongSupplier clock;

    public AuthApi(OkHttpClient client, LongSupplier clock) {
        this.client = client;
        this.clock = clock;
    }

    public Result<AuthSession> login(String root, String username, String password, String restaurantId) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        if (restaurantId != null && !restaurantId.isEmpty()) body.addProperty("restaurantId", restaurantId);
        return send(root, "/api/tpv/auth/login", body, null, null, true);
    }

    public Result<AuthSession> refresh(String root, AuthSession previous) {
        JsonObject body = new JsonObject();
        body.addProperty("refreshToken", previous.refreshToken);
        return send(root, "/api/tpv/auth/refresh", body, null, previous, false);
    }

    /** Administrador: abre una sesión para otro negocio (la anterior queda revocada en el servidor). */
    public Result<AuthSession> switchRestaurant(String root, AuthSession current, String restaurantId) {
        JsonObject body = new JsonObject();
        body.addProperty("restaurantId", restaurantId);
        return send(root, "/api/admin/switch-restaurant", body, current.accessToken, current, false);
    }

    /** Cierra la sesión en el servidor con el acceso y, por si ya caducó, con el refresco en el cuerpo. */
    public Result<Void> logout(String root, AuthSession session) {
        JsonObject body = new JsonObject();
        body.addProperty("refreshToken", session.refreshToken);
        Request request = new Request.Builder().url(url(root, "/api/tpv/auth/logout"))
                .header("Authorization", "Bearer " + session.accessToken)
                .post(RequestBody.create(body.toString(), JSON)).build();
        try (Response r = client.newCall(request).execute()) {
            return r.isSuccessful() ? Result.success(null) : Result.failure(mapStatus(r.code(), false), "HTTP " + r.code());
        } catch (IOException e) {
            return Result.failure(Error.NETWORK, e.getMessage());
        }
    }

    private Result<AuthSession> send(String root, String path, JsonObject body, String bearer, AuthSession previous, boolean isLogin) {
        Request.Builder rb = new Request.Builder().url(url(root, path)).post(RequestBody.create(body.toString(), JSON));
        if (bearer != null) rb.header("Authorization", "Bearer " + bearer);
        try (Response r = client.newCall(rb.build()).execute()) {
            String text = r.body() != null ? r.body().string() : "";
            if (r.isSuccessful()) return parseSession(text, previous, issuerOf(root));
            JsonObject json = asObject(text);
            String message = json != null && json.has("error") ? json.get("error").getAsString() : "HTTP " + r.code();
            if (r.code() == 400 && json != null && json.has("restaurants") && json.get("restaurants").isJsonArray()) {
                return Result.chooseTenant(message, parseRestaurants(json.getAsJsonArray("restaurants")));
            }
            return Result.failure(mapStatus(r.code(), isLogin), message);
        } catch (IOException e) {
            return Result.failure(Error.NETWORK, e.getMessage());
        }
    }

    private Result<AuthSession> parseSession(String text, AuthSession previous, String issuer) {
        JsonObject json = asObject(text);
        try {
            if (json == null || !json.has("token") || !json.has("refreshToken")) {
                return Result.failure(Error.SERVER, "Respuesta inválida del servidor");
            }
            JsonObject user = json.has("user") && json.get("user").isJsonObject() ? json.getAsJsonObject("user") : new JsonObject();
            long accessExp = clock.getAsLong() + json.get("expiresIn").getAsLong() * 1000L;
            long refreshExp = Instant.parse(json.get("refreshExpiresAt").getAsString()).toEpochMilli();
            String username = user.has("username") ? user.get("username").getAsString() : previous != null ? previous.username : null;
            String role = user.has("role") ? user.get("role").getAsString() : previous != null ? previous.role : null;
            // El negocio que no llega en la respuesta se conserva (un administrador que ya eligió uno no debe perderlo al renovar).
            String tenant = user.has("restaurantId") && !user.get("restaurantId").isJsonNull()
                    ? user.get("restaurantId").getAsString() : previous != null ? previous.restaurantId : null;
            return Result.success(new AuthSession(json.get("token").getAsString(), json.get("refreshToken").getAsString(),
                    accessExp, refreshExp, username, role, tenant, false, issuer));
        } catch (RuntimeException e) {
            return Result.failure(Error.SERVER, "Respuesta inválida del servidor");
        }
    }

    private static List<Restaurant> parseRestaurants(JsonArray array) {
        List<Restaurant> list = new ArrayList<>();
        for (JsonElement e : array) {
            JsonObject o = e.getAsJsonObject();
            list.add(new Restaurant(o.get("id").getAsString(), o.has("nombre") ? o.get("nombre").getAsString() : o.get("id").getAsString()));
        }
        return list;
    }

    private static Error mapStatus(int code, boolean isLogin) {
        switch (code) {
            case 401: return isLogin ? Error.INVALID_CREDENTIALS : Error.SESSION_INVALID;
            case 403: return Error.FORBIDDEN;
            case 429: return Error.RATE_LIMITED;
            case 503: return Error.BUSY;
            default: return Error.SERVER;
        }
    }

    private static JsonObject asObject(String text) {
        try {
            JsonElement e = JsonParser.parseString(text);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String issuerOf(String root) {
        return root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
    }

    private static String url(String root, String path) {
        String r = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        return r + path;
    }
}
