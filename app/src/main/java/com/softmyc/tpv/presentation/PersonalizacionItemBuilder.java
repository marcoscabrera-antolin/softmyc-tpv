package com.softmyc.tpv.presentation;

import android.view.View;

import com.google.android.material.chip.ChipGroup;
import com.softmyc.tpv.domain.CustomizedOrderItem;
import com.softmyc.tpv.domain.IngredientOption;
import com.softmyc.tpv.domain.Producto;

import java.util.ArrayList;
import java.util.List;

/**
 * Traduce lo elegido en el diálogo de personalización a un {@link CustomizedOrderItem}. Lo usan a la vez la vista
 * previa del precio y el botón de añadir al carrito, para que ambos vean exactamente el mismo artículo.
 */
final class PersonalizacionItemBuilder {

    private static final String UNIDAD_UNITARIA = "UD";

    private PersonalizacionItemBuilder() {
    }

    static CustomizedOrderItem construir(Producto producto, int cantidad, double cantidadDecimal, double tareKg,
                                         ChipGroup quitar, ChipGroup anadir, ChipGroup salsas) {
        CustomizedOrderItem item = crearBase(producto, cantidad, cantidadDecimal, tareKg);
        for (LightChipView chip : marcados(quitar)) {
            if (chip.getTag() instanceof String) {
                item.addRemovedIngredient((String) chip.getTag());
            }
        }
        for (LightChipView chip : marcados(anadir)) {
            if (chip.getTag() instanceof IngredientOption) {
                item.addExtraIngredient((IngredientOption) chip.getTag());
            }
        }
        for (LightChipView chip : marcados(salsas)) {
            if (chip.getTag() instanceof IngredientOption) {
                item.addSauce((IngredientOption) chip.getTag());
            } else if (chip.getTag() instanceof String) {
                item.addSauce(new IngredientOption((String) chip.getTag(), 0.0, IngredientOption.Type.SALSA));
            }
        }
        return item;
    }

    private static CustomizedOrderItem crearBase(Producto producto, int cantidad, double cantidadDecimal, double tareKg) {
        String unidadVenta = producto.getUnidadEfectiva();
        if (!UNIDAD_UNITARIA.equalsIgnoreCase(unidadVenta)) {
            CustomizedOrderItem item = new CustomizedOrderItem(producto, cantidadDecimal, unidadVenta);
            item.setTareKg(tareKg);
            return item;
        }
        return new CustomizedOrderItem(producto, cantidad);
    }

    private static List<LightChipView> marcados(ChipGroup grupo) {
        List<LightChipView> marcados = new ArrayList<>();
        if (grupo == null) return marcados;
        for (int i = 0; i < grupo.getChildCount(); i++) {
            View hijo = grupo.getChildAt(i);
            if (hijo instanceof LightChipView && ((LightChipView) hijo).isChecked()) {
                marcados.add((LightChipView) hijo);
            }
        }
        return marcados;
    }
}
