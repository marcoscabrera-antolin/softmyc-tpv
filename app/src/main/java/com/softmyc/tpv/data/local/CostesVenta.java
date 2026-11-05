package com.softmyc.tpv.data.local;

import com.softmyc.tpv.domain.escandallo.LineaConsumo;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.usecase.CalculadoraRentabilidad;
import com.softmyc.tpv.domain.usecase.ConsumoIngredientes;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lectura y anotación de las líneas guardadas de una venta para el escandallo.
 *
 * Al cobrar, cada línea guarda su "foto" de coste ("coste_ingredientes" y "gastos_anadidos", de la línea entera):
 * si después cambian precios o recetas, las ventas ya hechas conservan el beneficio que tuvieron.
 */
public final class CostesVenta {

    public static final String COSTE_INGREDIENTES = "coste_ingredientes";
    public static final String GASTOS_ANADIDOS = "gastos_anadidos";

    private CostesVenta() { }

    /** Devuelve el JSON con la foto de coste en cada línea en la que se conoce. Si el JSON no es válido, lo deja igual. */
    public static String anotar(String itemsJson, Recetario recetario) {
        if (itemsJson == null) return null;
        try {
            JSONArray arr = new JSONArray(itemsJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                LineaConsumo linea = linea(obj);
                Double coste = ConsumoIngredientes.coste(linea, recetario);
                Double gastos = ConsumoIngredientes.gastosAnadidos(linea, recetario);
                obj.remove(COSTE_INGREDIENTES);
                obj.remove(GASTOS_ANADIDOS);
                if (coste != null && gastos != null) {
                    obj.put(COSTE_INGREDIENTES, coste);
                    obj.put(GASTOS_ANADIDOS, gastos);
                }
            }
            return arr.toString();
        } catch (Exception e) {
            return itemsJson;
        }
    }

    /**
     * Líneas de una venta guardada para calcular lo que gastó, o null si no se puede (JSON inválido o venta
     * antigua sin código de producto: entonces no consta qué se descontó).
     */
    public static List<LineaConsumo> lineasConsumo(String itemsJson) {
        if (itemsJson == null) return null;
        try {
            JSONArray arr = new JSONArray(itemsJson);
            List<LineaConsumo> lineas = new ArrayList<>();
            boolean algunCodigo = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                algunCodigo |= obj.optInt("codigo", 0) > 0;
                lineas.add(linea(obj));
            }
            return algunCodigo ? lineas : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Líneas de una venta para calcular su beneficio, o null si el JSON no se puede leer. Usa la foto de coste
     * guardada al cobrar; las ventas anteriores a la foto se valoran con la receta y los precios actuales.
     */
    public static List<CalculadoraRentabilidad.Linea> lineasRentabilidad(String itemsJson, Recetario recetario) {
        if (itemsJson == null) return null;
        try {
            JSONArray arr = new JSONArray(itemsJson);
            List<CalculadoraRentabilidad.Linea> lineas = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                int cantidad = obj.optInt("cantidad", 0);
                double unidades = obj.optDouble("cantidad_medida", cantidad);
                double importe = obj.has("importe_linea")
                        ? obj.optDouble("importe_linea", 0)
                        : obj.optDouble("precio_unitario", 0) * unidades;
                Double coste;
                Double gastos;
                if (obj.has(COSTE_INGREDIENTES) && obj.has(GASTOS_ANADIDOS)) {
                    coste = obj.optDouble(COSTE_INGREDIENTES);
                    gastos = obj.optDouble(GASTOS_ANADIDOS);
                } else {
                    LineaConsumo l = linea(obj);
                    coste = ConsumoIngredientes.coste(l, recetario);
                    gastos = ConsumoIngredientes.gastosAnadidos(l, recetario);
                }
                lineas.add(new CalculadoraRentabilidad.Linea(obj.optString("nombre", "?"), unidades, importe, coste, gastos));
            }
            return lineas;
        } catch (Exception e) {
            return null;
        }
    }

    static LineaConsumo linea(JSONObject obj) {
        return new LineaConsumo(obj.optInt("codigo", 0), obj.optInt("cantidad", 0), obj.optString("unidad", "UD"),
                textos(obj.optJSONArray("quitados")), textos(obj.optJSONArray("extras")));
    }

    private static List<String> textos(JSONArray arr) {
        List<String> r = new ArrayList<>();
        if (arr == null) return r;
        for (int i = 0; i < arr.length(); i++) r.add(arr.optString(i, ""));
        return r;
    }
}
