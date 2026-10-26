package com.softmyc.tpv.data.network;

import com.softmyc.tpv.domain.repository.ExtraCatalogo;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;
import com.softmyc.tpv.util.CategoryUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Convierte las secciones del catálogo que devuelve el servidor en modelos del catálogo local. */
public final class CatalogoRemotoParser {
    private static final String UNIDAD_POR_DEFECTO = "UD";
    private static final int SIN_STOCK = -1;

    private CatalogoRemotoParser() {
    }

    /** Productos ({@code products}). El array debe existir; los campos ausentes toman valores por defecto. */
    public static List<ProductoCatalogo> productos(JSONArray prodsArr) throws JSONException {
        List<ProductoCatalogo> productos = new ArrayList<>();
        for (int i = 0; i < prodsArr.length(); i++) {
            JSONObject p = prodsArr.getJSONObject(i);
            int codigo = p.optInt("codigo", i + 1);
            String nombre = p.optString("nombre");
            double precio = p.optDouble("precio", 0.0);
            String categoria = CategoryUtils.capitalizeCategory(p.optString("categoria", "Otros"));
            productos.add(new ProductoCatalogo(codigo, nombre, precio, categoria, ingredientes(p),
                    UNIDAD_POR_DEFECTO, SIN_STOCK));
        }
        return productos;
    }

    /** Extras ({@code extras}); sin array, lista vacía. */
    public static List<ExtraCatalogo> extras(JSONArray extrasArr) throws JSONException {
        List<ExtraCatalogo> extras = new ArrayList<>();
        if (extrasArr == null) return extras;
        for (int i = 0; i < extrasArr.length(); i++) {
            JSONObject e = extrasArr.getJSONObject(i);
            extras.add(new ExtraCatalogo(e.optString("nombre"), e.optDouble("precio", 0.0)));
        }
        return extras;
    }

    /** Salsas ({@code salsas}): admite tanto cadenas como objetos con {@code nombre} o {@code name}. */
    public static List<String> salsas(JSONArray salsasArr) throws JSONException {
        List<String> salsas = new ArrayList<>();
        if (salsasArr == null) return salsas;
        for (int i = 0; i < salsasArr.length(); i++) {
            String name = nombreSalsa(salsasArr.get(i));
            if (name != null && !name.trim().isEmpty()) salsas.add(name.trim());
        }
        return salsas;
    }

    private static List<String> ingredientes(JSONObject producto) throws JSONException {
        List<String> ings = new ArrayList<>();
        JSONArray ingsArr = producto.optJSONArray("ingredientes");
        if (ingsArr != null) {
            for (int j = 0; j < ingsArr.length(); j++) {
                ings.add(ingsArr.getString(j));
            }
        }
        return ings;
    }

    private static String nombreSalsa(Object itemObj) {
        if (itemObj instanceof JSONObject) {
            JSONObject s = (JSONObject) itemObj;
            return s.optString("nombre", s.optString("name", ""));
        }
        if (itemObj instanceof String) {
            return (String) itemObj;
        }
        return null;
    }
}
