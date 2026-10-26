package com.softmyc.tpv.domain.pricing;

import com.softmyc.tpv.domain.model.CatalogItem;
import com.softmyc.tpv.domain.model.PricingStrategyType;
import com.softmyc.tpv.domain.model.TaxCalculationMode;

import java.math.BigDecimal;

/**
 * Patrón Factory para resolver y suministrar la estrategia de precios adecuada
 * según la naturaleza comercial del artículo.
 */
public class PricingStrategyFactory {

    private final TaxCalculationMode defaultTaxMode;

    public PricingStrategyFactory() {
        this(TaxCalculationMode.INCLUSIVE);
    }

    public PricingStrategyFactory(TaxCalculationMode defaultTaxMode) {
        this.defaultTaxMode = (defaultTaxMode != null) ? defaultTaxMode : TaxCalculationMode.INCLUSIVE;
    }

    /**
     * Resuelve la estrategia adecuada para un artículo de catálogo.
     */
    public PricingStrategy createStrategy(CatalogItem item) {
        if (item == null) {
            return new StandardUnitPricingStrategy(defaultTaxMode);
        }

        PricingStrategyType type = item.getStrategyType();
        switch (type) {
            case WEIGHT:
                return new WeightPricingStrategy(defaultTaxMode);
            case TIERED_VOLUME:
                return new TieredVolumePricingStrategy(VolumeTierSchedule.tiersFor(item.getBasePrice()), defaultTaxMode);
            case TIME:
                return new TimePricingStrategy(defaultTaxMode, BigDecimal.ZERO);
            case UNIT:
            default:
                return new StandardUnitPricingStrategy(defaultTaxMode);
        }
    }
}
