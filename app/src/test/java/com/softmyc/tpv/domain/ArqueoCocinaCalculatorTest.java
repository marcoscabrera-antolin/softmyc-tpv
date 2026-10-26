package com.softmyc.tpv.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.usecase.ArqueoCocinaCalculator;
import com.softmyc.tpv.domain.usecase.ArqueoCocinaCalculator.Resultado;
import com.softmyc.tpv.domain.usecase.ArqueoCocinaCalculator.Venta;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ArqueoCocinaCalculatorTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final Set<String> COCINA = new HashSet<>(Arrays.asList("Bocadillos", "Hamburguesas"));
    private static final double DELTA = 0.001;

    private static String line(String nombre, int cantidad, double precio, String categoria, Double importe) {
        return "{\"nombre\":\"" + nombre + "\",\"cantidad\":" + cantidad + ",\"precio_unitario\":" + precio
                + (categoria != null ? ",\"categoria\":\"" + categoria + "\"" : "")
                + (importe != null ? ",\"importe_linea\":" + importe : "") + "}";
    }

    private static Venta venta(String metodo, String... lines) {
        return new Venta(metodo, "[" + String.join(",", lines) + "]");
    }

    private static Resultado calc(List<Venta> ventas) {
        return ArqueoCocinaCalculator.calcular(ventas, COCINA, Collections.emptyMap());
    }

    @Test
    public void calcular_sumsOnlyKitchenCategoryLines_usingLineAmount() {
        Resultado r = calc(Arrays.asList(venta("Efectivo",
                line("Burger", 2, 6.0, "Hamburguesas", 13.0),
                line("Cola", 1, 2.0, "Bebidas", 2.0))));

        assertEquals(13.0, r.totalCocina, DELTA);
    }

    @Test
    public void calcular_withoutLineAmount_fallsBackToUnitPriceTimesQuantity() {
        Resultado r = calc(Arrays.asList(venta("Efectivo", line("Burger", 3, 5.5, "Hamburguesas", null))));

        assertEquals(16.5, r.totalCocina, DELTA);
    }

    @Test
    public void calcular_withoutCategoryInJson_usesCatalogCategoryByName() {
        Map<String, String> catalogo = new HashMap<>();
        catalogo.put("burger", "Hamburguesas");
        Resultado r = ArqueoCocinaCalculator.calcular(
                Arrays.asList(venta("Efectivo", line("Burger", 1, 7.0, null, null))), COCINA, catalogo);

        assertEquals(7.0, r.totalCocina, DELTA);
    }

    @Test
    public void calcular_unknownCategory_isNotKitchen() {
        Resultado r = calc(Arrays.asList(venta("Efectivo", line("Algo", 1, 7.0, null, null))));

        assertEquals(0.0, r.totalCocina, DELTA);
        assertEquals(0, r.tickets);
    }

    @Test
    public void calcular_splitsCashAndCardByPaymentMethod() {
        Resultado r = calc(Arrays.asList(
                venta("Efectivo", line("Burger", 1, 6.0, "Hamburguesas", 6.0)),
                venta("Tarjeta", line("Bocata", 1, 4.0, "Bocadillos", 4.0))));

        assertEquals(6.0, r.efectivo, DELTA);
        assertEquals(4.0, r.tarjeta, DELTA);
        assertEquals(10.0, r.totalCocina, DELTA);
    }

    @Test
    public void calcular_countsOnlyTicketsWithKitchenLines() {
        Resultado r = calc(Arrays.asList(
                venta("Efectivo", line("Burger", 1, 6.0, "Hamburguesas", 6.0)),
                venta("Efectivo", line("Cola", 1, 2.0, "Bebidas", 2.0))));

        assertEquals(1, r.tickets);
    }

    @Test
    public void calcular_groupsByProductSortedByAmountDescending() {
        Resultado r = calc(Arrays.asList(
                venta("Efectivo", line("Bocata", 1, 4.0, "Bocadillos", 4.0), line("Burger", 1, 6.0, "Hamburguesas", 6.0)),
                venta("Efectivo", line("Bocata", 3, 4.0, "Bocadillos", 12.0))));

        assertEquals(2, r.productos.size());
        assertEquals("Bocata", r.productos.get(0).nombre);
        assertEquals(4, r.productos.get(0).unidades);
        assertEquals(16.0, r.productos.get(0).importe, DELTA);
        assertEquals("Burger", r.productos.get(1).nombre);
    }

    @Test
    public void calcular_ignoresMalformedJson() {
        Resultado r = calc(Arrays.asList(new Venta("Efectivo", "no es json"),
                venta("Efectivo", line("Burger", 1, 6.0, "Hamburguesas", 6.0))));

        assertEquals(6.0, r.totalCocina, DELTA);
    }

    @Test
    public void calcular_withNoKitchenCategories_returnsZero() {
        Resultado r = ArqueoCocinaCalculator.calcular(
                Arrays.asList(venta("Efectivo", line("Burger", 1, 6.0, "Hamburguesas", 6.0))),
                Collections.emptySet(), Collections.emptyMap());

        assertEquals(0.0, r.totalCocina, DELTA);
    }

    @Test
    public void formatearTicket_includesTotalsAndProducts() {
        Resultado r = calc(Arrays.asList(venta("Tarjeta", line("Burger", 2, 6.0, "Hamburguesas", 12.0))));

        String ticket = ArqueoCocinaCalculator.formatearTicket(r, "La Nube", "HOY", "01/01/2026 10:00", 48);

        assertTrue(ticket.contains("ARQUEO DE COCINA"));
        assertTrue(ticket.contains("LA NUBE"));
        assertTrue(ticket.contains("Burger"));
        assertTrue(ticket.contains("12.00") || ticket.contains("12,00"));
    }
}
