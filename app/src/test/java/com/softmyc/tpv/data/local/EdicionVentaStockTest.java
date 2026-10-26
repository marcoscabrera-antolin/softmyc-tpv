package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.printer.TicketGenerator;
import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

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

/** NEG-03 (el número del ticket es el id de la venta) y NEG-04 (editar una venta ajusta el stock). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EdicionVentaStockTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private AppDatabase db;
    private final Context context = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
    }

    @After
    public void tearDown() {
        db.close();
    }

    private void producto(int codigo, int stock) {
        ProductoLocal p = new ProductoLocal(codigo, "P" + codigo, 2.0, "Cat", Collections.emptyList());
        p.stock = stock;
        db.productoDao().insert(p);
    }

    private static PedidoItem item(int codigo, int cantidad) {
        return new PedidoItem(new Producto(codigo, "P" + codigo, 2.0, "Cat", Collections.emptyList()), cantidad);
    }

    private long vender(List<PedidoItem> lista) {
        VentaLocal v = new VentaLocal(1L, 0.0, SyncManager.serializarItems(lista), false, "Efectivo");
        return VentaStockRegistrar.registrar(db, v, lista, true);
    }

    private void editar(long id, List<PedidoItem> nueva) {
        VentaLocal v = new VentaLocal(1L, 0.0, SyncManager.serializarItems(nueva), false, "Efectivo");
        v.id = (int) id;
        VentaStockRegistrar.registrarEdicion(db, v, nueva);
    }

    @Test
    public void registrar_devuelveElIdDeLaVentaGuardada() {
        long id = vender(Collections.singletonList(item(1, 1)));

        assertNotNull(db.ventaDao().getById((int) id));
    }

    @Test
    public void editarVenta_aumentarCantidadDescuentaSoloLaDiferencia() {
        producto(1, 10);
        long id = vender(Collections.singletonList(item(1, 2)));

        editar(id, Collections.singletonList(item(1, 5)));

        assertEquals(5, db.productoDao().getByCodigo(1).stock);
    }

    @Test
    public void editarVenta_quitarLineaDevuelveElStock() {
        producto(1, 10);
        producto(2, 10);
        long id = vender(Arrays.asList(item(1, 3), item(2, 4)));

        editar(id, Collections.singletonList(item(1, 3)));

        assertEquals(7, db.productoDao().getByCodigo(1).stock);
        assertEquals(10, db.productoDao().getByCodigo(2).stock);
    }

    @Test
    public void editarVenta_anadirLineaNuevaLaDescuenta() {
        producto(1, 10);
        producto(2, 10);
        long id = vender(Collections.singletonList(item(1, 1)));

        editar(id, Arrays.asList(item(1, 1), item(2, 2)));

        assertEquals(9, db.productoDao().getByCodigo(1).stock);
        assertEquals(8, db.productoDao().getByCodigo(2).stock);
    }

    @Test
    public void editarVenta_sinCambiosNoTocaElStock() {
        producto(1, 10);
        long id = vender(Collections.singletonList(item(1, 3)));

        editar(id, Collections.singletonList(item(1, 3)));

        assertEquals(7, db.productoDao().getByCodigo(1).stock);
    }

    @Test
    public void editarVenta_noDevuelveStockAProductosSinControl() {
        producto(1, -1);
        long id = vender(Collections.singletonList(item(1, 3)));

        editar(id, Collections.singletonList(item(1, 1)));

        assertEquals(-1, db.productoDao().getByCodigo(1).stock);
    }

    @Test
    public void editarVentaAntiguaSinCodigoEnElJson_noTocaElStock() {
        producto(1, 10);
        VentaLocal antigua = new VentaLocal(1L, 4.0,
                "[{\"nombre\":\"P1\",\"cantidad\":2,\"precio_unitario\":2.0}]", false, "Efectivo");
        long id = db.ventaDao().insert(antigua);

        editar(id, Collections.singletonList(item(1, 5)));

        assertEquals(10, db.productoDao().getByCodigo(1).stock);
    }

    @Test
    public void editarVentaAntiguaDosVeces_nuncaTocaElStock() {
        producto(1, 10);
        VentaLocal antigua = new VentaLocal(1L, 4.0,
                "[{\"nombre\":\"P1\",\"cantidad\":2,\"precio_unitario\":2.0}]", false, "Efectivo");
        long id = db.ventaDao().insert(antigua);

        editar(id, Collections.singletonList(item(1, 5)));
        editar(id, Collections.singletonList(item(1, 1)));

        assertEquals(10, db.productoDao().getByCodigo(1).stock);
    }

    @Test
    public void recuperarVentaDeProductoQueYaNoEstaEnElCatalogo_conservaSuCodigo() {
        String json = SyncManager.serializarItems(Collections.singletonList(item(7, 2)));

        List<PedidoItem> recuperada = SyncManager.deserializarItems(json, Collections.emptyList());

        assertEquals(7, recuperada.get(0).getId());
    }

    @Test
    public void recuperarVentaDeProductoRenombrado_loEnlazaPorCodigo() {
        String json = SyncManager.serializarItems(Collections.singletonList(item(7, 2)));
        Producto renombrado = new Producto(7, "Nombre nuevo", 3.0, "Cat", Collections.emptyList());

        List<PedidoItem> recuperada = SyncManager.deserializarItems(json, Collections.singletonList(renombrado));

        assertEquals("Nombre nuevo", recuperada.get(0).getProducto().getNombre());
    }

    @Test
    public void editarVenta_conProductoFueraDelCatalogoNoTocaOtrosProductos() {
        producto(999, 10);
        long id = vender(Collections.singletonList(item(7, 2)));
        List<PedidoItem> recuperada = SyncManager.deserializarItems(
                db.ventaDao().getById((int) id).itemsJson, Collections.emptyList());

        editar(id, recuperada);

        assertEquals(10, db.productoDao().getByCodigo(999).stock);
    }

    @Test
    public void cantidadesPorProducto_devuelveNullSiNoHayCodigos() {
        assertNull(SyncManager.cantidadesPorProducto("[]"));
        assertNull(SyncManager.cantidadesPorProducto("no es json"));
        assertNull(SyncManager.cantidadesPorProducto("[{\"nombre\":\"A\",\"cantidad\":1}]"));
    }

    @Test
    public void ticket_conNumeroExplicitoImprimeEseNumeroYNoGastaElContador() {
        List<PedidoItem> pedido = Collections.singletonList(item(1, 1));
        int antes = TicketGenerator.getSecurePrefs(context).getInt("numero_pedido", 1);

        TicketGenerator.TicketResult r = TicketGenerator.generarTicket(context, pedido, "T", new HashMap<>(), "Efectivo", 42);

        assertTrue(r.texto.contains("PEDIDO N 42"));
        assertEquals(antes, TicketGenerator.getSecurePrefs(context).getInt("numero_pedido", 1));
    }
}
