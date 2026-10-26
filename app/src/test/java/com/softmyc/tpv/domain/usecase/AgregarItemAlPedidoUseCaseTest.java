package com.softmyc.tpv.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Reglas del carrito: agregar, iniciar edición, cambiar cantidad y eliminar. Las operaciones devuelven una lista
 * nueva (el pedido original no cambia) y los errores llevan el mensaje que muestra la pantalla.
 */
public class AgregarItemAlPedidoUseCaseTest {

    private AgregarItemAlPedidoUseCase caso;
    private PedidoItem cafe;
    private PedidoItem tarta;

    @Before
    public void setUp() {
        caso = new AgregarItemAlPedidoUseCase();
        cafe = item("Café", 1);
        tarta = item("Tarta", 2);
    }

    @Test
    public void agregar_sin_item_temporal_devuelve_error_y_no_toca_el_pedido() {
        List<PedidoItem> pedido = new ArrayList<>(Collections.singletonList(cafe));

        AgregarItemAlPedidoUseCase.Resultado resultado = caso.agregar(pedido, null);

        assertFalse(resultado.isOk());
        assertEquals("Selecciona un producto primero", resultado.error);
        assertNull(resultado.pedidoActualizado);
        assertEquals(1, pedido.size());
    }

    @Test
    public void agregar_con_pedido_nulo_crea_un_pedido_con_el_item() {
        AgregarItemAlPedidoUseCase.Resultado resultado = caso.agregar(null, cafe);

        assertTrue(resultado.isOk());
        assertEquals(Collections.singletonList(cafe), resultado.pedidoActualizado);
    }

    @Test
    public void agregar_anade_al_final_sin_modificar_la_lista_original() {
        List<PedidoItem> pedido = new ArrayList<>(Collections.singletonList(cafe));

        AgregarItemAlPedidoUseCase.Resultado resultado = caso.agregar(pedido, tarta);

        assertEquals(Arrays.asList(cafe, tarta), resultado.pedidoActualizado);
        assertEquals(Collections.singletonList(cafe), pedido);
        assertNotSame(pedido, resultado.pedidoActualizado);
    }

    @Test
    public void iniciarEdicion_quita_la_linea_de_la_posicion_indicada() {
        List<PedidoItem> pedido = Arrays.asList(cafe, tarta);

        AgregarItemAlPedidoUseCase.Resultado resultado = caso.iniciarEdicion(pedido, 0);

        assertTrue(resultado.isOk());
        assertEquals(Collections.singletonList(tarta), resultado.pedidoActualizado);
        assertEquals(2, pedido.size());
    }

    @Test
    public void iniciarEdicion_con_posicion_fuera_de_rango_o_pedido_nulo_devuelve_error() {
        List<PedidoItem> pedido = Collections.singletonList(cafe);

        assertEquals("Posición de edición inválida", caso.iniciarEdicion(pedido, 1).error);
        assertEquals("Posición de edición inválida", caso.iniciarEdicion(pedido, -1).error);
        assertEquals("Posición de edición inválida", caso.iniciarEdicion(null, 0).error);
    }

    @Test
    public void modificarCantidad_suma_el_delta_al_item() {
        List<PedidoItem> pedido = new ArrayList<>(Collections.singletonList(tarta));

        AgregarItemAlPedidoUseCase.Resultado resultado = caso.modificarCantidad(pedido, 0, 3);

        assertTrue(resultado.isOk());
        assertEquals(5, resultado.pedidoActualizado.get(0).getCantidad());
    }

    @Test
    public void modificarCantidad_que_no_llega_a_uno_devuelve_error_y_no_cambia_el_item() {
        List<PedidoItem> pedido = new ArrayList<>(Collections.singletonList(tarta));

        AgregarItemAlPedidoUseCase.Resultado resultado = caso.modificarCantidad(pedido, 0, -2);

        assertFalse(resultado.isOk());
        assertEquals("La cantidad no puede ser menor que 1", resultado.error);
        assertEquals(2, tarta.getCantidad());
    }

    @Test
    public void modificarCantidad_con_posicion_invalida_o_pedido_nulo_devuelve_error() {
        List<PedidoItem> pedido = new ArrayList<>(Collections.singletonList(tarta));

        assertEquals("Posición inválida", caso.modificarCantidad(pedido, 4, 1).error);
        assertEquals("Posición inválida", caso.modificarCantidad(pedido, -1, 1).error);
        assertEquals("Posición inválida", caso.modificarCantidad(null, 0, 1).error);
    }

    @Test
    public void eliminar_quita_solo_la_linea_indicada() {
        List<PedidoItem> pedido = Arrays.asList(cafe, tarta);

        AgregarItemAlPedidoUseCase.Resultado resultado = caso.eliminar(pedido, 1);

        assertTrue(resultado.isOk());
        assertEquals(Collections.singletonList(cafe), resultado.pedidoActualizado);
        assertSame(cafe, resultado.pedidoActualizado.get(0));
    }

    @Test
    public void eliminar_con_posicion_invalida_o_pedido_nulo_devuelve_error() {
        List<PedidoItem> pedido = Collections.singletonList(cafe);

        assertEquals("Posición inválida", caso.eliminar(pedido, 1).error);
        assertEquals("Posición inválida", caso.eliminar(pedido, -1).error);
        assertEquals("Posición inválida", caso.eliminar(null, 0).error);
    }

    private static PedidoItem item(String nombre, int cantidad) {
        return new PedidoItem(new Producto(1, nombre, 2.0, "Carta", new ArrayList<>(), "UD"), cantidad);
    }
}
