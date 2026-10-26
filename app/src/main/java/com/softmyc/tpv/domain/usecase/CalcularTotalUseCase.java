package com.softmyc.tpv.domain.usecase;

import com.softmyc.tpv.domain.CalculoPedidoException;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.adapter.LegacyOrderAdapter;
import com.softmyc.tpv.domain.model.UniversalOrder;
import com.softmyc.tpv.domain.pricing.PoliticaRedondeo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Caso de uso: calcula el coste de los extras de un PedidoItem
 * y el total de una lista de items.
 *
 * Utiliza internamente el motor agnóstico de alta precisión de la Fase 2 (BigDecimal)
 * a través de LegacyOrderAdapter y CalculateUniversalTotalUseCase.
 */
public class CalcularTotalUseCase {

    private final CalculateUniversalTotalUseCase universalCalculator = new CalculateUniversalTotalUseCase();

    /**
     * Suma el precio por unidad de los ingredientes extra de un item (no multiplica por la cantidad). Es solo para
     * mostrar el extra de la línea en pantalla; el importe cobrado lo calcula {@link #calcularTotal}.
     *
     * No delega en el motor universal porque este no expone el extra por unidad: {@code CalculatedPrice} solo
     * ofrece el delta de modificadores de toda la línea (ya multiplicado por kg o unidades, con tara y descuentos
     * mezclados). Por eso se suma en {@link BigDecimal} con el mismo redondeo a céntimos que aplica
     * {@code LegacyOrderAdapter} a cada extra, de modo que lo mostrado coincide con lo cobrado.
     *
     * @param item           el item del pedido.
     * @param preciosExtras  mapa nombre→precio de los extras disponibles.
     * @return coste total de los extras de una unidad del item.
     */
    public double calcularExtras(PedidoItem item, Map<String, Double> preciosExtras) {
        if (item == null || preciosExtras == null) return 0.0;
        BigDecimal total = BigDecimal.ZERO;
        for (String ing : item.getIngredientesExtra()) {
            total = total.add(PoliticaRedondeo.aCentimos(preciosExtras.getOrDefault(ing, 0.0)));
        }
        return total.doubleValue();
    }

    /**
     * Calcula el importe total de una lista de items con precisión exacta BigDecimal.
     *
     * Las líneas con cantidad cero no aportan importe y se omiten (evita que una venta recuperada con
     * cantidad 0 rompa el cálculo). Cualquier dato incoherente se convierte en {@link CalculoPedidoException}
     * para que quien llama muestre un aviso en lugar de cerrarse.
     *
     * @param lista         lista de items del pedido.
     * @param preciosExtras mapa nombre→precio de los extras disponibles.
     * @return suma total del pedido con redondeo financiero estándar.
     * @throws CalculoPedidoException si alguna línea no se puede calcular.
     */
    public double calcularTotal(List<PedidoItem> lista, Map<String, Double> preciosExtras) {
        if (lista == null || lista.isEmpty()) return 0.0;
        try {
            List<PedidoItem> lineasConImporte = lineasConCantidad(lista);
            if (lineasConImporte.isEmpty()) return 0.0;
            UniversalOrder uOrder = LegacyOrderAdapter.toUniversalOrder("CALC_TEMP", lineasConImporte, "Efectivo", preciosExtras);
            CalculateUniversalTotalUseCase.OrderFinancialSummary summary = universalCalculator.execute(uOrder);
            return summary.getGrandTotal().doubleValue();
        } catch (RuntimeException e) {
            // Límite del caso de uso: cualquier fallo del motor (IllegalArgument, NPE por línea nula...)
            // se expone como error de dominio tipado.
            throw new CalculoPedidoException(
                    "No se puede calcular el importe del pedido: " + e.getMessage(), e);
        }
    }

    private static List<PedidoItem> lineasConCantidad(List<PedidoItem> lista) {
        List<PedidoItem> lineas = new ArrayList<>();
        for (PedidoItem item : lista) {
            if (item == null) {
                throw new CalculoPedidoException("Hay una línea vacía en el pedido", null);
            }
            if (LegacyOrderAdapter.cantidadEfectiva(item).signum() != 0) {
                lineas.add(item);
            }
        }
        return lineas;
    }
}
