package com.softmyc.tpv.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.softmyc.tpv.domain.CalculoPedidoException;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** NEG-06: un cálculo inválido devuelve un error tipado y una línea a cero no rompe el total. */
public class CalcularTotalUseCaseTest {

    private final CalcularTotalUseCase calculador = new CalcularTotalUseCase();

    private static PedidoItem cafe(int cantidad) {
        return new PedidoItem(new Producto(10, "Café", 2.50, "Bebidas", Collections.emptyList()), cantidad);
    }

    @Test
    public void listaNulaOVaciaDaCero() {
        assertEquals(0.0, calculador.calcularTotal(null, Collections.emptyMap()), 0.0001);
        assertEquals(0.0, calculador.calcularTotal(Collections.emptyList(), Collections.emptyMap()), 0.0001);
    }

    @Test
    public void lineaConCantidadCeroNoRompeElTotalYNoAportaImporte() {
        double total = calculador.calcularTotal(Arrays.asList(cafe(0), cafe(2)), Collections.emptyMap());

        assertEquals(5.00, total, 0.0001);
    }

    @Test
    public void pesoNetoNoPositivoLanzaErrorTipadoEnVezDeIllegalArgument() {
        Producto fruta = new Producto(20, "Manzanas", 2.0, "Frutería", Collections.emptyList(), "KG");
        PedidoItem item = new PedidoItem(fruta, 1);
        item.setUnidadMedida("KG");
        item.setCantidadMedida(0.5);
        item.setTaraKg(0.5); // tara igual al bruto: el neto no es positivo

        assertThrows(CalculoPedidoException.class,
                () -> calculador.calcularTotal(Collections.singletonList(item), Collections.emptyMap()));
    }

    @Test
    public void lineaSinProductoLanzaErrorTipado() {
        assertThrows(CalculoPedidoException.class,
                () -> calculador.calcularTotal(Collections.singletonList((PedidoItem) null), Collections.emptyMap()));
    }
}
