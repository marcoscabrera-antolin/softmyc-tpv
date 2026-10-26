package com.softmyc.tpv.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;

public class CustomizedOrderItemMapeoTest {

    @Test
    public void toPedidoItem_conservaMedidaUnidadTaraYComplementos() {
        Producto fruta = new Producto(10, "Manzanas", 4.0, "Frutería", new ArrayList<>(), "KG");
        CustomizedOrderItem item = new CustomizedOrderItem(fruta, 1.5, "KG");
        item.setTareKg(0.2);
        item.addRemovedIngredient("Hueso");
        item.addExtraIngredient(new IngredientOption("Bolsa", 0.1, IngredientOption.Type.ANADIR));
        item.addSauce(new IngredientOption("Miel", 0.0, IngredientOption.Type.SALSA));

        PedidoItem linea = item.toPedidoItem();

        assertEquals(1.5, linea.getCantidadMedida(), 0.0001);
        assertEquals("KG", linea.getUnidadMedida());
        assertEquals(0.2, linea.getTaraKg(), 0.0001);
        assertEquals(10, linea.getId());
        assertEquals(1, linea.getIngredientesQuitados().size());
        assertEquals("Bolsa", linea.getIngredientesExtra().get(0));
        assertEquals("Miel", linea.getSalsas().get(0));
    }
}
