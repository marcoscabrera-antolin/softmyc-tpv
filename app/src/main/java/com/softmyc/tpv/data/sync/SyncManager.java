package com.softmyc.tpv.data.sync;

import android.content.Context;
import android.util.Log;

import com.softmyc.tpv.domain.DeserializacionVentaException;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.SerializacionVentaException;
import com.softmyc.tpv.domain.usecase.StockAdvisor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SyncManager {

    private static final String TAG = "SyncManager";

    // Serializador auxiliar para guardar en Room
    public static String serializarItems(List<PedidoItem> items) {
        return serializarItems(items, null);
    }

    /**
     * Serializa las líneas del pedido. Si se facilitan los precios de extras se guarda también el
     * importe calculado de cada línea ("importe_linea"), que usan el arqueo de cocina y la analítica.
     */
    public static String serializarItems(List<PedidoItem> items, java.util.Map<String, Double> preciosExtras) {
        try {
            com.softmyc.tpv.domain.usecase.CalcularTotalUseCase calculadora =
                    new com.softmyc.tpv.domain.usecase.CalcularTotalUseCase();
            JSONArray arr = new JSONArray();
            for (PedidoItem item : items) {
                JSONObject obj = new JSONObject();
                obj.put("nombre", item.getNombre());
                obj.put("codigo", item.getId());
                obj.put("cantidad", item.getCantidad());
                // Peso/horas reales: "cantidad" es el entero ceil(kg), insuficiente para recuperar la venta.
                obj.put("cantidad_medida", item.getCantidadMedida());
                obj.put("unidad", item.getUnidadMedida());
                obj.put("tara_kg", item.getTaraKg());
                obj.put("precio_unitario", item.getProducto().getPrecio());
                obj.put("categoria", item.getProducto().getCategoria());
                if (preciosExtras != null) {
                    obj.put("importe_linea", calculadora.calcularTotal(
                            java.util.Collections.singletonList(item), preciosExtras));
                }

                JSONArray extras = new JSONArray();
                for (String ext : item.getIngredientesExtra()) {
                    extras.put(ext);
                }
                obj.put("extras", extras);

                // Incluir ingredientes quitados para conservar el historial completo
                JSONArray quitados = new JSONArray();
                for (String q : item.getIngredientesQuitados()) {
                    quitados.put(q);
                }
                obj.put("quitados", quitados);

                JSONArray salsas = new JSONArray();
                for (String sal : item.getSalsas()) {
                    salsas.put(sal);
                }
                obj.put("salsas", salsas);

                arr.put(obj);
            }
            return arr.toString();
        } catch (Exception e) {
            // Nunca devolver "[]": una venta con total y sin líneas no se puede auditar ni subir (ERR-06).
            Log.e(TAG, "Error al serializar las líneas de la venta", e);
            throw new SerializacionVentaException(
                    "No se pudo guardar el detalle del pedido. Revisa las líneas e inténtalo de nuevo.", e);
        }
    }

    /**
     * Unidades por código de producto de una venta guardada, o null si el JSON no es válido o no guarda
     * el código (ventas anteriores a este campo): en ese caso no se puede reconstruir qué stock se descontó.
     */
    public static java.util.Map<Integer, Integer> cantidadesPorProducto(String json) {
        if (json == null) return null;
        try {
            JSONArray arr = new JSONArray(json);
            java.util.Map<Integer, Integer> cantidades = new java.util.LinkedHashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                int codigo = obj.optInt("codigo", 0);
                int cantidad = obj.optInt("cantidad", 0);
                // Solo las unidades discretas descuentan stock (NEG-09); las líneas al peso o por horas no.
                boolean descuentaStock = StockAdvisor.controlaStock(obj.optString("unidad", "UD"));
                if (codigo > 0 && cantidad > 0 && descuentaStock) cantidades.merge(codigo, cantidad, Integer::sum);
            }
            boolean algunaLineaConCodigo = false;
            for (int i = 0; i < arr.length() && !algunaLineaConCodigo; i++) {
                algunaLineaConCodigo = arr.getJSONObject(i).optInt("codigo", 0) > 0;
            }
            return algunaLineaConCodigo ? cantidades : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Quita el código de producto de cada línea: marca una venta cuyo stock original no consta. */
    public static String sinCodigos(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) arr.getJSONObject(i).remove("codigo");
            return arr.toString();
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * Lee las líneas guardadas de una venta. Si alguna línea no es legible se lanza
     * {@link DeserializacionVentaException}: nunca se devuelve una lista parcial.
     *
     * @throws DeserializacionVentaException si el JSON es nulo, malformado o una línea no es válida
     */
    public static List<PedidoItem> deserializarItems(String json, List<Producto> productosDisponibles) {
        List<PedidoItem> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String nombre = obj.getString("nombre");
                int cantidad = obj.getInt("cantidad");
                double precioUnitario = obj.getDouble("precio_unitario");
                // Ventas guardadas antes de este campo no lo tienen: se tratan como unidades enteras.
                double cantidadMedida = obj.optDouble("cantidad_medida", cantidad);
                String unidadGuardada = obj.optString("unidad", "");
                double taraKg = obj.optDouble("tara_kg", 0.0);

                int codigoGuardado = obj.optInt("codigo", 0);
                Producto prod = null;
                if (productosDisponibles != null) {
                    for (Producto p : productosDisponibles) {
                        // El código guardado manda (un producto renombrado sigue siendo el mismo); el nombre es el respaldo.
                        boolean coincide = codigoGuardado > 0 ? p.getCodigo() == codigoGuardado : p.getNombre().equalsIgnoreCase(nombre);
                        if (coincide) {
                            prod = new Producto(
                                    p.getCodigo(),
                                    p.getNombre(),
                                    p.getPrecio(),
                                    p.getCategoria(),
                                    p.getIngredientes() != null ? new ArrayList<>(p.getIngredientes()) : new ArrayList<>(),
                                    p.getUnidadMedida()
                            );
                            break;
                        }
                    }
                }
                if (prod == null) {
                    // Sin catálogo: conserva el código real para que editar la venta no ajuste el stock de otro producto.
                    prod = new Producto(codigoGuardado > 0 ? codigoGuardado : 999, nombre, precioUnitario, "temporal", new ArrayList<>(),
                            unidadGuardada.isEmpty() ? "UD" : unidadGuardada);
                }

                PedidoItem item = new PedidoItem(prod, cantidad);
                if (cantidadMedida > 0) item.setCantidadMedida(cantidadMedida);
                if (!unidadGuardada.isEmpty()) item.setUnidadMedida(unidadGuardada);
                item.setTaraKg(taraKg);

                // Extras
                JSONArray arrExtras = obj.optJSONArray("extras");
                if (arrExtras != null) {
                    for (int j = 0; j < arrExtras.length(); j++) {
                        item.getIngredientesExtra().add(arrExtras.getString(j));
                    }
                }

                // Quitados
                JSONArray arrQuitados = obj.optJSONArray("quitados");
                if (arrQuitados != null) {
                    for (int j = 0; j < arrQuitados.length(); j++) {
                        item.getIngredientesQuitados().add(arrQuitados.getString(j));
                    }
                }

                // Salsas
                JSONArray arrSalsas = obj.optJSONArray("salsas");
                if (arrSalsas != null) {
                    for (int j = 0; j < arrSalsas.length(); j++) {
                        item.getSalsas().add(arrSalsas.getString(j));
                    }
                }

                result.add(item);
            }
        } catch (JSONException | RuntimeException e) {
            Log.e(TAG, "Error al deserializar las líneas de la venta", e);
            throw new DeserializacionVentaException("No se pudo leer el detalle de la venta", e);
        }
        return result;
    }

    public interface OnSyncCompleteListener {
        void onComplete(boolean success);
    }
}
