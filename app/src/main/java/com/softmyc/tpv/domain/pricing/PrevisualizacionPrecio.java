package com.softmyc.tpv.domain.pricing;

import com.softmyc.tpv.domain.CustomizedOrderItem;
import com.softmyc.tpv.domain.IngredientOption;
import com.softmyc.tpv.domain.NetWeight;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.usecase.CalcularTotalUseCase;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Vista previa del precio de un artículo personalizado. Usa el mismo motor que el carrito ({@link CalcularTotalUseCase}),
 * así que el importe mostrado coincide con el que se cobra (NEG-14).
 */
public final class PrevisualizacionPrecio {

    /** Cifras de la línea tal como se muestran en el diálogo. */
    public static final class Resultado {
        /** Importe de la línea según el motor. Cero si el peso no supera la tara. */
        public final double total;
        /** Tarifa por unidad aplicada (tramos de volumen); en el resto de casos, el precio base. */
        public final double precioUnitario;
        /** Peso neto en venta al peso (kg); cero en el resto de casos. */
        public final double netoKg;
        /** Falso solo en venta al peso con un peso que no supera la tara. */
        public final boolean pesoValido;

        Resultado(double total, double precioUnitario, double netoKg, boolean pesoValido) {
            this.total = total;
            this.precioUnitario = precioUnitario;
            this.netoKg = netoKg;
            this.pesoValido = pesoValido;
        }
    }

    private PrevisualizacionPrecio() {
    }

    /**
     * Calcula la previsualización de un artículo personalizado.
     *
     * @throws com.softmyc.tpv.domain.CalculoPedidoException si el motor no puede calcular la línea.
     */
    public static Resultado calcular(CustomizedOrderItem item) {
        Producto producto = item.getBaseProduct();
        if (producto == null) {
            return new Resultado(0.0, 0.0, 0.0, true);
        }
        double precioBase = producto.getPrecio();
        boolean pesoValido = !producto.isWeightBased() || NetWeight.isValid(item.getDecimalQuantity(), item.getTareKg());
        double netoKg = producto.isWeightBased() ? NetWeight.net(item.getDecimalQuantity(), item.getTareKg()) : 0.0;
        double precioUnitario = producto.isVolumeTiered()
                ? VolumeTierSchedule.unitPriceFor(BigDecimal.valueOf(precioBase), BigDecimal.valueOf(item.getQuantity())).doubleValue()
                : precioBase;
        if (!pesoValido) {
            return new Resultado(0.0, precioUnitario, netoKg, false);
        }
        double total = totalDelMotor(item);
        return new Resultado(total, precioUnitario, netoKg, true);
    }

    /** Mismo camino que el carrito: la línea del pedido y el mapa nombre→precio de extras y salsas. */
    private static double totalDelMotor(CustomizedOrderItem item) {
        PedidoItem linea = item.toPedidoItem();
        Map<String, Double> precios = new HashMap<>();
        for (IngredientOption extra : item.getAddedIngredients()) precios.put(extra.getName(), extra.getExtraPrice());
        for (IngredientOption sauce : item.getAddedSauces()) precios.put(sauce.getName(), sauce.getExtraPrice());
        return new CalcularTotalUseCase().calcularTotal(Collections.singletonList(linea), precios);
    }
}
