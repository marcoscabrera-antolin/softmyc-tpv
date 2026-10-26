package com.softmyc.tpv.data.auth;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import okhttp3.HttpUrl;

/**
 * Punto único para iniciar, renovar y cerrar la sesión. La renovación es de una sola vez para todos: como el token de
 * refresco es rotatorio y de un solo uso, si varias peticiones fallan a la vez con un 401 solo una llama al servidor y
 * las demás reutilizan el resultado. Los métodos de red deben llamarse fuera del hilo de la interfaz.
 */
public final class SessionManager {

    /** Avisos para el resto de la aplicación (cerrar pantallas, actualizar el rol y el negocio activos). */
    public interface Listener {
        /** El servidor rechazó el refresco o este caducó: hay que volver a iniciar sesión. */
        void onSessionExpired();

        /** La sesión se guardó o se renovó (el rol o el negocio pueden haber cambiado). */
        void onSessionChanged(AuthSession session);
    }

    private static final long REFRESH_MARGIN_MS = 30_000L;
    private static final String AUTH_PATH_PREFIX = "/api/tpv/auth/";

    private final SessionStore store;
    private final AuthApi api;
    private final Supplier<String> rootUrl;
    private final LongSupplier clock;
    private final Object lock = new Object();
    private volatile Listener listener;

    public SessionManager(SessionStore store, AuthApi api, Supplier<String> rootUrl, LongSupplier clock) {
        this.store = store;
        this.api = api;
        this.rootUrl = rootUrl;
        this.clock = clock;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public AuthSession current() {
        return store.load();
    }

    public String rootUrl() {
        return rootUrl.get();
    }

    /** Inicia sesión; si va bien, guarda la sesión. Un fallo no toca la sesión que hubiera. */
    public AuthApi.Result<AuthSession> login(String username, String password, String restaurantId) {
        AuthApi.Result<AuthSession> r = api.login(rootUrl.get(), username, password, restaurantId);
        if (r.ok) publish(r.value);
        return r;
    }

    /** Guarda una sesión obtenida por otra vía (p. ej. el cambio de negocio de un administrador). */
    public void adopt(AuthSession session) {
        publish(session);
    }

    /**
     * Cierra la sesión: primero se borra en local (siempre, aunque no haya red) y después se intenta revocar en el servidor.
     */
    public void logout() {
        AuthSession s = store.load();
        store.clear();
        notifyChanged(null);
        revokeRemote(s);
    }

    /** Revoca una sesión en el servidor que la emitió (mejor esfuerzo): sirve para cerrar la sesión local de inmediato y avisar después. */
    public void revokeRemote(AuthSession session) {
        if (session != null && !session.demo) api.logout(rootOf(session), session);
    }

    /** Raíz a la que hay que hablar para esta sesión: la del servidor que la emitió, o la configurada si no consta. */
    private String rootOf(AuthSession s) {
        return s != null && s.issuer != null ? s.issuer : rootUrl.get();
    }

    /** Cierra la sesión solo en local (sin avisar al servidor). */
    public void clearLocal() {
        store.clear();
    }

    /** Administrador: cambia de negocio. Si el servidor lo rechaza, la sesión actual no se toca. */
    public AuthApi.Result<AuthSession> switchRestaurant(String restaurantId) {
        if (accessTokenForRequest() == null) return AuthApi.Result.failure(AuthApi.Error.SESSION_INVALID, "No hay sesión activa");
        AuthSession current = store.load();
        AuthApi.Result<AuthSession> r = api.switchRestaurant(rootOf(current), current, restaurantId);
        if (r.ok) publish(r.value);
        return r;
    }

    /**
     * Token de acceso a enviar con una petición, renovándolo antes si ya caducó. Null si no hay sesión utilizable
     * (no hay, es de demostración o caducó el refresco).
     */
    public String accessTokenForRequest() {
        AuthSession s = store.load();
        if (s == null || s.demo) return null;
        long now = clock.getAsLong();
        if (now >= s.refreshExpiresAtMs) {
            expire();
            return null;
        }
        if (now >= s.accessExpiresAtMs - REFRESH_MARGIN_MS) {
            AuthSession renewed = refreshIfStale(s.accessToken);
            return renewed != null ? renewed.accessToken : null;
        }
        return s.accessToken;
    }

    /**
     * Renueva la sesión si el acceso que se usó ({@code staleAccessToken}) sigue siendo el guardado. Si otro hilo ya lo
     * renovó, devuelve la sesión actual sin llamar al servidor. Devuelve la sesión a usar (la misma si no se pudo renovar
     * por falta de red) o null si la sesión terminó.
     */
    public AuthSession refreshIfStale(String staleAccessToken) {
        synchronized (lock) {
            AuthSession s = store.load();
            if (s == null || s.demo) return s;
            if (!s.accessToken.equals(staleAccessToken)) return s;
            if (clock.getAsLong() >= s.refreshExpiresAtMs) {
                expire();
                return null;
            }
            AuthApi.Result<AuthSession> r = api.refresh(rootOf(s), s);
            if (r.ok) {
                // Si mientras tanto se cerró la sesión (o la renovó otro hilo), este resultado ya no vale.
                if (store.saveIfCurrent(s.accessToken, r.value)) {
                    notifyChanged(r.value);
                    return r.value;
                }
                return store.load();
            }
            if (r.error == AuthApi.Error.SESSION_INVALID) {
                expire();
                return null;
            }
            return s; // sin red o servidor ocupado: la sesión no se pierde
        }
    }

    /** ¿La petición va dirigida al servidor configurado? El token nunca se envía a otro servidor. */
    public boolean isApiRequest(HttpUrl url) {
        HttpUrl root = HttpUrl.parse(rootOf(store.load()));
        return root != null && root.host().equalsIgnoreCase(url.host()) && root.port() == url.port()
                && root.scheme().equals(url.scheme());
    }

    public static boolean isAuthEndpoint(HttpUrl url) {
        return url.encodedPath().startsWith(AUTH_PATH_PREFIX);
    }

    private void publish(AuthSession session) {
        store.save(session);
        notifyChanged(session);
    }

    private void expire() {
        store.clear();
        notifyChanged(null);
        Listener l = listener;
        if (l != null) l.onSessionExpired();
    }

    private void notifyChanged(AuthSession session) {
        Listener l = listener;
        if (l != null && session != null) l.onSessionChanged(session);
    }
}
