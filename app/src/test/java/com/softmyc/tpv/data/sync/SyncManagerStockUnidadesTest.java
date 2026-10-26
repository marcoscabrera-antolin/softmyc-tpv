package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.SerializacionVentaException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.Map;

/** NEG-09 (solo unidades discretas descuentan stock) y ERR-06 (error tipado al serializar). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SyncManagerStockUnidadesTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    @Test
    public void cantidadesPorProductoNoIncluyeLineasEnPesoNiHoras() {
        String json = "[{\"codigo\":7,\"cantidad\":3,\"unidad\":\"KG\"},"
                + "{\"codigo\":8,\"cantidad\":2,\"unidad\":\"UD\"},"
                + "{\"codigo\":9,\"cantidad\":1,\"unidad\":\"HORA\"}]";

        Map<Integer, Integer> cantidades = SyncManager.cantidadesPorProducto(json);

        assertNotNull(cantidades);
        assertEquals(1, cantidades.size());
        assertEquals(Integer.valueOf(2), cantidades.get(8));
        assertFalse(cantidades.containsKey(7));
        assertFalse(cantidades.containsKey(9));
    }

    @Test
    public void lineaAntiguaSinUnidadCuentaComoUnidades() {
        Map<Integer, Integer> cantidades =
                SyncManager.cantidadesPorProducto("[{\"codigo\":7,\"cantidad\":3}]");

        assertEquals(Integer.valueOf(3), cantidades.get(7));
    }

    @Test
    public void ventaSoloConLineasEnPesoNoDescuentaNada() {
        String json = "[{\"codigo\":7,\"cantidad\":3,\"unidad\":\"KG\"}]";

        Map<Integer, Integer> cantidades = SyncManager.cantidadesPorProducto(json);

        assertNotNull(cantidades);
        assertTrue(cantidades.isEmpty());
    }

    @Test
    public void serializarLineaInvalidaLanzaErrorControladoEnVezDeGuardarVentaVacia() {
        SerializacionVentaException error = assertThrows(SerializacionVentaException.class,
                () -> SyncManager.serializarItems(Collections.singletonList((PedidoItem) null)));

        assertNotNull(error.getMessage());
        assertNotNull(error.getCause());
    }

    @Test
    public void serializarListaValidaSigueFuncionando() {
        Producto p = new Producto(1, "Burger", 6.0, "Hamburguesas", Collections.emptyList());
        String json = SyncManager.serializarItems(Collections.singletonList(new PedidoItem(p, 2)));

        assertTrue(json.contains("\"codigo\":1"));
    }
}
