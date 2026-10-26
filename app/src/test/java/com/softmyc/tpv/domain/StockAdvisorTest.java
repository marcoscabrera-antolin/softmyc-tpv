package com.softmyc.tpv.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.usecase.StockAdvisor;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** NEG-09 y NEG-10: regla de stock (solo unidades discretas) y aviso de venta sin existencias. */
public class StockAdvisorTest {

    @Test
    public void cantidadDentroDelStockNoAvisa() {
        StockAdvisor.Aviso aviso = StockAdvisor.evaluar(5, "UD", 3);

        assertFalse(aviso.superaStock());
        assertEquals(0, aviso.getExcedente());
        assertEquals("", aviso.mensaje());
    }

    @Test
    public void cantidadIgualAlStockNoAvisa() {
        assertFalse(StockAdvisor.evaluar(5, "UD", 5).superaStock());
    }

    @Test
    public void cantidadSuperiorAlStockAvisaConExcedente() {
        StockAdvisor.Aviso aviso = StockAdvisor.evaluar(2, "UD", 5);

        assertTrue(aviso.superaStock());
        assertEquals(3, aviso.getExcedente());
        assertEquals(2, aviso.getStockDisponible());
        assertTrue(aviso.mensaje().contains("3"));
    }

    @Test
    public void stockAgotadoAvisaPorTodaLaCantidad() {
        StockAdvisor.Aviso aviso = StockAdvisor.evaluar(0, "UD", 1);

        assertTrue(aviso.superaStock());
        assertEquals(1, aviso.getExcedente());
    }

    @Test
    public void productoSinControlDeStockNoAvisa() {
        assertFalse(StockAdvisor.evaluar(-1, "UD", 100).superaStock());
    }

    @Test
    public void lineasEnPesoOHorasNoSeComparanConElStock() {
        assertFalse(StockAdvisor.evaluar(2, "KG", 5).superaStock());
        assertFalse(StockAdvisor.evaluar(2, "HORA", 5).superaStock());
    }

    @Test
    public void unidadVaciaSeTrataComoUnidadesDiscretas() {
        assertTrue(StockAdvisor.evaluar(2, "", 5).superaStock());
    }

    @Test
    public void controlaStockSoloEnUnidadesDiscretas() {
        assertTrue(StockAdvisor.controlaStock("UD"));
        assertTrue(StockAdvisor.controlaStock("ud"));
        assertTrue(StockAdvisor.controlaStock(null));
        assertFalse(StockAdvisor.controlaStock("KG"));
        assertFalse(StockAdvisor.controlaStock("HORA"));
    }

    @Test
    public void cantidadUdEnPedidoSumaSoloLineasDiscretasDelProducto() {
        Producto unidad = new Producto(7, "Bocadillo", 3.0, "Bocadillos", Collections.emptyList());
        Producto fruta = new Producto(7, "Fruta", 2.4, "Frutería", Collections.emptyList(), "KG");
        PedidoItem dos = new PedidoItem(unidad, 2);
        PedidoItem tres = new PedidoItem(unidad, 3);
        PedidoItem kilos = new PedidoItem(fruta, 4);
        PedidoItem otro = new PedidoItem(new Producto(8, "Agua", 1.0, "Bebidas", Collections.emptyList()), 9);

        int total = StockAdvisor.cantidadUdEnPedido(Arrays.asList(dos, tres, kilos, otro), 7);

        assertEquals(5, total);
    }

    @Test
    public void cantidadUdEnPedidoVacioDaCero() {
        assertEquals(0, StockAdvisor.cantidadUdEnPedido(Collections.emptyList(), 7));
    }
}
