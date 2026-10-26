package com.softmyc.tpv.domain.pricing;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Tarifa por volumen por defecto (mayorista): única fuente de verdad para lo que muestra el
 * diálogo de producto y lo que cobra el motor de precios.
 *
 * Tramos: 1-9 uds a precio base, 10-49 uds con un 10 % de descuento y 50 o más con un 20 %.
 */
public final class VolumeTierSchedule {

    public static final int TIER_2_FROM = 10;
    public static final int TIER_3_FROM = 50;
    private static final BigDecimal TIER_2_FACTOR = new BigDecimal("0.90");
    private static final BigDecimal TIER_3_FACTOR = new BigDecimal("0.80");

    private VolumeTierSchedule() { }

    public static List<TieredVolumePricingStrategy.Tier> tiersFor(BigDecimal basePrice) {
        return Arrays.asList(
                new TieredVolumePricingStrategy.Tier(BigDecimal.ZERO, new BigDecimal(TIER_2_FROM - 1), basePrice),
                new TieredVolumePricingStrategy.Tier(new BigDecimal(TIER_2_FROM), new BigDecimal(TIER_3_FROM - 1),
                        basePrice.multiply(TIER_2_FACTOR)),
                new TieredVolumePricingStrategy.Tier(new BigDecimal(TIER_3_FROM), null,
                        basePrice.multiply(TIER_3_FACTOR)));
    }

    /** Precio unitario aplicable a la cantidad indicada. */
    public static BigDecimal unitPriceFor(BigDecimal basePrice, BigDecimal quantity) {
        if (quantity.compareTo(new BigDecimal(TIER_3_FROM)) >= 0) {
            return PoliticaRedondeo.aCentimos(basePrice.multiply(TIER_3_FACTOR));
        }
        if (quantity.compareTo(new BigDecimal(TIER_2_FROM)) >= 0) {
            return PoliticaRedondeo.aCentimos(basePrice.multiply(TIER_2_FACTOR));
        }
        return PoliticaRedondeo.aCentimos(basePrice);
    }
}
