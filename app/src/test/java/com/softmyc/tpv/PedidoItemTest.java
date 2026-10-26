package com.softmyc.tpv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PedidoItemTest {

    private Producto productoBase;

    @Before
    public void setUp() {
        List<String> ingredientes = new ArrayList<>();
        ingredientes.add("Remolacha");
        ingredientes.add("Queso");
        productoBase = new Producto(10, "Hamburguesa Especial", 6.50, "hamburguesas", ingredientes);
    }

    @Test
    public void testCrearPedidoItemYModificaciones() {
        PedidoItem item = new PedidoItem(productoBase, 2);

        assertEquals(10, item.getId());
        assertEquals("Hamburguesa Especial", item.getNombre());
        assertEquals(2, item.getCantidad());
        assertFalse(item.sinUnidades());

        // Aumentar y reducir cantidades
        item.aumentarCantidad(3);
        assertEquals(5, item.getCantidad());

        item.reducirCantidad(5);
        assertEquals(0, item.getCantidad());
        assertTrue(item.sinUnidades());
    }

    @Test
    public void testDetallesVisualesYModificaciones() {
        PedidoItem item = new PedidoItem(productoBase, 1);

        // Añadir extras y quitados
        item.getIngredientesQuitados().add("Remolacha");
        item.getIngredientesExtra().add("Bacon");
        item.getIngredientesExtra().add("Huevo");

        String expected = "Sin: Remolacha\nAñados: Bacon, Huevo";
        // Nota: en PedidoItem.java getDetallesVisuales dice: "Añadidos: "
        assertTrue(item.getDetallesVisuales().contains("Sin: Remolacha"));
        assertTrue(item.getDetallesVisuales().contains("Añadidos: Bacon, Huevo"));
    }

    @Test
    public void testConstructorCopiaCopiaProfunda() {
        PedidoItem original = new PedidoItem(productoBase, 2);
        original.getIngredientesQuitados().add("Queso");
        original.getIngredientesExtra().add("Bacon");
        original.getSalsas().add("Ketchup");
        original.setPrecioExtra(1.50);

        // Duplicar usando constructor copia
        PedidoItem copia = new PedidoItem(original);

        assertEquals(original.getId(), copia.getId());
        assertEquals(original.getCantidad(), copia.getCantidad());
        assertEquals(original.getPrecioExtra(), copia.getPrecioExtra(), 0.001);

        // Validar que son listas independientes en la memoria
        assertNotSame(original.getIngredientesQuitados(), copia.getIngredientesQuitados());
        assertNotSame(original.getIngredientesExtra(), copia.getIngredientesExtra());
        assertNotSame(original.getSalsas(), copia.getSalsas());
        assertNotSame(original.getProducto(), copia.getProducto());

        // Validar contenido copiado
        assertEquals("Queso", copia.getIngredientesQuitados().get(0));
        assertEquals("Bacon", copia.getIngredientesExtra().get(0));
        assertEquals("Ketchup", copia.getSalsas().get(0));
    }
}
