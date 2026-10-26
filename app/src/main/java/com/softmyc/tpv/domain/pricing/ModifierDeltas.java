package com.softmyc.tpv.domain.pricing;

import com.softmyc.tpv.domain.model.ItemModifier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Semántica única de los modificadores monetarios para todas las estrategias (NEG-08):
 *
 *  - Recargo o descuento fijo: importe POR UNIDAD FACTURADA (unidad, kg netos o hora facturable).
 *    Se multiplica por la cantidad facturada, igual que el precio base.
 *  - Porcentual: porcentaje del precio unitario de la estrategia, también multiplicado por la cantidad.
 *  - Tara, exclusión y nota cualitativa: no alteran el importe.
 *
 * Cada modificador se redondea a céntimos antes de sumarse.
 */
final class ModifierDeltas {

    private ModifierDeltas() { }

    /**
     * @param modifiers           modificadores aplicados (puede ser null).
     * @param precioUnitario      precio unitario de la estrategia (por unidad, kg o hora).
     * @param cantidadFacturada   cantidad facturada sobre la que se multiplica el importe por unidad.
     * @return suma de deltas en euros, con dos decimales.
     */
    static BigDecimal totalPorUnidadFacturada(
            List<ItemModifier> modifiers, BigDecimal precioUnitario, BigDecimal cantidadFacturada) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (modifiers == null) return total;
        for (ItemModifier mod : modifiers) {
            BigDecimal deltaPorUnidad = mod.calculateMonetaryDelta(precioUnitario);
            total = total.add(deltaPorUnidad.multiply(cantidadFacturada).setScale(2, RoundingMode.HALF_UP));
        }
        return total;
    }
}
