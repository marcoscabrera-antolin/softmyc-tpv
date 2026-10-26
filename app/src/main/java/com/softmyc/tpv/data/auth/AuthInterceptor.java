package com.softmyc.tpv.data.auth;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Añade el token de acceso (renovándolo antes si caducó) a las peticiones dirigidas a la API configurada. */
public final class AuthInterceptor implements Interceptor {

    private final SessionManager manager;

    public AuthInterceptor(SessionManager manager) {
        this.manager = manager;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        // Una clave de API vacía (no configurada) no debe viajar: con ella el servidor la intentaría validar.
        if ("".equals(request.header("x-api-key"))) request = request.newBuilder().removeHeader("x-api-key").build();
        if (!manager.isApiRequest(request.url()) || SessionManager.isAuthEndpoint(request.url()) || request.header("Authorization") != null) {
            return chain.proceed(request);
        }
        String token = manager.accessTokenForRequest();
        if (token == null) return chain.proceed(request);
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer " + token).build());
    }
}
