package com.softmyc.tpv.data.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parseo de los pedidos web que llegan de la API: filtro por negocio, orden, estado y datos del ticket. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PedidoWebParserTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    @Test
    public void pedidosDelNegocioKeepsOnlyActiveTenantAndSortsByTimestamp() {
        // Arrange
        Map<String, Object> tarde = pedido("A", "lanube", 2000L);
        Map<String, Object> ajeno = pedido("B", "otro", 1000L);
        Map<String, Object> temprano = pedido("C", "lanube", 1000L);
        List<Map<String, Object>> recibidos = Arrays.asList(tarde, ajeno, temprano);

        // Act
        List<Map<String, Object>> resultado = PedidoWebParser.pedidosDelNegocio(recibidos, "lanube");

        // Assert
        assertEquals(Arrays.asList(temprano, tarde), resultado);
    }

    @Test
    public void pedidoSinRestaurantIdCuentaComoLanube() {
        Map<String, Object> sinNegocio = new HashMap<>();
        sinNegocio.put("_id", "X");

        List<Map<String, Object>> resultado = PedidoWebParser.pedidosDelNegocio(
                Collections.singletonList(sinNegocio), "lanube");

        assertEquals(1, resultado.size());
    }

    @Test
    public void pedidosDelNegocioAcceptsNullListAsEmpty() {
        assertTrue(PedidoWebParser.pedidosDelNegocio(null, "lanube").isEmpty());
    }

    @Test
    public void timestampReadsNumericFecha() {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("fecha", 1234.0);

        assertEquals(1234L, PedidoWebParser.timestamp(pedido));
    }

    @Test
    public void timestampReadsIsoCreatedAtWhenFechaIsMissing() {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("createdAt", "2026-10-16T10:00:00Z");

        assertEquals(Instant.parse("2026-10-16T10:00:00Z").toEpochMilli(), PedidoWebParser.timestamp(pedido));
    }

    @Test
    public void timestampIsZeroWhenMissingOrUnparseable() {
        Map<String, Object> sinFecha = new HashMap<>();
        Map<String, Object> basura = new HashMap<>();
        basura.put("fecha", "ayer por la tarde");

        assertEquals(0L, PedidoWebParser.timestamp(sinFecha));
        assertEquals(0L, PedidoWebParser.timestamp(basura));
        assertEquals(0L, PedidoWebParser.timestamp(null));
    }

    @Test
    public void pedidoSinEstadoEsPendiente() {
        assertTrue(PedidoWebParser.esPendiente(new HashMap<>()));
    }

    @Test
    public void pedidoListoNoEsPendiente() {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("estado", "listo");

        assertFalse(PedidoWebParser.esPendiente(pedido));
    }

    @Test
    public void clienteUsaValoresPorDefectoCuandoEsTexto() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("cliente", "Ana");

        Map<String, Object> cliente = PedidoWebParser.cliente(doc);

        assertEquals("Ana", cliente.get("nombre"));
        assertEquals("", cliente.get("telefono"));
        assertEquals("", cliente.get("mesa"));
    }

    @Test
    public void clienteSinDatosTomaElNombreGenerico() {
        Map<String, Object> cliente = PedidoWebParser.cliente(new HashMap<>());

        assertEquals("Cliente Web", cliente.get("nombre"));
    }

    @Test
    public void lineasUsaItemsYSiNoHayCaeEnCart() {
        Map<String, Object> conItems = new HashMap<>();
        conItems.put("items", Collections.singletonList(linea("Bocata")));
        Map<String, Object> conCart = new HashMap<>();
        conCart.put("items", new ArrayList<>());
        conCart.put("cart", Collections.singletonList(linea("Zumo")));

        assertEquals("Bocata", PedidoWebParser.lineas(conItems).get(0).get("nombre"));
        assertEquals("Zumo", PedidoWebParser.lineas(conCart).get(0).get("nombre"));
    }

    @Test
    public void totalLeeNumeroOCeroSiFalta() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("total", 12.5);

        assertEquals(12.5, PedidoWebParser.total(doc), 0.0001);
        assertEquals(0.0, PedidoWebParser.total(new HashMap<>()), 0.0001);
    }

    @Test
    public void idPedidoEsNullSinId() {
        Map<String, Object> pedido = new HashMap<>();

        assertEquals(null, PedidoWebParser.idPedido(pedido));
        assertEquals("ORD-9", PedidoWebParser.idPedido(pedidoConId("ORD-9")));
    }

    private static Map<String, Object> pedido(String id, String negocio, long fecha) {
        Map<String, Object> pedido = pedidoConId(id);
        pedido.put("restaurantId", negocio);
        pedido.put("fecha", (double) fecha);
        return pedido;
    }

    private static Map<String, Object> pedidoConId(String id) {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("_id", id);
        return pedido;
    }

    private static Map<String, Object> linea(String nombre) {
        Map<String, Object> linea = new HashMap<>();
        linea.put("nombre", nombre);
        return linea;
    }
}
