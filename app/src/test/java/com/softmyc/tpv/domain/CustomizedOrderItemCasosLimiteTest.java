package com.softmyc.tpv.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.usecase.CalcularTotalUseCase;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Artículo personalizado: cantidades enteras y decimales, peso con tara, personalizaciones nulas o repetidas,
 * y la línea de pedido equivalente. Sin producto base el importe es cero.
 */
public class CustomizedOrderItemCasosLimiteTest {

    @Test
    public void sin_producto_base_la_unidad_es_UD_y_el_subtotal_es_cero() {
        CustomizedOrderItem item = new CustomizedOrderItem(null, 2);

        assertEquals("UD", item.getUnit());
        assertEquals(2, item.getQuantity());
    }

    @Test
    public void la_unidad_si_no_se_indica_es_la_del_producto_base() {
        CustomizedOrderItem item = new CustomizedOrderItem(producto(2.0, "KG"), 1.5, null);

        assertEquals("KG", item.getUnit());
    }

    @Test
    public void la_cantidad_decimal_redondea_hacia_arriba_la_cantidad_entera() {
        CustomizedOrderItem item = new CustomizedOrderItem(producto(2.0, "UD"), 2.1, "UD");

        assertEquals(3, item.getQuantity());
        assertEquals(2.1, item.getDecimalQuantity(), 1e-9);
    }

    @Test
    public void al_fijar_la_cantidad_entera_la_decimal_la_sigue_y_viceversa() {
        CustomizedOrderItem item = new CustomizedOrderItem(producto(1.0, "UD"), 1);

        item.setQuantity(4);
        assertEquals(4.0, item.getDecimalQuantity(), 1e-9);

        item.setDecimalQuantity(2.2);
        assertEquals(3, item.getQuantity());
    }

    @Test
    public void peso_con_tara_factura_solo_el_neto() {
        CustomizedOrderItem item = new CustomizedOrderItem(producto(2.0, "KG"), 1.5, "KG");
        item.setTareKg(0.25);

        assertEquals(2.5, total(item), 1e-9);
    }

    @Test
    public void tara_mayor_que_el_peso_se_rechaza_en_vez_de_dar_importe_negativo() {
        CustomizedOrderItem item = new CustomizedOrderItem(producto(2.0, "KG"), 0.3, "KG");
        item.setTareKg(0.5);

        // El motor universal lo rechaza (el heredado devolvía 0): no hay peso neto que cobrar.
        assertThrows(CalculoPedidoException.class, () -> total(item));
    }

    @Test
    public void unidades_suman_extras_y_salsas_al_precio_antes_de_multiplicar() {
        CustomizedOrderItem item = new CustomizedOrderItem(producto(4.0, "UD"), 2);
        item.addExtraIngredient(new IngredientOption("Queso", 1.0, IngredientOption.Type.ANADIR));
        item.addSauce(new IngredientOption("Alioli", 0.5, IngredientOption.Type.SALSA));

        assertEquals(11.0, total(item), 1e-9); // (4,00 + 1,00 + 0,50) x 2
    }

    @Test
    public void extras_y_salsas_nulos_se_ignoran_al_anadirlos() {
        CustomizedOrderItem item = new CustomizedOrderItem(producto(4.0, "UD"), 1);

        item.addExtraIngredient(null);
        item.addSauce(null);

        assertTrue(item.getAddedIngredients().isEmpty());
        assertTrue(item.getAddedSauces().isEmpty());
        assertEquals(4.0, total(item), 1e-9);
    }

    @Test
    public void quitar_el_mismo_ingrediente_dos_veces_lo_deja_una_vez_y_los_nulos_no_entran() {
        CustomizedOrderItem item = new CustomizedOrderItem(producto(4.0, "UD"), 1);

        item.addRemovedIngredient("Cebolla");
        item.addRemovedIngredient("Cebolla");
        item.addRemovedIngredient(null);

        assertEquals(Collections.singletonList("Cebolla"), item.getRemovedIngredients());
    }

    @Test
    public void los_extras_quedan_ordenados_por_nombre() {
        CustomizedOrderItem item = new CustomizedOrderItem(producto(4.0, "UD"), 1);

        item.addExtraIngredient(new IngredientOption("Tomate", 0.2, IngredientOption.Type.ANADIR));
        item.addExtraIngredient(new IngredientOption("Bacon", 0.9, IngredientOption.Type.ANADIR));

        assertEquals("Bacon", item.getAddedIngredients().get(0).getName());
        assertEquals("Tomate", item.getAddedIngredients().get(1).getName());
    }

    @Test
    public void toPedidoItem_traslada_cantidades_unidad_tara_y_personalizaciones() {
        CustomizedOrderItem item = new CustomizedOrderItem(producto(2.0, "KG"), 1.5, "KG");
        item.setTareKg(0.2);
        item.addRemovedIngredient("Sal");
        item.addExtraIngredient(new IngredientOption("Limón", 0.0, IngredientOption.Type.ANADIR));
        item.addSauce(new IngredientOption("Mojo", 0.0, IngredientOption.Type.SALSA));

        PedidoItem linea = item.toPedidoItem();

        assertEquals(2, linea.getCantidad());
        assertEquals(1.5, linea.getCantidadMedida(), 1e-9);
        assertEquals("KG", linea.getUnidadMedida());
        assertEquals(0.2, linea.getTaraKg(), 1e-9);
        assertEquals(Collections.singletonList("Sal"), linea.getIngredientesQuitados());
        assertEquals(Collections.singletonList("Limón"), linea.getIngredientesExtra());
        assertEquals(Collections.singletonList("Mojo"), linea.getSalsas());
    }

    @Test
    public void getAddedSauces_empieza_vacio_y_admite_el_mismo_nombre_mas_de_una_vez() {
        CustomizedOrderItem item = new CustomizedOrderItem(producto(1.0, "UD"), 1);
        assertTrue(item.getAddedSauces().isEmpty());

        item.addSauce(new IngredientOption("Ketchup", 0.0, IngredientOption.Type.SALSA));
        item.addSauce(new IngredientOption("Ketchup", 0.0, IngredientOption.Type.SALSA));

        assertEquals(2, item.getAddedSauces().size());
    }

    /** Importe según el motor universal (el único que cobra), con los precios de los extras del propio artículo. */
    private static double total(CustomizedOrderItem item) {
        Map<String, Double> precios = new HashMap<>();
        for (IngredientOption o : item.getAddedIngredients()) precios.put(o.getName(), o.getExtraPrice());
        for (IngredientOption o : item.getAddedSauces()) precios.put(o.getName(), o.getExtraPrice());
        return new CalcularTotalUseCase()
                .calcularTotal(Collections.singletonList(item.toPedidoItem()), precios);
    }

    private static Producto producto(double precio, String unidad) {
        return new Producto(1, "Base", precio, "Carta", new ArrayList<>(), unidad);
    }
}
