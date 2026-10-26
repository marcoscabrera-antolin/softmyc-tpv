package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.softmyc.tpv.domain.DeserializacionVentaException;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

/** ARQ-06 / NEG-06: leer una venta corrupta debe fallar explícitamente, nunca devolver una lista parcial. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SyncManagerDeserializacionTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String LINEA_VALIDA = "{\"nombre\":\"Burger\",\"cantidad\":2,\"precio_unitario\":6.0,\"codigo\":1}";

    @Test
    public void lineasValidasSeDeserializanCompletas() {
        String json = "[" + LINEA_VALIDA + "," + LINEA_VALIDA.replace("Burger", "Patatas") + "]";

        List<PedidoItem> items = SyncManager.deserializarItems(json, null);

        assertEquals(2, items.size());
        assertEquals("Burger", items.get(0).getNombre());
        assertEquals("Patatas", items.get(1).getNombre());
    }

    @Test
    public void jsonMalformadoLanzaErrorEnLugarDeListaVacia() {
        expectDeserializacionFallida("[{\"nombre\":");
    }

    @Test
    public void unaLineaIncompletaNoDevuelveLasDemasComoListaParcial() {
        String lineaSinPrecio = "{\"nombre\":\"Patatas\",\"cantidad\":1}";
        expectDeserializacionFallida("[" + LINEA_VALIDA + "," + lineaSinPrecio + "]");
    }

    @Test
    public void jsonNuloLanzaError() {
        expectDeserializacionFallida(null);
    }

    @Test
    public void catalogoDisponibleNoOcultaUnaLineaCorrupta() {
        List<Producto> catalogo = Collections.singletonList(new Producto(1, "Burger", 6.0, "Hamburguesas", Collections.emptyList()));
        expectDeserializacionFallidaConCatalogo("[" + LINEA_VALIDA + ",{\"cantidad\":1}]", catalogo);
    }

    private static void expectDeserializacionFallida(String json) {
        expectDeserializacionFallidaConCatalogo(json, null);
    }

    private static void expectDeserializacionFallidaConCatalogo(String json, List<Producto> catalogo) {
        try {
            SyncManager.deserializarItems(json, catalogo);
            fail("Debía lanzar DeserializacionVentaException");
        } catch (DeserializacionVentaException esperada) {
            // OK: el error se comunica al llamador
        }
    }
}
