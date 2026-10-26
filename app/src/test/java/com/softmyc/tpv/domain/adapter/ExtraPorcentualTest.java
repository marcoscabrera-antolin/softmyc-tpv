package com.softmyc.tpv.domain.adapter;

import static org.junit.Assert.assertEquals;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.model.ItemModifier;
import com.softmyc.tpv.domain.model.ModifierType;
import com.softmyc.tpv.domain.model.UniversalOrderItem;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** NEG-05: un extra con "%" en el nombre debe usar su porcentaje real, no un 20 % fijo. */
public class ExtraPorcentualTest {

    private static final Producto HAMBURGUESA =
            new Producto(1, "Hamburguesa", 10.0, "Hamburguesas", Collections.emptyList());

    /** Modificador resultante de añadir un único extra con el precio configurado indicado. */
    private static ItemModifier modificadorDe(String extra, double precioConfigurado) {
        PedidoItem item = new PedidoItem(HAMBURGUESA, 1);
        item.getIngredientesExtra().add(extra);
        Map<String, Double> precios = new HashMap<>();
        precios.put(extra, precioConfigurado);
        UniversalOrderItem linea = LegacyOrderAdapter.toUniversalOrderItem(item, precios);
        return linea.getAppliedModifiers().get(0);
    }

    @Test
    public void extraConPorcentajeConSignoUsaElPorcentajeDelNombre() {
        ItemModifier mod = modificadorDe("Urgencia +30%", 3.0);

        assertEquals(ModifierType.SURCHARGE_PERCENTAGE, mod.getType());
        assertEquals(0, new BigDecimal("30").compareTo(mod.getValue()));
    }

    @Test
    public void extraConPorcentajeNegativoEsDescuentoPorcentual() {
        ItemModifier mod = modificadorDe("Descuento -10%", 0.0);

        assertEquals(ModifierType.DISCOUNT_PERCENTAGE, mod.getType());
        assertEquals(0, new BigDecimal("10").compareTo(mod.getValue()));
    }

    @Test
    public void porcentajeEntreParentesisConEspaciosYComaDecimal() {
        ItemModifier entreParentesis = modificadorDe("Servicio Urgente (+ 15 %)", 0.0);
        ItemModifier decimal = modificadorDe("Extra +12,5%", 0.0);

        assertEquals(0, new BigDecimal("15").compareTo(entreParentesis.getValue()));
        assertEquals(0, new BigDecimal("12.5").compareTo(decimal.getValue()));
    }

    @Test
    public void porcentajeFueraDeRangoUsaElPrecioConfigurado() {
        ItemModifier mod = modificadorDe("Extra +150%", 2.0);

        assertEquals(ModifierType.SURCHARGE_FIXED, mod.getType());
        assertEquals(0, new BigDecimal("2.00").compareTo(mod.getValue()));
    }

    @Test
    public void porcentajeQueNoEsFinalDelNombreUsaElPrecioConfigurado() {
        ItemModifier mod = modificadorDe("Queso 100% vaca", 1.0);

        assertEquals(ModifierType.SURCHARGE_FIXED, mod.getType());
        assertEquals(0, new BigDecimal("1.00").compareTo(mod.getValue()));
    }

    @Test
    public void porcentajeSinSignoUsaElPrecioConfiguradoPorSerAmbiguo() {
        ItemModifier mod = modificadorDe("Urgencia 30%", 3.0);

        assertEquals(ModifierType.SURCHARGE_FIXED, mod.getType());
        assertEquals(0, new BigDecimal("3.00").compareTo(mod.getValue()));
    }

    @Test
    public void extraSinPorcentajeSigueSiendoFijoConSuPrecio() {
        ItemModifier mod = modificadorDe("Bacon", 1.2);

        assertEquals(ModifierType.SURCHARGE_FIXED, mod.getType());
        assertEquals(0, new BigDecimal("1.20").compareTo(mod.getValue()));
    }
}
