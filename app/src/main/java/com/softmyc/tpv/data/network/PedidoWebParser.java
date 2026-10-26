package com.softmyc.tpv.data.network;

import com.softmyc.tpv.util.TypeSafeJsonHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Lectura de los pedidos web tal como llegan de la API (documentos JSON como mapas). */
public final class PedidoWebParser {

    private static final String NEGOCIO_POR_DEFECTO = "lanube";
    private static final String ESTADO_PENDIENTE = "pendiente";
    private static final String CLIENTE_WEB = "Cliente Web";
    private static final String FORMATO_FECHA_SIN_ZONA = "yyyy-MM-dd'T'HH:mm:ss";

    private PedidoWebParser() {
    }

    /** Pedidos del negocio activo, del más antiguo al más reciente. Los de otro negocio se descartan. */
    public static List<Map<String, Object>> pedidosDelNegocio(List<Map<String, Object>> recibidos, String negocioActivo) {
        List<Map<String, Object>> propios = new ArrayList<>();
        if (recibidos == null) return propios;
        for (Map<String, Object> pedido : recibidos) {
            if (tenantDelPedido(pedido).equalsIgnoreCase(negocioActivo)) {
                propios.add(pedido);
            }
        }
        Collections.sort(propios, Comparator.comparingLong(PedidoWebParser::timestamp));
        return propios;
    }

    /** Negocio del pedido normalizado; un pedido sin negocio se atribuye al negocio por defecto. */
    static String tenantDelPedido(Map<String, Object> pedido) {
        Object restaurante = pedido.get("restaurantId");
        return restaurante != null
                ? OrderSyncService.sanitizeTenantId(String.valueOf(restaurante))
                : NEGOCIO_POR_DEFECTO;
    }

    /** Fecha del pedido en milisegundos (cero si no se puede leer). */
    public static long timestamp(Map<String, Object> pedido) {
        if (pedido == null) return 0L;
        Object fecha = pedido.get("fecha");
        if (fecha == null) fecha = pedido.get("createdAt");
        if (fecha == null) return 0L;
        if (fecha instanceof Number) {
            return ((Number) fecha).longValue();
        }
        String texto = String.valueOf(fecha).trim();
        if (texto.isEmpty()) return 0L;
        try {
            return isoATimestamp(texto);
        } catch (Exception sinFormatoIso) {
            try {
                return Long.parseLong(texto);
            } catch (NumberFormatException noEsNumero) {
                return 0L;
            }
        }
    }

    private static long isoATimestamp(String texto) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return java.time.Instant.parse(texto).toEpochMilli();
        }
        SimpleDateFormat formato = new SimpleDateFormat(FORMATO_FECHA_SIN_ZONA, Locale.US);
        formato.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formato.parse(texto).getTime();
    }

    /** Un pedido sin estado se considera pendiente de cocina. */
    public static boolean esPendiente(Map<String, Object> pedido) {
        Object estado = pedido.get("estado");
        String texto = estado != null ? String.valueOf(estado) : ESTADO_PENDIENTE;
        return texto.equalsIgnoreCase(ESTADO_PENDIENTE);
    }

    /** Identificador del pedido en el servidor, o null si no lo trae. */
    public static String idPedido(Map<String, Object> pedido) {
        Object id = pedido.get("_id");
        return id != null ? String.valueOf(id) : null;
    }

    /** Datos del cliente para el ticket; si el pedido solo trae un nombre, se usa ese. */
    public static Map<String, Object> cliente(Map<String, Object> doc) {
        Map<String, Object> cliente = TypeSafeJsonHelper.safeGetMap(doc, "cliente");
        if (cliente != null) return cliente;
        Map<String, Object> porDefecto = new HashMap<>();
        porDefecto.put("nombre", TypeSafeJsonHelper.safeGetString(doc, "cliente", CLIENTE_WEB));
        porDefecto.put("telefono", "");
        porDefecto.put("mesa", "");
        return porDefecto;
    }

    /** Líneas del pedido: "items" o, si está vacío, "cart". */
    public static List<Map<String, Object>> lineas(Map<String, Object> doc) {
        List<Map<String, Object>> items = TypeSafeJsonHelper.safeGetMapList(doc, "items");
        return items.isEmpty() ? TypeSafeJsonHelper.safeGetMapList(doc, "cart") : items;
    }

    public static double total(Map<String, Object> doc) {
        return TypeSafeJsonHelper.safeGetDouble(doc, "total", 0.0);
    }
}
