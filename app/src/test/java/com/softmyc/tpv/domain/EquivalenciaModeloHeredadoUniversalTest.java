package com.softmyc.tpv.domain;

import static org.junit.Assert.assertEquals;

import com.softmyc.tpv.domain.usecase.CalcularTotalUseCase;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Importes de artículos personalizados calculados por el motor universal ({@link CalcularTotalUseCase} ->
 * LegacyOrderAdapter -> motor BigDecimal), con valores esperados literales. Antes se comparaban con el
 * subtotal double del modelo heredado, retirado.
 *
 * Comportamiento esperado del motor universal frente al heredado (NEG-05/NEG-08 y PoliticaRedondeo):
 * 1) redondea el precio unitario HALF_UP a céntimos antes de multiplicar;
 * 2) en venta al peso multiplica los extras por kg neto (NEG-08).
 */
public class EquivalenciaModeloHeredadoUniversalTest {

    private final CalcularTotalUseCase calculador = new CalcularTotalUseCase();

    private static Producto plato(double precio) {
        return new Producto(1, "Plato", precio, "Comida", Collections.emptyList());
    }

    private static Map<String, Double> precios(IngredientOption... opciones) {
        Map<String, Double> mapa = new HashMap<>();
        for (IngredientOption o : opciones) mapa.put(o.getName(), o.getExtraPrice());
        return mapa;
    }

    private double universal(CustomizedOrderItem item, IngredientOption... opciones) {
        return calculador.calcularTotal(Collections.singletonList(item.toPedidoItem()), precios(opciones));
    }

    @Test
    public void unidadesSinPersonalizarSumanPrecioPorCantidad() {
        CustomizedOrderItem item = new CustomizedOrderItem(plato(8.50), 3);

        assertEquals(25.50, universal(item), 0.0001);
    }

    @Test
    public void extrasEnUnidadesSeMultiplicanPorCantidadEnAmbosCaminos() {
        IngredientOption queso = new IngredientOption("Queso", 1.20, IngredientOption.Type.ANADIR);
        IngredientOption bacon = new IngredientOption("Bacon", 0.75, IngredientOption.Type.ANADIR);
        CustomizedOrderItem item = new CustomizedOrderItem(plato(9.90), 4);
        item.addExtraIngredient(queso);
        item.addExtraIngredient(bacon);

                assertEquals((9.90 + 1.20 + 0.75) * 4, universal(item, queso, bacon), 0.0001);
    }

    @Test
    public void salsaConPrecioCoincide() {
        IngredientOption alioli = new IngredientOption("Alioli", 0.50, IngredientOption.Type.SALSA);
        CustomizedOrderItem item = new CustomizedOrderItem(plato(7.00), 2);
        item.addSauce(alioli);

        assertEquals(15.00, universal(item, alioli), 0.0001); // (7,00 + 0,50) x 2
    }

    @Test
    public void salsaGratisYIngredienteQuitadoNoAportanImporte() {
        IngredientOption ketchup = new IngredientOption("Ketchup", 0.0, IngredientOption.Type.SALSA);
        CustomizedOrderItem item = new CustomizedOrderItem(plato(5.00), 2);
        item.addSauce(ketchup);
        item.addRemovedIngredient("Cebolla");

                assertEquals(10.00, universal(item, ketchup), 0.0001);
    }

    @Test
    public void totalDeVariasLineasEsLaSumaDeLosSubtotalesHeredados() {
        IngredientOption queso = new IngredientOption("Queso", 1.20, IngredientOption.Type.ANADIR);
        CustomizedOrderItem a = new CustomizedOrderItem(plato(6.30), 2);
        a.addExtraIngredient(queso);
        CustomizedOrderItem b = new CustomizedOrderItem(plato(2.10), 5);

        double total = calculador.calcularTotal(
                java.util.Arrays.asList(a.toPedidoItem(), b.toPedidoItem()), precios(queso));

        assertEquals(25.50, total, 0.0001); // 2 x (6,30 + 1,20) + 5 x 2,10 = 15,00 + 10,50
    }

    @Test
    public void ventaAlPesoSinExtrasConTaraCoincide() {
        Producto fruta = new Producto(20, "Manzanas", 2.40, "Frutería", Collections.emptyList(), "KG");
        CustomizedOrderItem item = new CustomizedOrderItem(fruta, 1.850, "KG");
        item.setTareKg(0.150);

        assertEquals(4.08, universal(item), 0.0001);
    }

    @Test
    public void discrepancia_ventaAlPesoConExtraElUniversalMultiplicaPorKgNetos() {
        Producto fruta = new Producto(20, "Manzanas", 2.00, "Frutería", Collections.emptyList(), "KG");
        IngredientOption bolsa = new IngredientOption("Bolsa", 0.10, IngredientOption.Type.ANADIR);
        CustomizedOrderItem item = new CustomizedOrderItem(fruta, 2.0, "KG");
        item.addExtraIngredient(bolsa);

        // Motor universal (NEG-08): el extra se multiplica por los kg netos, (2.00 + 0.10) * 2.
        // El modelo heredado, ya retirado, sumaba el extra una sola vez (4.10).
        assertEquals(4.20, universal(item, bolsa), 0.0001);
    }

    @Test
    public void discrepancia_redondeoElUniversalRedondeaElPrecioUnitarioAntesDeMultiplicar() {
        IngredientOption extra = new IngredientOption("Extra", 0.333, IngredientOption.Type.ANADIR);
        CustomizedOrderItem item = new CustomizedOrderItem(plato(1.00), 3);
        item.addExtraIngredient(extra);

        // Motor universal: el precio unitario se redondea HALF_UP a céntimos antes de multiplicar,
        // (1.00 + 0.33) * 3 = 3.99. El modelo heredado, ya retirado, no redondeaba (3.999).
        assertEquals(3.99, universal(item, extra), 0.0001);
    }

    @Test
    public void totalUniversalSiempreTieneComoMaximoDosDecimales() {
        IngredientOption extra = new IngredientOption("Extra", 0.07, IngredientOption.Type.ANADIR);
        for (int cantidad = 1; cantidad <= 50; cantidad++) {
            CustomizedOrderItem item = new CustomizedOrderItem(plato(3.33), cantidad);
            item.addExtraIngredient(extra);

            BigDecimal total = BigDecimal.valueOf(universal(item, extra));

            assertEquals(total.setScale(2, RoundingMode.HALF_UP).doubleValue(), total.doubleValue(), 0.0);
        }
    }
}
