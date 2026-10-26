package com.softmyc.tpv.data.auth;

/**
 * Sesión de usuario emitida por la API: token de acceso (corta duración), token de refresco (larga duración) y la
 * identidad que el servidor ha asignado (usuario, rol y negocio). Es inmutable: renovar o cambiar de negocio crea otra.
 */
public final class AuthSession {
    private static final long DEMO_LIFETIME_MS = 7L * 24 * 60 * 60 * 1000;
    public static final String DEMO_TOKEN_PREFIX = "local_demo_token_";

    public final String accessToken;
    public final String refreshToken;
    public final long accessExpiresAtMs;
    public final long refreshExpiresAtMs;
    public final String username;
    public final String role;
    public final String restaurantId;
    /** Sesión local de demostración (solo compilaciones de depuración): nunca se envía al servidor. */
    public final boolean demo;
    /**
     * Raíz del servidor que emitió la sesión. Los tokens solo se envían a ese servidor: si Ajustes pasa a apuntar a otro,
     * el acceso y el refresco (de un solo uso) no viajan a él. Null en sesiones de demostración o antiguas.
     */
    public final String issuer;

    public AuthSession(String accessToken, String refreshToken, long accessExpiresAtMs, long refreshExpiresAtMs,
                       String username, String role, String restaurantId, boolean demo) {
        this(accessToken, refreshToken, accessExpiresAtMs, refreshExpiresAtMs, username, role, restaurantId, demo, null);
    }

    public AuthSession(String accessToken, String refreshToken, long accessExpiresAtMs, long refreshExpiresAtMs,
                       String username, String role, String restaurantId, boolean demo, String issuer) {
        this.issuer = issuer;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessExpiresAtMs = accessExpiresAtMs;
        this.refreshExpiresAtMs = refreshExpiresAtMs;
        this.username = username;
        this.role = role;
        this.restaurantId = restaurantId;
        this.demo = demo;
    }

    public static AuthSession demo(String restaurantId, String role, String username, long nowMs) {
        long end = nowMs + DEMO_LIFETIME_MS;
        return new AuthSession(DEMO_TOKEN_PREFIX + restaurantId, "demo_refresh", end, end, username, role, restaurantId, true);
    }

    public AuthSession withTokens(String newAccess, String newRefresh, long newAccessExpiresAtMs, long newRefreshExpiresAtMs) {
        return new AuthSession(newAccess, newRefresh, newAccessExpiresAtMs, newRefreshExpiresAtMs, username, role, restaurantId, demo, issuer);
    }

    public AuthSession withTenant(String newRestaurantId, String newRole) {
        return new AuthSession(accessToken, refreshToken, accessExpiresAtMs, refreshExpiresAtMs, username, newRole, newRestaurantId, demo, issuer);
    }
}
