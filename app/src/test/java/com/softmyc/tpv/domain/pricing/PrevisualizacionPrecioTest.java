package com.softmyc.tpv.domain.pricing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.CustomizedOrderItem;
import com.softmyc.tpv.domain.IngredientOption;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.usecase.CalcularTotalUseCase;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La vista previa del diálogo de personalización debe coincidir con el total que calcula el motor cuando el artículo
 * se añade al carrito (NEG-14). Cada caso compara la previsualización con el motor sobre la misma línea.
 */
public class PrevisualizacionPrecioTest {

    private static final double CENTIMO = 0.005;

    @Test
    public void unidadPreviewMatchesEngineTotalWithExtrasAndSauce() {
        // Arrange
        Producto bocata = new Producto(1, "Bocata", 3.50, "Bocadillos", Collections.emptyList());
        CustomizedOrderItem item = new CustomizedOrderItem(bocata, 2);
        IngredientOption queso = new IngredientOption("Queso", 0.50, IngredientOption.Type.ANADIR);
        IngredientOption mayonesa = new IngredientOption("Mayonesa", 0.30, IngredientOption.Type.SALSA);
        item.addExtraIngredient(queso);
        item.addSauce(mayonesa);

        // Act
        PrevisualizacionPrecio.Resultado preview = PrevisualizacionPrecio.calcular(item);

        // Assert
        assertEquals(8.60, preview.total, CENTIMO);
        assertEquals(engineTotal(item), preview.total, CENTIMO);
    }

    @Test
    public void pesoConTaraPreviewMatchesEngineTotal() {
        // Arrange
        Producto manzanas = new Producto(2, "Manzanas", 2.00, "Frutería", Collections.emptyList(), "KG");
        CustomizedOrderItem item = new CustomizedOrderItem(manzanas, 1.5, "KG");
        item.setTareKg(0.2);
        item.addExtraIngredient(new IngredientOption("Bolsa", 0.10, IngredientOption.Type.ANADIR));

        // Act
        PrevisualizacionPrecio.Resultado preview = PrevisualizacionPrecio.calcular(item);

        // Assert
        assertTrue(preview.pesoValido);
        assertEquals(1.3, preview.netoKg, 0.0001);
        // 1,3 kg x 2,00 € = 2,60 €; el motor aplica los extras por kg neto: 0,10 € x 1,3 kg = 0,13 €
        assertEquals(2.73, preview.total, CENTIMO);
        assertEquals(engineTotal(item), preview.total, CENTIMO);
    }

    @Test
    public void pesoNoSuperiorALaTaraDaPesoInvalidoYTotalCero() {
        // Arrange
        Producto manzanas = new Producto(2, "Manzanas", 2.00, "Frutería", Collections.emptyList(), "KG");
        CustomizedOrderItem item = new CustomizedOrderItem(manzanas, 0.1, "KG");
        item.setTareKg(0.2);

        // Act
        PrevisualizacionPrecio.Resultado preview = PrevisualizacionPrecio.calcular(item);

        // Assert
        assertFalse(preview.pesoValido);
        assertEquals(0.0, preview.total, CENTIMO);
    }

    @Test
    public void horasPreviewAppliesPercentageSurchargeOverSubtotalAsEngineDoes() {
        // Arrange: 2 h a 40 € con "Urgencia +20%" -> 80 € + 20 % del subtotal = 96 € (NEG-14)
        Producto reparacion = new Producto(3, "Reparación", 40.00, "Taller", Collections.emptyList(), "H");
        CustomizedOrderItem item = new CustomizedOrderItem(reparacion, 2.0, "H");
        item.addExtraIngredient(new IngredientOption("Urgencia +20%", 0.0, IngredientOption.Type.ANADIR));

        // Act
        PrevisualizacionPrecio.Resultado preview = PrevisualizacionPrecio.calcular(item);

        // Assert
        assertEquals(96.00, preview.total, CENTIMO);
        assertEquals(engineTotal(item), preview.total, CENTIMO);
    }

    @Test
    public void volumenPreviewUsesTierUnitPriceAndMatchesEngineTotal() {
        // Arrange: 12 uds de 45 € -> tramo de 10 o más = 40,50 €/ud
        Producto aceite = new Producto(4, "Aceite caja 5L", 45.00, "Mayorista", Collections.emptyList());
        CustomizedOrderItem item = new CustomizedOrderItem(aceite, 12);

        // Act
        PrevisualizacionPrecio.Resultado preview = PrevisualizacionPrecio.calcular(item);

        // Assert
        assertEquals(40.50, preview.precioUnitario, CENTIMO);
        assertEquals(486.00, preview.total, CENTIMO);
        assertEquals(engineTotal(item), preview.total, CENTIMO);
    }

    @Test
    public void quitarIngredienteNoCambiaElPrecioPreviewNiElMotor() {
        // Arrange
        Producto bocata = new Producto(1, "Bocata", 3.50, "Bocadillos", Collections.emptyList());
        CustomizedOrderItem item = new CustomizedOrderItem(bocata, 1);
        item.addRemovedIngredient("Tomate");

        // Act
        PrevisualizacionPrecio.Resultado preview = PrevisualizacionPrecio.calcular(item);

        // Assert
        assertEquals(3.50, preview.total, CENTIMO);
        assertEquals(engineTotal(item), preview.total, CENTIMO);
    }

    /**
     * Total que da el motor cuando el artículo entra en el carrito: misma conversión que PedidoViewModel usa para
     * agregarCustomizedItem, y el mapa de precios de los extras y salsas que usa el carrito.
     */
    private static double engineTotal(CustomizedOrderItem item) {
        PedidoItem linea = new PedidoItem(item.getBaseProduct(), item.getQuantity());
        linea.setCantidadMedida(item.getDecimalQuantity());
        linea.setUnidadMedida(item.getUnit());
        linea.setTaraKg(item.getTareKg());
        linea.getIngredientesQuitados().addAll(item.getRemovedIngredients());
        Map<String, Double> precios = new HashMap<>();
        for (IngredientOption extra : item.getAddedIngredients()) {
            linea.getIngredientesExtra().add(extra.getName());
            precios.put(extra.getName(), extra.getExtraPrice());
        }
        for (IngredientOption sauce : item.getAddedSauces()) {
            linea.getSalsas().add(sauce.getName());
            precios.put(sauce.getName(), sauce.getExtraPrice());
        }
        List<PedidoItem> carrito = new ArrayList<>(Arrays.asList(linea));
        return new CalcularTotalUseCase().calcularTotal(carrito, precios);
    }
}
