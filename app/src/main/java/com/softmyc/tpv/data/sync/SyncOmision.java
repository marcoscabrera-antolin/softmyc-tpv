package com.softmyc.tpv.data.sync;

import java.io.IOException;

/** Motivo por el que el último intento de sincronización no pudo subir las ventas. {@link #NINGUNA} si subió o no había nada. */
public enum SyncOmision {
    NINGUNA("", false),
    SIN_RED("sin conexión a internet", true),
    SERVIDOR_ERROR("el servidor devolvió un error", true),
    SIN_CREDENCIALES("sesión caducada o sin credenciales: inicia sesión de nuevo", false),
    SIN_SESION("no hay sesión iniciada", false),
    SIN_NEGOCIO("sin negocio activo", false),
    SESION_OTRO_NEGOCIO("la sesión es de otro negocio", false),
    URL_INVALIDA("URL del servidor mal configurada", false);

    private static final String MARCA_SIN_CREDENCIALES = "Sin credenciales";
    private static final String MARCA_SIN_NEGOCIO = "Sin negocio";
    private static final String MARCA_URL_INVALIDA = "URL no válida";

    private final String descripcion;
    private final boolean reintentable;

    SyncOmision(String descripcion, boolean reintentable) {
        this.descripcion = descripcion;
        this.reintentable = reintentable;
    }

    public String descripcion() {
        return descripcion;
    }

    /** ¿Tiene sentido reintentar más tarde sin que el usuario haga nada? */
    public boolean reintentable() {
        return reintentable;
    }

    /**
     * Clasifica el fallo de red que devuelve {@code OrderSyncService}: sus errores previos a la llamada llevan
     * un mensaje fijo; cualquier otra {@link IOException} es de conexión.
     */
    public static SyncOmision clasificar(IOException e) {
        String mensaje = e.getMessage() != null ? e.getMessage() : "";
        if (mensaje.contains(MARCA_SIN_CREDENCIALES)) return SIN_CREDENCIALES;
        if (mensaje.contains(MARCA_SIN_NEGOCIO)) return SIN_NEGOCIO;
        if (mensaje.contains(MARCA_URL_INVALIDA)) return URL_INVALIDA;
        return SIN_RED;
    }
}
