package com.softmyc.tpv.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomizedOrderItem {
    private Producto baseProduct;
    private int quantity;
    private double decimalQuantity = 1.0;
    private double tareKg = 0.0;
    private String unit = "UD";
    private List<String> removedIngredients = new ArrayList<>();
    private List<IngredientOption> addedIngredients = new ArrayList<>();
    private List<IngredientOption> addedSauces = new ArrayList<>();

    public CustomizedOrderItem(Producto baseProduct, int quantity) {
        this.baseProduct = baseProduct;
        this.quantity = quantity;
        this.decimalQuantity = quantity;
        this.unit = baseProduct != null ? baseProduct.getUnidadMedida() : "UD";
    }

    public CustomizedOrderItem(Producto baseProduct, double decimalQuantity, String unit) {
        this.baseProduct = baseProduct;
        this.decimalQuantity = decimalQuantity;
        this.quantity = (int) Math.ceil(decimalQuantity);
        this.unit = unit != null ? unit : (baseProduct != null ? baseProduct.getUnidadMedida() : "UD");
    }

    public void addRemovedIngredient(String ingredient) {
        if (ingredient != null && !removedIngredients.contains(ingredient)) {
            removedIngredients.add(ingredient);
        }
    }

    public void addExtraIngredient(IngredientOption extra) {
        if (extra != null) {
            addedIngredients.add(extra);
            Collections.sort(addedIngredients);
        }
    }

    public void addSauce(IngredientOption sauce) {
        if (sauce != null) {
            addedSauces.add(sauce);
        }
    }

    public Producto getBaseProduct() { return baseProduct; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.decimalQuantity = quantity;
    }
    public double getDecimalQuantity() { return decimalQuantity; }
    public void setDecimalQuantity(double decimalQuantity) {
        this.decimalQuantity = decimalQuantity;
        this.quantity = (int) Math.ceil(decimalQuantity);
    }
    public double getTareKg() { return tareKg; }
    public void setTareKg(double tareKg) { this.tareKg = tareKg; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public List<String> getRemovedIngredients() { return removedIngredients; }
    public List<IngredientOption> getAddedIngredients() { return addedIngredients; }
    public List<IngredientOption> getAddedSauces() { return addedSauces; }

    /**
     * La línea del pedido equivalente a este artículo personalizado. Es el único sitio que hace este mapeo: lo usan
     * a la vez la vista previa del precio y el carrito, así no pueden divergir en cantidad, peso, tara o unidad.
     */
    public PedidoItem toPedidoItem() {
        PedidoItem linea = new PedidoItem(baseProduct, quantity);
        linea.setCantidadMedida(decimalQuantity);
        linea.setUnidadMedida(unit);
        linea.setTaraKg(tareKg);
        linea.getIngredientesQuitados().addAll(removedIngredients);
        for (IngredientOption extra : addedIngredients) linea.getIngredientesExtra().add(extra.getName());
        for (IngredientOption sauce : addedSauces) linea.getSalsas().add(sauce.getName());
        return linea;
    }
}
