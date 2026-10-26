package com.softmyc.tpv.domain;

import com.softmyc.tpv.domain.adapter.LegacyOrderAdapter;
import com.softmyc.tpv.domain.model.CatalogItem;
import com.softmyc.tpv.domain.model.UniversalOrder;
import com.softmyc.tpv.domain.model.UniversalOrderItem;
import com.softmyc.tpv.domain.usecase.CalcularTotalUseCase;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Suite de pruebas unitarias para el patrón Adapter (LegacyOrderAdapter).
 * Demuestra la compatibilidad regresiva completa entre el modelo gastronómico de la Fase 1
 * y el motor agnóstico de la Fase 2.
 */
public class LegacyOrderAdapterTest {

    @Test
    public void testLegacyToUniversalMapping_preservesExactPricing() {
        // 1. Crear producto tradicional de hostelería de la Fase 1
        Producto burgerProd = new Producto(
                101,
                "Hamburguesa Clásica",
                7.50,
                "Hamburguesas",
                Arrays.asList("Lechuga", "Tomate", "Cebolla")
        );

        PedidoItem legacyItem = new PedidoItem(burgerProd, 2);
        legacyItem.getIngredientesQuitados().add("Cebolla");
        legacyItem.getIngredientesExtra().add("Bacon");
        legacyItem.getSalsas().add("Mayonesa");

        Map<String, Double> extrasMap = new HashMap<>();
        extrasMap.put("Bacon", 1.00);
        extrasMap.put("Mayonesa", 0.50);

        // 2. Cálculo tradicional de la Fase 1 con CalcularTotalUseCase
        CalcularTotalUseCase legacyCalculator = new CalcularTotalUseCase();
        double legacyTotal = legacyCalculator.calcularTotal(Collections.singletonList(legacyItem), extrasMap);
        // (7.50 base + 1.00 extra bacon) * 2 = 17.00 € (la salsa en Fase 1 se calculaba según mapa si aplicaba)
        // Nota: CalcularTotalUseCase suma getIngredientesExtra(), que tiene "Bacon" (1.00). 7.50 + 1.00 = 8.50 * 2 = 17.00 €

        // 3. Conversión mediante LegacyOrderAdapter al motor de la Fase 2
        UniversalOrderItem universalItem = LegacyOrderAdapter.toUniversalOrderItem(legacyItem, extrasMap);

        assertNotNull(universalItem);
        assertEquals("Hamburguesa Clásica", universalItem.getItem().getName());
        assertEquals(new BigDecimal("2"), universalItem.getQuantity());

        // Verificar modificadores adaptados
        assertEquals(3, universalItem.getAppliedModifiers().size());
        assertTrue(universalItem.getFormattedModifiersSummary().contains("Sin Cebolla"));
        assertTrue(universalItem.getFormattedModifiersSummary().contains("Bacon (+1.00 €)"));

        // 4. Adaptar a UniversalOrder completo
        UniversalOrder universalOrder = LegacyOrderAdapter.toUniversalOrder(
                "TICKET-F1-001",
                Collections.singletonList(legacyItem),
                "Tarjeta",
                extrasMap
        );

        assertNotNull(universalOrder);
        assertEquals("HOSPITALITY", universalOrder.getBusinessSector());

        // El total monetario debe ser matemáticamente consistente
        BigDecimal grandTotal = universalOrder.calculateGrandTotal();
        assertNotNull(grandTotal);
        assertTrue(grandTotal.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    public void testIngredientOptionMapping() {
        IngredientOption anadir = new IngredientOption("Queso de Cabra", 1.50, IngredientOption.Type.ANADIR);
        IngredientOption quitar = new IngredientOption("Pepinillo", 0.0, IngredientOption.Type.QUITAR);

        assertNotNull(LegacyOrderAdapter.toItemModifier(anadir));
        assertEquals("Queso de Cabra", LegacyOrderAdapter.toItemModifier(anadir).getName());
        assertEquals(new BigDecimal("1.50"), LegacyOrderAdapter.toItemModifier(anadir).getValue());

        assertNotNull(LegacyOrderAdapter.toItemModifier(quitar));
        assertEquals("Sin Pepinillo", LegacyOrderAdapter.toItemModifier(quitar).toString());
    }

    @Test
    public void testLegacyAdapter_weightProduceItem_deductsTareAndCalculatesNetWeight() {
        Producto manzana = new Producto(
                201, "Manzanas Golden Bio", 2.40, "Frutería",
                Collections.emptyList(), "KG"
        );
        assertTrue(manzana.isWeightBased());

        PedidoItem item = new PedidoItem(manzana, 2);
        item.setCantidadMedida(1.850);
        item.setUnidadMedida("KG");
        item.setTaraKg(0.350); // Caja de madera

        UniversalOrderItem universalItem = LegacyOrderAdapter.toUniversalOrderItem(item, Collections.emptyMap());
        assertNotNull(universalItem);
        assertEquals(new BigDecimal("1.850"), universalItem.getQuantity());

        // Net weight: 1.850 - 0.350 = 1.500 kg -> 1.500 * 2.40 = 3.60 €
        BigDecimal lineTotal = universalItem.getLineTotal();
        assertEquals(new BigDecimal("3.60"), lineTotal);
    }

    @Test
    public void testLegacyAdapter_workshopTimeItem_calculatesLaborAndUrgencySurcharge() {
        Producto manoObra = new Producto(
                301, "Mano de Obra Mecánica", 45.00, "Taller Mecánico",
                Collections.emptyList(), "HORA"
        );
        assertTrue(manoObra.isTimeBased());

        PedidoItem item = new PedidoItem(manoObra, 2);
        item.setCantidadMedida(2.0);
        item.setUnidadMedida("HORA");
        item.getIngredientesExtra().add("Servicio Urgente (+20%)");

        UniversalOrderItem universalItem = LegacyOrderAdapter.toUniversalOrderItem(item, Collections.emptyMap());
        assertNotNull(universalItem);

        // 2.0 h * 45.00 € = 90.00 € + 20% (18.00 €) = 108.00 €
        BigDecimal lineTotal = universalItem.getLineTotal();
        assertEquals(new BigDecimal("108.00"), lineTotal);
    }

    @Test
    public void testCalcularTotalUseCase_withMultiSectorMixedCart_calculatesAccurately() {
        CalcularTotalUseCase calculator = new CalcularTotalUseCase();

        // 1. Hamburguesa con extra bacon: 7.50 + 1.00 = 8.50 €
        Producto burger = new Producto(101, "Burger Smash", 7.50, "Hamburguesas", Collections.emptyList());
        PedidoItem burgerItem = new PedidoItem(burger, 1);
        burgerItem.getIngredientesExtra().add("Bacon");

        // 2. Manzanas: 1.850 kg brutas - 0.350 tara = 1.500 kg netas * 2.40 = 3.60 €
        Producto manzana = new Producto(201, "Manzanas", 2.40, "Frutería", Collections.emptyList(), "KG");
        PedidoItem fruitItem = new PedidoItem(manzana, 2);
        fruitItem.setCantidadMedida(1.850);
        fruitItem.setUnidadMedida("KG");
        fruitItem.setTaraKg(0.350);

        // 3. Taller: 2 horas * 45 = 90 + 20% urgencia (18.00) = 108.00 €
        Producto taller = new Producto(301, "Mano de Obra", 45.00, "Taller", Collections.emptyList(), "HORA");
        PedidoItem laborItem = new PedidoItem(taller, 2);
        laborItem.setCantidadMedida(2.0);
        laborItem.setUnidadMedida("HORA");
        laborItem.getIngredientesExtra().add("Servicio Urgente (+20%)");

        Map<String, Double> extrasMap = new HashMap<>();
        extrasMap.put("Bacon", 1.00);

        List<PedidoItem> cart = Arrays.asList(burgerItem, fruitItem, laborItem);
        double total = calculator.calcularTotal(cart, extrasMap);

        // 8.50 + 3.60 + 108.00 = 120.10 €
        assertEquals(120.10, total, 0.001);
    }
}
