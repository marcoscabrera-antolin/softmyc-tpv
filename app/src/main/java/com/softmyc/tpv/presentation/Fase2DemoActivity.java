package com.softmyc.tpv.presentation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.model.CatalogItem;
import com.softmyc.tpv.domain.model.ItemModifier;
import com.softmyc.tpv.domain.model.MeasurementUnit;
import com.softmyc.tpv.domain.model.PaymentMethod;
import com.softmyc.tpv.domain.model.PricingStrategyType;
import com.softmyc.tpv.domain.model.TaxCalculationMode;
import com.softmyc.tpv.domain.model.TaxRate;
import com.softmyc.tpv.domain.model.UniversalOrder;
import com.softmyc.tpv.domain.model.UniversalOrderItem;
import com.softmyc.tpv.domain.pricing.PricingStrategy;
import com.softmyc.tpv.domain.pricing.StandardUnitPricingStrategy;
import com.softmyc.tpv.domain.pricing.TieredVolumePricingStrategy;
import com.softmyc.tpv.domain.pricing.TimePricingStrategy;
import com.softmyc.tpv.domain.pricing.WeightPricingStrategy;
import com.softmyc.tpv.domain.service.TextReceiptFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Actividad de Demostración Visual de la Fase 2: Motor Comercial Universal Desacoplado.
 * Permite al usuario y al tribunal de evaluación experimentar en tiempo real cómo la misma
 * arquitectura computa reglas de negocio, estrategias de tarificación y tickets térmicos
 * para cuatro sectores comerciales totalmente distintos:
 * 1. Taller Mecánico (Automoción / Servicios B2B)
 * 2. Frutería y Comercio al Peso (Granel con Taras)
 * 3. Restauración / Fast-Food (Menú con Salsas e IVA Múltiple)
 * 4. Mayorista (Escalado de Precios por Tramos de Volumen)
 */
public class Fase2DemoActivity extends BaseActivity {

    private enum SectorType {
        TALLER, FRUTERIA, RESTAURANTE, MAYORISTA
    }

    private SectorType currentSector = SectorType.TALLER;

    // Vistas principales
    private MaterialButton tabTaller, tabFruteria, tabRestaurante, tabMayorista;
    private TextView tvStrategyBadge, tvTaxModeBadge, tvSectorDescription;
    private LinearLayout itemsContainer, taxBreakdownContainer;
    private TextView tvLiveSubtotal, tvLiveTotal, tvThermalReceipt;

    // Estado del sector activo
    private final List<SimulatedItemState> activeItems = new ArrayList<>();
    private final TextReceiptFormatter receiptFormatter = new TextReceiptFormatter();

    private static class SimulatedItemState {
        CatalogItem item;
        BigDecimal quantity;
        PricingStrategy strategy;
        List<ItemModifier> availableModifiers = new ArrayList<>();
        List<ItemModifier> selectedModifiers = new ArrayList<>();

        SimulatedItemState(CatalogItem item, BigDecimal initialQty, PricingStrategy strategy, List<ItemModifier> mods) {
            this.item = item;
            this.quantity = initialQty;
            this.strategy = strategy;
            if (mods != null) {
                this.availableModifiers.addAll(mods);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fase2_demo);

        initViews();
        setupListeners();

        // Cargar sector inicial (Taller Mecánico)
        selectSector(SectorType.TALLER);
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnResetDemo).setOnClickListener(v -> selectSector(currentSector));

        tabTaller = findViewById(R.id.tabTaller);
        tabFruteria = findViewById(R.id.tabFruteria);
        tabRestaurante = findViewById(R.id.tabRestaurante);
        tabMayorista = findViewById(R.id.tabMayorista);

        tvStrategyBadge = findViewById(R.id.tvStrategyBadge);
        tvTaxModeBadge = findViewById(R.id.tvTaxModeBadge);
        tvSectorDescription = findViewById(R.id.tvSectorDescription);

        itemsContainer = findViewById(R.id.itemsContainer);
        taxBreakdownContainer = findViewById(R.id.taxBreakdownContainer);
        tvLiveSubtotal = findViewById(R.id.tvLiveSubtotal);
        tvLiveTotal = findViewById(R.id.tvLiveTotal);
        tvThermalReceipt = findViewById(R.id.tvThermalReceipt);
    }

    private void setupListeners() {
        tabTaller.setOnClickListener(v -> selectSector(SectorType.TALLER));
        tabFruteria.setOnClickListener(v -> selectSector(SectorType.FRUTERIA));
        tabRestaurante.setOnClickListener(v -> selectSector(SectorType.RESTAURANTE));
        tabMayorista.setOnClickListener(v -> selectSector(SectorType.MAYORISTA));
    }

    private void selectSector(SectorType sector) {
        this.currentSector = sector;
        updateTabStyles();
        loadSectorCatalog(sector);
        renderCatalogItems();
        recalculateOrder();
    }

    private void updateTabStyles() {
        int activeBg = getResources().getColor(R.color.primary_brand);
        int inactiveStroke = getResources().getColor(R.color.surface_light);

        setTabActive(tabTaller, currentSector == SectorType.TALLER, activeBg, inactiveStroke);
        setTabActive(tabFruteria, currentSector == SectorType.FRUTERIA, activeBg, inactiveStroke);
        setTabActive(tabRestaurante, currentSector == SectorType.RESTAURANTE, activeBg, inactiveStroke);
        setTabActive(tabMayorista, currentSector == SectorType.MAYORISTA, activeBg, inactiveStroke);
    }

    private void setTabActive(MaterialButton tab, boolean isActive, int activeColor, int inactiveStroke) {
        if (isActive) {
            tab.setBackgroundColor(activeColor);
            tab.setTextColor(getResources().getColor(R.color.white));
            tab.setStrokeWidth(0);
        } else {
            tab.setBackgroundColor(0); // Transparente
            tab.setTextColor(getResources().getColor(R.color.text_primary));
            tab.setStrokeColorResource(R.color.surface_light);
            tab.setStrokeWidth(2);
        }
    }

    private void loadSectorCatalog(SectorType sector) {
        activeItems.clear();

        switch (sector) {
            case TALLER:
                tvStrategyBadge.setText(getString(R.string.f2_taller_estrategia));
                tvTaxModeBadge.setText(getString(R.string.f2_b2b_regimen_iva21));
                tvSectorDescription.setText(getString(R.string.f2_taller_descripcion));

                // 1. Mano de Obra (Horas)
                CatalogItem labor = CatalogItem.builder("SERV-MEC-01", "Mano de Obra Especializada", new BigDecimal("40.00"))
                        .category("SERVICIOS")
                        .unit(MeasurementUnit.TIME_HOUR)
                        .tax(TaxRate.GENERAL_21)
                        .strategy(PricingStrategyType.TIME)
                        .build();
                List<ItemModifier> laborMods = Arrays.asList(
                        ItemModifier.percentageSurcharge("Urgencia 24h", new BigDecimal("20.00")),
                        ItemModifier.fixedSurcharge("Tasa Gestión Residuos", new BigDecimal("5.00"))
                );
                PricingStrategy timeStrategy = new TimePricingStrategy(TaxCalculationMode.EXCLUSIVE, new BigDecimal("0.25"));
                activeItems.add(new SimulatedItemState(labor, new BigDecimal("1.50"), timeStrategy, laborMods));

                // 2. Pastillas de freno
                CatalogItem brakePads = CatalogItem.builder("REP-FRENOS-01", "Juego Pastillas Freno Bosch", new BigDecimal("45.00"))
                        .category("REPUESTOS")
                        .unit(MeasurementUnit.UNIT)
                        .tax(TaxRate.GENERAL_21)
                        .build();
                PricingStrategy unitB2B = new StandardUnitPricingStrategy(TaxCalculationMode.EXCLUSIVE);
                activeItems.add(new SimulatedItemState(brakePads, new BigDecimal("2.00"), unitB2B, Collections.singletonList(
                        ItemModifier.qualitative("Instalar en eje delantero")
                )));

                // 3. Discos de freno
                CatalogItem brakeDiscs = CatalogItem.builder("REP-DISC-01", "Discos de Freno Ventilados", new BigDecimal("75.00"))
                        .category("REPUESTOS")
                        .unit(MeasurementUnit.UNIT)
                        .tax(TaxRate.GENERAL_21)
                        .build();
                activeItems.add(new SimulatedItemState(brakeDiscs, BigDecimal.ZERO, unitB2B, Collections.emptyList()));
                break;

            case FRUTERIA:
                tvStrategyBadge.setText(getString(R.string.f2_fruteria_estrategia));
                tvTaxModeBadge.setText(getString(R.string.f2_fruteria_regimen));
                tvSectorDescription.setText(getString(R.string.f2_fruteria_descripcion));

                PricingStrategy weightStrategy = new WeightPricingStrategy(TaxCalculationMode.INCLUSIVE);

                // 1. Manzanas
                CatalogItem apples = CatalogItem.builder("FRUT-01", "Manzana Golden Nacional", new BigDecimal("2.20"))
                        .unit(MeasurementUnit.WEIGHT_KG)
                        .tax(TaxRate.SUPER_REDUCED_4)
                        .strategy(PricingStrategyType.WEIGHT)
                        .build();
                List<ItemModifier> appleMods = Arrays.asList(
                        ItemModifier.tareDeduction("Tara Caja Madera", new BigDecimal("0.200")),
                        ItemModifier.tareDeduction("Tara Bolsa Reutilizable", new BigDecimal("0.050"))
                );
                activeItems.add(new SimulatedItemState(apples, new BigDecimal("1.450"), weightStrategy, appleMods));

                // 2. Plátanos
                CatalogItem bananas = CatalogItem.builder("FRUT-02", "Plátano de Canarias Extra", new BigDecimal("1.85"))
                        .unit(MeasurementUnit.WEIGHT_KG)
                        .tax(TaxRate.SUPER_REDUCED_4)
                        .strategy(PricingStrategyType.WEIGHT)
                        .build();
                activeItems.add(new SimulatedItemState(bananas, new BigDecimal("0.850"), weightStrategy, Collections.emptyList()));

                // 3. Frutos secos granel (IVA 10%)
                CatalogItem nuts = CatalogItem.builder("FRUT-03", "Nueces Peladas a Granel", new BigDecimal("12.00"))
                        .unit(MeasurementUnit.WEIGHT_KG)
                        .tax(TaxRate.REDUCED_10)
                        .strategy(PricingStrategyType.WEIGHT)
                        .build();
                activeItems.add(new SimulatedItemState(nuts, new BigDecimal("0.300"), weightStrategy, Collections.singletonList(
                        ItemModifier.tareDeduction("Bote Cristal Cliente", new BigDecimal("0.120"))
                )));
                break;

            case RESTAURANTE:
                tvStrategyBadge.setText(getString(R.string.f2_restaurante_estrategia));
                tvTaxModeBadge.setText(getString(R.string.f2_restaurante_regimen));
                tvSectorDescription.setText(getString(R.string.f2_restaurante_descripcion));

                PricingStrategy unitB2C = new StandardUnitPricingStrategy(TaxCalculationMode.INCLUSIVE);

                // 1. Burger Angus
                CatalogItem burger = CatalogItem.builder("REST-01", "Hamburguesa Buey Angus", new BigDecimal("9.50"))
                        .category("HAMBURGUESAS")
                        .unit(MeasurementUnit.UNIT)
                        .tax(TaxRate.REDUCED_10)
                        .build();
                List<ItemModifier> burgerMods = Arrays.asList(
                        ItemModifier.fixedSurcharge("Extra Bacon", new BigDecimal("1.20")),
                        ItemModifier.fixedSurcharge("Extra Cheddar", new BigDecimal("1.00")),
                        ItemModifier.exclusion("Cebolla"),
                        ItemModifier.qualitative("Punto: Muy hecho")
                );
                activeItems.add(new SimulatedItemState(burger, new BigDecimal("2.00"), unitB2C, burgerMods));

                // 2. Cerveza Artesana (IVA 21%)
                CatalogItem beer = CatalogItem.builder("BEV-01", "Cerveza Artesana IPA", new BigDecimal("3.80"))
                        .category("BEBIDAS")
                        .unit(MeasurementUnit.UNIT)
                        .tax(TaxRate.GENERAL_21)
                        .build();
                activeItems.add(new SimulatedItemState(beer, new BigDecimal("2.00"), unitB2C, Collections.emptyList()));

                // 3. Patatas Rústicas
                CatalogItem fries = CatalogItem.builder("SIDE-01", "Patatas Rústicas Especiadas", new BigDecimal("3.50"))
                        .category("COMPLEMENTOS")
                        .unit(MeasurementUnit.UNIT)
                        .tax(TaxRate.REDUCED_10)
                        .build();
                activeItems.add(new SimulatedItemState(fries, new BigDecimal("1.00"), unitB2C, Collections.singletonList(
                        ItemModifier.fixedSurcharge("Salsa Trufa", new BigDecimal("0.80"))
                )));
                break;

            case MAYORISTA:
                tvStrategyBadge.setText(getString(R.string.f2_mayorista_estrategia));
                tvTaxModeBadge.setText(getString(R.string.f2_b2b_regimen_iva21));
                tvSectorDescription.setText(getString(R.string.f2_mayorista_descripcion));

                List<TieredVolumePricingStrategy.Tier> tiers = Arrays.asList(
                        new TieredVolumePricingStrategy.Tier(new BigDecimal("1"), new BigDecimal("9"), new BigDecimal("15.00")),
                        new TieredVolumePricingStrategy.Tier(new BigDecimal("10"), new BigDecimal("49"), new BigDecimal("12.50")),
                        new TieredVolumePricingStrategy.Tier(new BigDecimal("50"), null, new BigDecimal("9.90"))
                );
                PricingStrategy bulkStrategy = new TieredVolumePricingStrategy(tiers, TaxCalculationMode.EXCLUSIVE);

                CatalogItem batteries = CatalogItem.builder("VOL-01", "Pack Baterías Industriales Li-Ion", new BigDecimal("15.00"))
                        .unit(MeasurementUnit.UNIT)
                        .tax(TaxRate.GENERAL_21)
                        .strategy(PricingStrategyType.TIERED_VOLUME)
                        .build();
                List<ItemModifier> bulkMods = Collections.singletonList(
                        ItemModifier.percentageDiscount("Pronto Pago", new BigDecimal("2.00"))
                );
                activeItems.add(new SimulatedItemState(batteries, new BigDecimal("25.00"), bulkStrategy, bulkMods));

                CatalogItem cable = CatalogItem.builder("VOL-02", "Rollo Cable Cobre 100m", new BigDecimal("60.00"))
                        .unit(MeasurementUnit.UNIT)
                        .tax(TaxRate.GENERAL_21)
                        .build();
                activeItems.add(new SimulatedItemState(cable, new BigDecimal("4.00"), bulkStrategy, Collections.emptyList()));
                break;
        }
    }

    private void renderCatalogItems() {
        itemsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (SimulatedItemState state : activeItems) {
            View card = inflater.inflate(R.layout.item_fase2_catalog_card, itemsContainer, false);

            TextView tvItemName = card.findViewById(R.id.tvItemName);
            TextView tvItemSubtitle = card.findViewById(R.id.tvItemSubtitle);
            TextView tvQuantity = card.findViewById(R.id.tvQuantity);
            MaterialButton btnMinus = card.findViewById(R.id.btnMinus);
            MaterialButton btnPlus = card.findViewById(R.id.btnPlus);
            LinearLayout quickStepsContainer = card.findViewById(R.id.quickStepsContainer);
            ChipGroup chipGroupModifiers = card.findViewById(R.id.chipGroupModifiers);

            tvItemName.setText(state.item.getName());
            tvItemSubtitle.setText(String.format(Locale.getDefault(), "%.2f € / %s (%s)",
                    state.item.getBasePrice(), state.item.getMeasurementUnit().getCode(), state.item.getTaxRate().getName()));

            updateQuantityText(tvQuantity, state);

            // Pasos rápidos si permite decimales (ej: KG u Horas)
            if (state.item.getMeasurementUnit().isFractionalAllowed()) {
                quickStepsContainer.setVisibility(View.VISIBLE);
                card.findViewById(R.id.btnStepSmall).setOnClickListener(v -> {
                    state.quantity = state.quantity.add(new BigDecimal("0.25"));
                    updateQuantityText(tvQuantity, state);
                    recalculateOrder();
                });
                card.findViewById(R.id.btnStepMedium).setOnClickListener(v -> {
                    state.quantity = state.quantity.add(new BigDecimal("0.50"));
                    updateQuantityText(tvQuantity, state);
                    recalculateOrder();
                });
                card.findViewById(R.id.btnStepLarge).setOnClickListener(v -> {
                    state.quantity = state.quantity.add(new BigDecimal("1.00"));
                    updateQuantityText(tvQuantity, state);
                    recalculateOrder();
                });
            } else {
                quickStepsContainer.setVisibility(View.GONE);
            }

            // Botones +/-
            btnPlus.setOnClickListener(v -> {
                BigDecimal step = state.item.getMeasurementUnit().isFractionalAllowed() ? new BigDecimal("0.50") : BigDecimal.ONE;
                state.quantity = state.quantity.add(step);
                updateQuantityText(tvQuantity, state);
                recalculateOrder();
            });

            btnMinus.setOnClickListener(v -> {
                BigDecimal step = state.item.getMeasurementUnit().isFractionalAllowed() ? new BigDecimal("0.50") : BigDecimal.ONE;
                if (state.quantity.compareTo(step) >= 0) {
                    state.quantity = state.quantity.subtract(step);
                    updateQuantityText(tvQuantity, state);
                    recalculateOrder();
                }
            });

            // Modificadores dinámicos
            chipGroupModifiers.removeAllViews();
            for (ItemModifier mod : state.availableModifiers) {
                Chip chip = new Chip(this);
                chip.setText(mod.toString());
                chip.setCheckable(true);
                chip.setChecked(state.selectedModifiers.contains(mod));
                chip.setChipBackgroundColorResource(R.color.surface_light);
                chip.setTextColor(getResources().getColor(R.color.text_primary));

                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        if (!state.selectedModifiers.contains(mod)) state.selectedModifiers.add(mod);
                    } else {
                        state.selectedModifiers.remove(mod);
                    }
                    recalculateOrder();
                });
                chipGroupModifiers.addView(chip);
            }

            itemsContainer.addView(card);
        }
    }

    private void updateQuantityText(TextView tv, SimulatedItemState state) {
        if (state.item.getMeasurementUnit().isFractionalAllowed()) {
            tv.setText(String.format(Locale.getDefault(), "%.3f %s", state.quantity, state.item.getMeasurementUnit().getCode()));
        } else {
            tv.setText(String.format(Locale.getDefault(), "%d %s", state.quantity.intValue(), state.item.getMeasurementUnit().getCode()));
        }
    }

    private void recalculateOrder() {
        List<UniversalOrderItem> lines = new ArrayList<>();

        for (SimulatedItemState state : activeItems) {
            if (state.quantity.compareTo(BigDecimal.ZERO) > 0) {
                UniversalOrderItem line = UniversalOrderItem.builder(state.item, state.quantity)
                        .modifiers(state.selectedModifiers)
                        .strategy(state.strategy)
                        .build();
                lines.add(line);
            }
        }

        TaxCalculationMode taxMode = (currentSector == SectorType.TALLER || currentSector == SectorType.MAYORISTA)
                ? TaxCalculationMode.EXCLUSIVE : TaxCalculationMode.INCLUSIVE;

        String storeName = getStoreNameForSector(currentSector);
        String clientIdentifier = getClientForSector(currentSector);

        UniversalOrder order = new UniversalOrder(
                "SIM-F2-" + (System.currentTimeMillis() % 10000),
                System.currentTimeMillis(),
                currentSector.name(),
                clientIdentifier,
                lines,
                PaymentMethod.CARD,
                taxMode
        );

        // 1. Actualizar Resumen Financiero en Vivo
        BigDecimal subtotal = order.calculateSubtotal();
        BigDecimal grandTotal = order.calculateGrandTotal();

        tvLiveSubtotal.setText(String.format(Locale.getDefault(), "%.2f €", subtotal));
        tvLiveTotal.setText(String.format(Locale.getDefault(), "%.2f €", grandTotal));

        // Desglose de impuestos por tipo
        taxBreakdownContainer.removeAllViews();
        Map<TaxRate, BigDecimal[]> breakdown = order.getTaxBreakdown();
        for (Map.Entry<TaxRate, BigDecimal[]> entry : breakdown.entrySet()) {
            TaxRate rate = entry.getKey();
            BigDecimal base = entry.getValue()[0];
            BigDecimal quota = entry.getValue()[1];

            TextView tvTax = new TextView(this);
            tvTax.setText(getString(R.string.f2_impuesto_linea_fmt,
                    rate.getName(), base, quota));
            tvTax.setTextColor(getResources().getColor(R.color.text_secondary));
            tvTax.setTextSize(12);
            taxBreakdownContainer.addView(tvTax);
        }

        // 2. Emitir Ticket Térmico en Pantalla
        if (lines.isEmpty()) {
            tvThermalReceipt.setText(getString(R.string.f2_ticket_vacio));
        } else {
            String ticket = receiptFormatter.formatTicket(order, storeName);
            tvThermalReceipt.setText(ticket);
        }
    }

    private String getStoreNameForSector(SectorType sector) {
        switch (sector) {
            case TALLER: return "TALLERES MECÁNICOS SOFTMYC SL";
            case FRUTERIA: return "FRUTERÍA LA HUERTA ECOLÓGICA";
            case RESTAURANTE: return "SOFTMYC BURGER & GRILL";
            case MAYORISTA: return "DISTRIBUCIONES SOFTMYC CASH&CARRY";
            default: return "SOFTMYC TPV UNIVERSAL";
        }
    }

    private String getClientForSector(SectorType sector) {
        switch (sector) {
            case TALLER: return "Vehículo: 7391-JMP (Cliente: Manuel R.)";
            case MAYORISTA: return "CIF: B-98765432 (ElectroRepuestos SL)";
            default: return "";
        }
    }
}
