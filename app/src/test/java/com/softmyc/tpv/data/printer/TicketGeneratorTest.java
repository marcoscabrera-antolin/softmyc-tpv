package com.softmyc.tpv.data.printer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.usecase.CalcularTotalUseCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** El total del ticket (y el que se guarda en la venta) debe coincidir con el motor de precios. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TicketGeneratorTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private final Context context = ApplicationProvider.getApplicationContext();
    private Locale localeOriginal;

    @Before
    public void fijarLocale() {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(new Locale("es", "ES"));
    }

    @After
    public void restaurarLocale() {
        Locale.setDefault(localeOriginal);
    }

    private static PedidoItem fruit(double grossKg, double tareKg) {
        Producto p = new Producto(1, "Manzana Golden", 3.00, "Frutas", Collections.emptyList(), "KG");
        PedidoItem item = new PedidoItem(p, 1);
        item.setCantidadMedida(grossKg);
        item.setTaraKg(tareKg);
        return item;
    }

    @Test
    public void ticketTotal_forWeightedItemWithTare_matchesPricingEngine() {
        List<PedidoItem> pedido = Collections.singletonList(fruit(1.850, 0.120));

        TicketGenerator.TicketResult result = TicketGenerator.generarTicket(context, pedido, "Test", new HashMap<>(), "Efectivo");

        assertEquals(5.19, result.total, 0.001);
    }

    @Test
    public void ticketTotal_matchesCalcularTotalForMixedOrder() {
        Producto burger = new Producto(2, "Burger", 6.00, "Hamburguesas", Collections.emptyList());
        PedidoItem burgers = new PedidoItem(burger, 2);
        burgers.getIngredientesExtra().add("Queso");
        Map<String, Double> extras = new HashMap<>();
        extras.put("Queso", 1.0);
        List<PedidoItem> pedido = Arrays.asList(burgers, fruit(0.500, 0.0));

        TicketGenerator.TicketResult result = TicketGenerator.generarTicket(context, pedido, "Test", extras, "Tarjeta");

        assertEquals(new CalcularTotalUseCase().calcularTotal(pedido, extras), result.total, 0.001);
    }

    @Test
    public void ticketText_printsTheSameTotalItReturns() {
        List<PedidoItem> pedido = Collections.singletonList(fruit(1.850, 0.120));

        TicketGenerator.TicketResult result = TicketGenerator.generarTicket(context, pedido, "Test", new HashMap<>(), "Efectivo");

        assertTrue(result.texto.contains("TOTAL: 5,19 EUR"));
    }
}
