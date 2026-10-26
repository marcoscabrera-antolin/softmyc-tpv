package com.softmyc.tpv.data.network;

/** Datos de conexión que usa {@link OrderSyncService}: los guardados en el dispositivo y la sesión actual. */
interface OrderSyncSettings {

    /** URL de pedidos configurada en Ajustes (sin normalizar), o null si no hay ninguna. */
    String backendBaseUrl();

    /** Clave de API heredada configurada en Ajustes, o null. */
    String apiKey();

    /** Negocio activo tal como está guardado, o null. */
    String activeTenant();

    /** ¿Hay una sesión de usuario real (no de demostración) iniciada? */
    boolean hasUserSession();
}
