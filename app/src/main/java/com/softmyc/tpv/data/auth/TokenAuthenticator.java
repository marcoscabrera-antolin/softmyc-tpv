package com.softmyc.tpv.data.auth;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/** Ante un 401 en una petición con nuestro token, renueva la sesión una sola vez y reintenta con el token nuevo. */
public final class TokenAuthenticator implements Authenticator {

    private static final String BEARER = "Bearer ";
    private static final int MAX_ATTEMPTS = 2;

    private final SessionManager manager;

    public TokenAuthenticator(SessionManager manager) {
        this.manager = manager;
    }

    @Override
    public Request authenticate(Route route, Response response) {
        Request request = response.request();
        String header = request.header("Authorization");
        if (header == null || !header.startsWith(BEARER)) return null;
        if (!manager.isApiRequest(request.url()) || SessionManager.isAuthEndpoint(request.url())) return null;
        if (attempts(response) >= MAX_ATTEMPTS) return null;
        String used = header.substring(BEARER.length());
        AuthSession renewed = manager.refreshIfStale(used);
        if (renewed == null || renewed.accessToken.equals(used)) return null;
        return request.newBuilder().header("Authorization", BEARER + renewed.accessToken).build();
    }

    private static int attempts(Response response) {
        int n = 1;
        for (Response r = response.priorResponse(); r != null; r = r.priorResponse()) n++;
        return n;
    }
}
