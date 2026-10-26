package com.softmyc.tpv.domain.pricing;

import com.softmyc.tpv.domain.model.CalculatedPrice;
import com.softmyc.tpv.domain.model.CatalogItem;
import com.softmyc.tpv.domain.model.ItemModifier;

import java.math.BigDecimal;
import java.util.List;

/**
 * Patrón Strategy para el motor de tarificación universal.
 * Permite que diferentes sectores de negocio (hostelería, automoción,
 * comercio a granel, servicios por hora) apliquen algoritmos de cálculo
 * de precios específicos sin acoplarse al resto del sistema.
 */
public interface PricingStrategy {

    /**
     * Calcula el desglose financiero completo para un artículo comercial,
     * dada una cantidad y una lista de modificadores aplicados.
     *
     * @param item      Artículo de catálogo con sus propiedades base (precio, unidad, tipo de IVA).
     * @param quantity  Cantidad solicitada (unidades, kg, horas, etc.).
     * @param modifiers Lista de modificadores seleccionados (recargos, descuentos, taras, notas).
     * @return Objeto CalculatedPrice con desglose de importes y registro de auditoría.
     */
    CalculatedPrice calculate(CatalogItem item, BigDecimal quantity, List<ItemModifier> modifiers);
}
