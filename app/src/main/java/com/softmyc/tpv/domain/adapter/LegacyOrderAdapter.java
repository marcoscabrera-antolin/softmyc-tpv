package com.softmyc.tpv.domain.adapter;

import com.softmyc.tpv.domain.CategoriaHeuristicas;
import com.softmyc.tpv.domain.IngredientOption;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.model.CatalogItem;
import com.softmyc.tpv.domain.model.ItemModifier;
import com.softmyc.tpv.domain.model.MeasurementUnit;
import com.softmyc.tpv.domain.model.PaymentMethod;
import com.softmyc.tpv.domain.model.PricingStrategyType;
import com.softmyc.tpv.domain.model.TaxCalculationMode;
import com.softmyc.tpv.domain.model.TaxRate;
import com.softmyc.tpv.domain.model.UniversalOrder;
import com.softmyc.tpv.domain.model.UniversalOrderItem;
import com.softmyc.tpv.domain.pricing.PoliticaRedondeo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de compatibilidad (Adapter Pattern) entre los modelos gastronómicos
 * de la Fase 1 (Producto, PedidoItem, IngredientOption) y el motor universal
 * agnóstico de la Fase 2 (CatalogItem, UniversalOrderItem, UniversalOrder).
 *
 * Permite que toda la interfaz gráfica y los datos existentes de hostelería
 * sigan funcionando transparentemente mientras se benefician de la alta
 * precisión numérica (BigDecimal) y las reglas de negocio de la Fase 2.
 */
public class LegacyOrderAdapter {

    /**
     * Convierte un Producto tradicional de hostelería o multinegocio a CatalogItem agnóstico.
     */
    public static CatalogItem toCatalogItem(Producto producto) {
        if (producto == null) return null;

        BigDecimal basePrice = PoliticaRedondeo.aCentimos(producto.getPrecio());

        String cat = (producto.getCategoria() != null && !producto.getCategoria().trim().isEmpty())
                ? producto.getCategoria() : "GENERAL";

        MeasurementUnit unit = MeasurementUnit.UNIT;
        TaxRate tax = CategoriaHeuristicas.IVA_DEFECTO; // Default hostelería 10%
        PricingStrategyType strategy = PricingStrategyType.UNIT;

        if (producto.isWeightBased()) {
            unit = MeasurementUnit.WEIGHT_KG;
            strategy = PricingStrategyType.WEIGHT;
            tax = CategoriaHeuristicas.IVA_PESO; // 4% IVA superreducido fruta/verdura en España
        } else if (producto.isTimeBased()) {
            unit = MeasurementUnit.TIME_HOUR;
            strategy = PricingStrategyType.TIME;
            tax = CategoriaHeuristicas.IVA_HORAS; // 21% IVA taller / servicios profesionales
        } else if (producto.isVolumeTiered()) {
            unit = MeasurementUnit.UNIT;
            strategy = PricingStrategyType.TIERED_VOLUME;
            tax = CategoriaHeuristicas.IVA_VOLUMEN;
        }

        return CatalogItem.builder(String.valueOf(producto.getCodigo()), producto.getNombre(), basePrice)
                .category(cat)
                .unit(unit)
                .tax(tax)
                .strategy(strategy)
                .build();
    }

    /**
     * Convierte una opción de ingrediente a un ItemModifier agnóstico.
     */
    public static ItemModifier toItemModifier(IngredientOption option) {
        if (option == null) return null;

        BigDecimal price = PoliticaRedondeo.aCentimos(option.getExtraPrice());

        switch (option.getType()) {
            case ANADIR:
                return ItemModifier.fixedSurcharge(option.getName(), price);
            case QUITAR:
                return ItemModifier.exclusion(option.getName());
            case SALSA:
                return price.compareTo(BigDecimal.ZERO) > 0
                        ? ItemModifier.fixedSurcharge(option.getName(), price)
                        : ItemModifier.qualitative(option.getName());
            default:
                return ItemModifier.qualitative(option.getName());
        }
    }

    /**
     * Cantidad con la que se factura una línea: el peso o las horas reales si la línea no es en unidades
     * (1.850 kg, 2.5 h), o las unidades enteras en el resto. Puede ser cero o negativa; quien calcula
     * decide cómo tratarla.
     */
    public static BigDecimal cantidadEfectiva(PedidoItem legacyItem) {
        if (!"UD".equalsIgnoreCase(legacyItem.getUnidadMedida()) && legacyItem.getCantidadMedida() > 0) {
            return BigDecimal.valueOf(legacyItem.getCantidadMedida()).setScale(3, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(legacyItem.getCantidad());
    }

    /**
     * Convierte un PedidoItem tradicional en un UniversalOrderItem.
     *
     * @param legacyItem     item tradicional con cantidades e ingredientes.
     * @param preciosExtras  mapa nombre->precio de extras para resolver suplementos.
     */
    public static UniversalOrderItem toUniversalOrderItem(
            PedidoItem legacyItem,
            Map<String, Double> preciosExtras
    ) {
        if (legacyItem == null) return null;

        CatalogItem catalogItem = toCatalogItem(legacyItem.getProducto());
        
        BigDecimal quantity = cantidadEfectiva(legacyItem);

        List<ItemModifier> modifiers = new ArrayList<>();

        // Deducción de tara si aplica (venta al peso en frutería/granel)
        if (legacyItem.getTaraKg() > 0.0) {
            BigDecimal tara = BigDecimal.valueOf(legacyItem.getTaraKg()).setScale(3, RoundingMode.HALF_UP);
            modifiers.add(ItemModifier.tareDeduction("Tara envase", tara));
        }

        // Ingredientes quitados -> EXCLUSION
        for (String sin : legacyItem.getIngredientesQuitados()) {
            modifiers.add(ItemModifier.exclusion(sin));
        }

        // Ingredientes extra / suplementos -> SURCHARGE_FIXED o PERCENTAGE
        for (String extra : legacyItem.getIngredientesExtra()) {
            double priceDouble = (preciosExtras != null) ? preciosExtras.getOrDefault(extra, 0.0) : 0.0;
            BigDecimal price = PoliticaRedondeo.aCentimos(priceDouble);
            // Porcentaje explícito en el nombre ("Urgencia +20%"): se usa ese valor, nunca un 20 % fijo (NEG-05).
            BigDecimal porcentaje = PorcentajeExtra.extraer(extra);
            if (porcentaje == null) {
                modifiers.add(ItemModifier.fixedSurcharge(extra, price));
            } else if (porcentaje.signum() < 0) {
                modifiers.add(ItemModifier.percentageDiscount(extra, porcentaje.negate()));
            } else {
                modifiers.add(ItemModifier.percentageSurcharge(extra, porcentaje));
            }
        }

        // Salsas extra -> SURCHARGE_FIXED si tiene precio, o QUALITATIVE_NOTE
        for (String salsa : legacyItem.getSalsas()) {
            double priceDouble = (preciosExtras != null) ? preciosExtras.getOrDefault(salsa, 0.0) : 0.0;
            BigDecimal price = PoliticaRedondeo.aCentimos(priceDouble);
            if (price.compareTo(BigDecimal.ZERO) > 0) {
                modifiers.add(ItemModifier.fixedSurcharge(salsa, price));
            } else {
                modifiers.add(ItemModifier.qualitative(salsa));
            }
        }

        return UniversalOrderItem.builder(catalogItem, quantity)
                .modifiers(modifiers)
                .notes(legacyItem.getDetallesVisuales())
                .build();
    }

    /**
     * Convierte una lista completa de pedidos tradicionales a un UniversalOrder.
     */
    public static UniversalOrder toUniversalOrder(
            String orderId,
            List<PedidoItem> legacyItems,
            String metodoPago,
            Map<String, Double> preciosExtras
    ) {
        List<UniversalOrderItem> universalItems = new ArrayList<>();
        String sector = "HOSPITALITY";

        if (legacyItems != null) {
            for (PedidoItem it : legacyItems) {
                if (it.getProducto() != null) {
                    if (it.getProducto().isWeightBased()) sector = "PRODUCE";
                    else if (it.getProducto().isTimeBased()) sector = "AUTO_REPAIR";
                    else if (it.getProducto().isVolumeTiered()) sector = "WHOLESALE";
                }
                universalItems.add(toUniversalOrderItem(it, preciosExtras));
            }
        }

        PaymentMethod method = "Tarjeta".equalsIgnoreCase(metodoPago)
                ? PaymentMethod.CARD : PaymentMethod.CASH;

        return new UniversalOrder(
                orderId != null ? orderId : "ORD-" + System.currentTimeMillis(),
                System.currentTimeMillis(),
                sector,
                "",
                universalItems,
                method,
                TaxCalculationMode.INCLUSIVE
        );
    }
}
