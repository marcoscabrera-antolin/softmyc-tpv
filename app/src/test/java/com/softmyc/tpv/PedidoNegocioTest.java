package com.softmyc.tpv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.usecase.CalcularTotalUseCase;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests de lógica de negocio del modelo de dominio PedidoItem.
 * M1: Cubre el cálculo de totales, el comportamiento de los extras con precio
 * y la validación de estados críticos de un ítem del pedido.
 */
public class PedidoNegocioTest {

    private Producto hamburguesa;
    private Producto bocadillo;
    private final CalcularTotalUseCase calculador = new CalcularTotalUseCase();

    @Before
    public void setUp() {
        List<String> ingHamburguesa = Arrays.asList("Carne", "Queso", "Lechuga", "Tomate");
        hamburguesa = new Producto(1, "Hamburguesa Clásica", 7.50, "hamburguesas", ingHamburguesa);

        List<String> ingBocadillo = Arrays.asList("Jamón", "Tomate", "Aceite");
        bocadillo = new Producto(2, "Bocadillo de Jamón", 4.00, "bocadillos", ingBocadillo);
    }

    // -------------------------------------------------------------------------
    // M1a: Precio total del pedido con extras, calculado por el caso de uso real
    // -------------------------------------------------------------------------

    @Test
    public void testPrecioTotalSinExtras() {
        PedidoItem item = new PedidoItem(hamburguesa, 1);

        double total = calculador.calcularTotal(Collections.singletonList(item), Collections.emptyMap());

        assertEquals(7.50, total, 0.001);
    }

    @Test
    public void testPrecioTotalConUnExtra() {
        PedidoItem item = new PedidoItem(hamburguesa, 1);
        item.getIngredientesExtra().add("Bacon");
        Map<String, Double> precios = Collections.singletonMap("Bacon", 1.20);

        assertEquals(1.20, calculador.calcularExtras(item, precios), 0.001);
        assertEquals(8.70, calculador.calcularTotal(Collections.singletonList(item), precios), 0.001);
    }

    @Test
    public void testPrecioTotalConMultiplesExtrasEnDosUnidades() {
        PedidoItem item = new PedidoItem(hamburguesa, 2);
        item.getIngredientesExtra().add("Queso");
        item.getIngredientesExtra().add("Bacon");
        Map<String, Double> precios = new HashMap<>();
        precios.put("Queso", 0.80);
        precios.put("Bacon", 1.20);

        assertEquals(2.00, calculador.calcularExtras(item, precios), 0.001);
        // 2 unidades x (7,50 base + 2,00 de extras)
        assertEquals(19.00, calculador.calcularTotal(Collections.singletonList(item), precios), 0.001);
    }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // M1b: Estado after eliminate: cuando un item llega a 0 unidades, sinUnidades() = true
    // -------------------------------------------------------------------------

    @Test
    public void testReducirCantidadHastaCeroMarcaSinUnidades() {
        PedidoItem item = new PedidoItem(bocadillo, 3);
        item.reducirCantidad(3);
        assertTrue("El item debe estar marcado como sinUnidades()", item.sinUnidades());
    }

    @Test
    public void testReducirCantidadParcialNoPierdeUnidades() {
        PedidoItem item = new PedidoItem(hamburguesa, 5);
        item.reducirCantidad(2);
        assertEquals(3, item.getCantidad());
        assertFalse(item.sinUnidades());
    }

    // -------------------------------------------------------------------------
    // M1c: Detalles visuales reflejan correctamente extras y quitados
    // -------------------------------------------------------------------------

    @Test
    public void testDetallesVisualesVaciosConItemPuro() {
        PedidoItem item = new PedidoItem(hamburguesa, 1);
        String detalles = item.getDetallesVisuales();
        assertTrue("Item sin modificaciones no debe tener detalles", detalles.isEmpty());
    }

    @Test
    public void testDetallesVisualesConModificaciones() {
        PedidoItem item = new PedidoItem(hamburguesa, 1);
        item.getIngredientesQuitados().add("Queso");
        item.getIngredientesExtra().add("Bacon");

        String detalles = item.getDetallesVisuales();
        assertTrue(detalles.contains("Sin: Queso"));
        assertTrue(detalles.contains("Añadidos: Bacon"));
    }

    // -------------------------------------------------------------------------
    // M1d: Acumulación de cantidad en ítem existente
    // -------------------------------------------------------------------------

    @Test
    public void testAumentarCantidadDesdeCero() {
        PedidoItem item = new PedidoItem(bocadillo, 0);
        item.aumentarCantidad(4);
        assertEquals(4, item.getCantidad());
    }

    @Test
    public void testSetCantidadDirecto() {
        PedidoItem item = new PedidoItem(hamburguesa, 1);
        item.setCantidad(10);
        assertEquals(10, item.getCantidad());
    }
}
