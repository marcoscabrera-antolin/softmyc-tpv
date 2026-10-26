package com.softmyc.tpv.data.network;

import com.softmyc.tpv.BuildConfig;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;

/** Normalización y derivación de las URLs de la API de pedidos. Sin estado ni dependencias de Android. */
final class OrderSyncUrls {
    private static final String ORDERS_SUFFIX = "/api/tpv/orders";
    private static final String DEFAULT_ROOT_FALLBACK = "https://bocaterianube.com";
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final String HTTPS_SCHEME = "https";
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_PREFIX = "https://";
    private static final String HTTP_PREFIX = "http://";

    private OrderSyncUrls() {
    }

    /** Normaliza una URL introducida a mano en la URL de pedidos (termina en {@code /api/tpv/orders}, sin query). */
    static String cleanUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return "";
        String cleaned = withoutQuery(trimmed);
        cleaned = normalizeScheme(cleaned);
        cleaned = collapseSchemeSlashes(cleaned);
        cleaned = stripTrailingSlashes(cleaned);
        return ensureOrdersPath(cleaned);
    }

    /** Quita cualquier parámetro de consulta (ej. ?restaurantID=lanube). */
    private static String withoutQuery(String url) {
        int queryIndex = url.indexOf('?');
        return queryIndex != -1 ? url.substring(0, queryIndex).trim() : url;
    }

    /** Corrige esquemas mal escritos ("https/x", "https:/x") o ausentes ("x.com" pasa a "https://x.com"). */
    private static String normalizeScheme(String cleaned) {
        String lower = cleaned.toLowerCase();
        if (lower.startsWith("https/")) {
            return HTTPS_PREFIX + cleaned.substring(6);
        }
        if (lower.startsWith("http/")) {
            return HTTP_PREFIX + cleaned.substring(5);
        }
        if (lower.startsWith("https:/") && !lower.startsWith(HTTPS_PREFIX)) {
            return HTTPS_PREFIX + cleaned.substring(7);
        }
        if (lower.startsWith("http:/") && !lower.startsWith(HTTP_PREFIX)) {
            return HTTP_PREFIX + cleaned.substring(6);
        }
        if (!lower.startsWith(HTTP_PREFIX) && !lower.startsWith(HTTPS_PREFIX)) {
            return HTTPS_PREFIX + cleaned;
        }
        return cleaned;
    }

    /** Elimina barras duplicadas tras el esquema (por ej. https:/// o https:////). */
    private static String collapseSchemeSlashes(String cleaned) {
        String lower = cleaned.toLowerCase();
        if (lower.startsWith(HTTPS_PREFIX)) {
            return HTTPS_PREFIX + stripLeadingSlashes(cleaned.substring(8));
        }
        if (lower.startsWith(HTTP_PREFIX)) {
            return HTTP_PREFIX + stripLeadingSlashes(cleaned.substring(7));
        }
        return cleaned;
    }

    private static String stripLeadingSlashes(String text) {
        String result = text;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private static String stripTrailingSlashes(String text) {
        String result = text;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /** Completa la URL hasta {@code /api/tpv/orders} según el tramo que ya tenga. */
    private static String ensureOrdersPath(String cleaned) {
        String lower = cleaned.toLowerCase();
        if (lower.endsWith(ORDERS_SUFFIX) || lower.endsWith("/orders")) {
            return cleaned;
        }
        if (lower.endsWith("/api/tpv")) {
            return cleaned + "/orders";
        }
        if (lower.endsWith("/api")) {
            return cleaned + "/tpv/orders";
        }
        return cleaned + ORDERS_SUFFIX;
    }

    /** Raíz de la API: la URL de pedidos sin su sufijo {@code /api/tpv/orders}, para colgar el resto de rutas de ella. */
    static HttpUrl apiRootOf(HttpUrl ordersUrl) {
        List<String> segments = new ArrayList<>(ordersUrl.encodedPathSegments());
        removeTrailing(segments, "");
        removeTrailing(segments, "orders");
        removeTrailing(segments, "tpv");
        removeTrailing(segments, "api");
        HttpUrl.Builder root = ordersUrl.newBuilder().encodedPath("/").query(null).fragment(null);
        for (String segment : segments) {
            root.addEncodedPathSegment(segment);
        }
        return root.build();
    }

    private static void removeTrailing(List<String> segments, String segment) {
        int last = segments.size() - 1;
        if (last >= 0 && segments.get(last).equals(segment)) {
            segments.remove(last);
        }
    }

    static HttpUrl parse(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) {
            throw new IllegalArgumentException("URL no válida: " + url);
        }
        return parsed;
    }

    /** Origen (esquema, host y puerto no por defecto) de la URL base, o la URL por defecto si está vacía. */
    static String apiRootUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return BuildConfig.DEFAULT_BACKEND_URL;
        }
        String origin = originFromUri(baseUrl);
        return origin != null ? origin : fallbackRoot(baseUrl);
    }

    /** Null si la URL no se puede interpretar o no tiene host. */
    private static String originFromUri(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                return null;
            }
            String scheme = uri.getScheme() != null ? uri.getScheme() : HTTPS_SCHEME;
            int port = uri.getPort();
            if (port != -1 && port != DEFAULT_HTTP_PORT && port != DEFAULT_HTTPS_PORT) {
                return scheme + "://" + host + ":" + port;
            }
            return scheme + "://" + host;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Fallback defensivo si URI.create no pudo parsearlo. */
    private static String fallbackRoot(String baseUrl) {
        String root = baseUrl.replaceAll("(?i)/api(/tpv)?(/orders)?.*$", "")
                             .replaceAll("(?i)/orders.*$", "");
        root = stripTrailingSlashes(root);
        return root.isEmpty() ? DEFAULT_ROOT_FALLBACK : root;
    }
}
