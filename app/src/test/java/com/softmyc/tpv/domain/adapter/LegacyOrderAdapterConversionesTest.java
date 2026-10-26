package com.softmyc.tpv.domain.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.softmyc.tpv.domain.IngredientOption;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.model.CatalogItem;
import com.softmyc.tpv.domain.model.ItemModifier;
import com.softmyc.tpv.domain.model.MeasurementUnit;
import com.softmyc.tpv.domain.model.ModifierType;
import com.softmyc.tpv.domain.model.PricingStrategyType;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Conversiones del puente hostelería → motor universal: unidad, estrategia e IVA según el tipo de producto,
 * modificadores de ingredientes y cantidad facturable de una línea.
 */
public class LegacyOrderAdapterConversionesTest {

    @Test
    public void producto_nulo_no_se_convierte() {
        assertNull(LegacyOrderAdapter.toCatalogItem(null));
    }

    @Test
    public void producto_sin_categoria_usa_la_categoria_general() {
        CatalogItem item = LegacyOrderAdapter.toCatalogItem(producto("Agua", 1.0, "   ", "UD"));

        assertEquals("GENERAL", item.getCategory());
        assertEquals(MeasurementUnit.UNIT, item.getMeasurementUnit());
        assertEquals(PricingStrategyType.UNIT, item.getStrategyType());
    }

    @Test
    public void producto_de_peso_se_convierte_a_kilos_con_estrategia_de_peso() {
        CatalogItem item = LegacyOrderAdapter.toCatalogItem(producto("Uvas", 2.5, "Frutas", "KG"));

        assertEquals(MeasurementUnit.WEIGHT_KG, item.getMeasurementUnit());
        assertEquals(PricingStrategyType.WEIGHT, item.getStrategyType());
        assertEquals(new BigDecimal("2.50"), item.getBasePrice());
    }

    @Test
    public void producto_por_tiempo_se_convierte_a_horas() {
        CatalogItem item = LegacyOrderAdapter.toCatalogItem(producto("Mano de obra", 40.0, "Servicios", "HORA"));

        assertEquals(MeasurementUnit.TIME_HOUR, item.getMeasurementUnit());
        assertEquals(PricingStrategyType.TIME, item.getStrategyType());
    }

    @Test
    public void producto_mayorista_usa_la_estrategia_de_volumen_escalonado() {
        CatalogItem item = LegacyOrderAdapter.toCatalogItem(producto("Harina 25kg", 28.5, "Mayorista", "UD"));

        assertEquals(MeasurementUnit.UNIT, item.getMeasurementUnit());
        assertEquals(PricingStrategyType.TIERED_VOLUME, item.getStrategyType());
    }

    @Test
    public void el_precio_base_se_redondea_a_dos_decimales() {
        CatalogItem item = LegacyOrderAdapter.toCatalogItem(producto("Pan", 1.005, "Panadería", "UD"));

        assertEquals(0, new BigDecimal("1.01").compareTo(item.getBasePrice()));
    }

    @Test
    public void ingrediente_nulo_no_produce_modificador() {
        assertNull(LegacyOrderAdapter.toItemModifier(null));
    }

    @Test
    public void ingrediente_para_quitar_es_una_exclusion() {
        ItemModifier modificador = LegacyOrderAdapter.toItemModifier(
                new IngredientOption("Cebolla", 0.0, IngredientOption.Type.QUITAR));

        assertEquals(ModifierType.EXCLUSION, modificador.getType());
        assertEquals("Cebolla", modificador.getName());
    }

    @Test
    public void salsa_sin_precio_es_cualitativa_y_con_precio_es_un_recargo() {
        ItemModifier gratis = LegacyOrderAdapter.toItemModifier(
                new IngredientOption("Ketchup", 0.0, IngredientOption.Type.SALSA));
        ItemModifier cobrada = LegacyOrderAdapter.toItemModifier(
                new IngredientOption("Alioli", 0.5, IngredientOption.Type.SALSA));

        assertEquals(ModifierType.QUALITATIVE_NOTE, gratis.getType());
        assertEquals(ModifierType.SURCHARGE_FIXED, cobrada.getType());
        assertEquals(new BigDecimal("0.50"), cobrada.getValue());
    }

    @Test
    public void extra_con_precio_se_convierte_en_recargo_fijo_con_dos_decimales() {
        ItemModifier extra = LegacyOrderAdapter.toItemModifier(
                new IngredientOption("Queso", 1.5, IngredientOption.Type.ANADIR));

        assertEquals(ModifierType.SURCHARGE_FIXED, extra.getType());
        assertEquals(new BigDecimal("1.50"), extra.getValue());
    }

    @Test
    public void cantidad_facturable_de_unidades_es_la_cantidad_entera() {
        PedidoItem linea = new PedidoItem(producto("Bocata", 4.0, "Bocadillos", "UD"), 3);

        assertEquals(new BigDecimal("3"), LegacyOrderAdapter.cantidadEfectiva(linea));
    }

    @Test
    public void cantidad_facturable_de_peso_usa_los_kilos_medidos_con_tres_decimales() {
        PedidoItem linea = new PedidoItem(producto("Uvas", 2.5, "Frutas", "KG"), 1);
        linea.setCantidadMedida(1.85);

        assertEquals(new BigDecimal("1.850"), LegacyOrderAdapter.cantidadEfectiva(linea));
    }

    @Test
    public void peso_sin_medir_cae_a_la_cantidad_entera() {
        PedidoItem linea = new PedidoItem(producto("Uvas", 2.5, "Frutas", "KG"), 2);
        linea.setCantidadMedida(0.0);

        assertEquals(new BigDecimal("0"), LegacyOrderAdapter.cantidadEfectiva(linea));
    }

    private static Producto producto(String nombre, double precio, String categoria, String unidad) {
        return new Producto(1, nombre, precio, categoria, new ArrayList<>(Collections.emptyList()), unidad);
    }
}
