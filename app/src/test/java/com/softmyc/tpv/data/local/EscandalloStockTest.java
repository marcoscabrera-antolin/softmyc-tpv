package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.escandallo.LineaConsumo;
import com.softmyc.tpv.domain.usecase.CalculadoraRentabilidad;

import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Escandallo en la venta: el stock de ingredientes baja en la misma transacción que se guarda la venta, las
 * ediciones solo aplican la diferencia y cada línea guarda la foto de su coste.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EscandalloStockTest {

    static {
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private AppDatabase db;
    private long pollo, pan, queso, salsa;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        db.productoDao().insert(new ProductoLocal(1, "Nube", 5.5, "Bocadillos", Arrays.asList("Pollo", "Salsa Nube")));
        db.productoDao().setGastosAnadidos(1, 1.69);
        pollo = ingrediente("Pollo", "G", 1000.0, 6.99);
        pan = ingrediente("Pan", "UD", 10.0, 0.35);
        queso = ingrediente("Queso", "G", 500.0, 5.0);
        salsa = ingrediente("Salsa", "G", null, 1.66);
        receta(1, linea(pollo, 333.333, "Pollo"), linea(pan, 1, null), linea(salsa, 150.602, "Salsa Nube"));
        ExtraConsumoLocal extra = new ExtraConsumoLocal();
        extra.extra = "Queso";
        extra.extraNormalizado = "queso";
        extra.ingredienteId = queso;
        extra.cantidad = 82;
        db.escandalloDao().reemplazarExtraConsumo("queso", Collections.singletonList(extra));
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void vender_descuenta_los_ingredientes_de_la_receta() {
        registrar(item(2, null, null));

        assertEquals(333.334, stock(pollo), 1e-6);
        assertEquals(8.0, stock(pan), 1e-9);
        assertNull("sin control de stock: no cambia", stock(salsa));
    }

    @Test
    public void el_extra_descuenta_y_el_quitado_no() {
        registrar(item(1, Collections.singletonList("Pollo"), Collections.singletonList("Queso")));

        assertEquals(1000.0, stock(pollo), 1e-9);
        assertEquals(418.0, stock(queso), 1e-9);
        assertEquals(9.0, stock(pan), 1e-9);
    }

    @Test
    public void sin_descontar_stock_no_toca_ingredientes_pero_guarda_el_coste() {
        long id = VentaStockRegistrar.registrar(db, venta(item(1, null, null)), Collections.singletonList(item(1, null, null)), false);

        assertEquals(1000.0, stock(pollo), 1e-9);
        assertTrue(db.ventaDao().getById((int) id).itemsJson.contains(CostesVenta.COSTE_INGREDIENTES));
    }

    @Test
    public void el_stock_puede_quedar_negativo_y_deshacer_la_edicion_lo_devuelve_exacto() {
        long id = registrar(item(15, null, null));   // 15 panes con 10 en stock
        assertEquals(-5.0, stock(pan), 1e-9);

        editar(id, item(1, null, null));
        assertEquals(9.0, stock(pan), 1e-9);
        editar(id, item(15, null, null));
        assertEquals(-5.0, stock(pan), 1e-9);
    }

    @Test
    public void editar_una_venta_solo_aplica_la_diferencia() {
        long id = registrar(item(2, null, null));
        editar(id, item(3, null, Collections.singletonList("Queso")));

        assertEquals(1000.0 - 3 * 333.333, stock(pollo), 1e-6);
        assertEquals(7.0, stock(pan), 1e-9);
        assertEquals(500.0 - 3 * 82, stock(queso), 1e-9);

        editar(id, item(3, Collections.singletonList("pollo"), null));
        assertEquals(1000.0, stock(pollo), 1e-6);
        assertEquals(500.0, stock(queso), 1e-9);
    }

    @Test
    public void editar_una_venta_antigua_sin_codigos_no_toca_ingredientes() {
        VentaLocal antigua = new VentaLocal(1L, 5.5, "[{\"nombre\":\"Nube\",\"cantidad\":1,\"precio_unitario\":5.5}]", false, "Efectivo");
        long id = db.ventaDao().insert(antigua);

        editar(id, item(4, null, null));

        assertEquals(1000.0, stock(pollo), 1e-9);
        assertEquals(10.0, stock(pan), 1e-9);
    }

    @Test
    public void si_falla_la_venta_no_se_descuenta_nada() {
        try {
            db.runInTransaction(() -> {
                VentaStockRegistrar.registrar(db, venta(item(1, null, null)), Collections.singletonList(item(1, null, null)), true);
                throw new IllegalStateException("fallo simulado al guardar");
            });
        } catch (IllegalStateException esperado) {
            // la transacción se revierte entera
        }
        assertEquals(1000.0, stock(pollo), 1e-9);
        assertEquals(0, db.ventaDao().getAll().size());
    }

    @Test
    public void cada_linea_guarda_la_foto_de_su_coste() throws Exception {
        long id = registrar(item(2, null, Collections.singletonList("Queso")));
        JSONArray lineas = new JSONArray(db.ventaDao().getById((int) id).itemsJson);

        assertEquals(2 * (2.33 + 0.35 + 0.25 + 0.41), lineas.getJSONObject(0).getDouble(CostesVenta.COSTE_INGREDIENTES), 0.01);
        assertEquals(3.38, lineas.getJSONObject(0).getDouble(CostesVenta.GASTOS_ANADIDOS), 1e-9);

        // Si el pollo sube de precio después, la venta ya hecha conserva su beneficio.
        db.escandalloDao().actualizarIngrediente(pollo, "Pollo", "pollo", "G", 20.0);
        List<CalculadoraRentabilidad.Linea> rent = CostesVenta.lineasRentabilidad(db.ventaDao().getById((int) id).itemsJson,
                RecetarioRoom.cargar(db));
        assertEquals(2 * (2.33 + 0.35 + 0.25 + 0.41), rent.get(0).costeIngredientes, 0.01);
        assertEquals(11.0, rent.get(0).importe, 1e-9);
    }

    @Test
    public void ventas_sin_foto_se_valoran_con_la_receta_actual() {
        String json = "[{\"nombre\":\"Nube\",\"codigo\":1,\"cantidad\":1,\"precio_unitario\":5.5,\"unidad\":\"UD\"},"
                + "{\"nombre\":\"Café\",\"codigo\":7,\"cantidad\":2,\"precio_unitario\":1.2}]";

        List<CalculadoraRentabilidad.Linea> rent = CostesVenta.lineasRentabilidad(json, RecetarioRoom.cargar(db));

        assertEquals(2.93, rent.get(0).costeIngredientes, 0.01);
        assertEquals(1.69, rent.get(0).gastosAnadidos, 1e-9);
        assertEquals(5.5, rent.get(0).importe, 1e-9);
        assertNull(rent.get(1).costeIngredientes);
        assertEquals(2.4, rent.get(1).importe, 1e-9);
        assertNull(CostesVenta.lineasRentabilidad("{roto", RecetarioRoom.cargar(db)));
        assertNull(CostesVenta.lineasRentabilidad(null, RecetarioRoom.cargar(db)));
    }

    @Test
    public void lineas_de_consumo_de_una_venta_guardada() {
        List<LineaConsumo> l = CostesVenta.lineasConsumo(
                "[{\"codigo\":1,\"cantidad\":2,\"unidad\":\"UD\",\"quitados\":[\"Pollo\"],\"extras\":[\"Queso\"]}]");

        assertEquals(1, l.get(0).codigo);
        assertEquals(2, l.get(0).cantidad);
        assertEquals(Collections.singletonList("Pollo"), l.get(0).quitados);
        assertEquals(Collections.singletonList("Queso"), l.get(0).extras);
        assertNull(CostesVenta.lineasConsumo("[{\"cantidad\":2}]"));
        assertNull(CostesVenta.lineasConsumo("no es json"));
        assertNull(CostesVenta.lineasConsumo(null));
        assertEquals("no es json", CostesVenta.anotar("no es json", RecetarioRoom.cargar(db)));
        assertNull(CostesVenta.anotar(null, RecetarioRoom.cargar(db)));
    }

    @Test
    public void la_sincronizacion_del_catalogo_conserva_recetas_y_gastos() {
        CatalogSync.syncFromServer(db, Collections.singletonList(new ProductoLocal(1, "Nube servidor", 6.0, "Bocadillos",
                new ArrayList<>())), null, null);

        assertEquals(Double.valueOf(1.69), db.productoDao().getByCodigo(1).gastosAnadidos);
        assertEquals(3, db.escandalloDao().getReceta(1).size());
    }

    @Test
    public void borrar_un_ingrediente_lo_quita_de_recetas_y_extras() {
        db.escandalloDao().eliminarIngrediente(queso);
        db.escandalloDao().eliminarIngrediente(pollo);

        assertEquals(2, db.escandalloDao().getReceta(1).size());
        assertTrue(db.escandalloDao().getExtraConsumos().isEmpty());
        assertFalse(RecetarioRoom.cargar(db).ingredientes().containsKey(pollo));
    }

    // ------------------------------------------------------------------

    private long ingrediente(String nombre, String unidad, Double stock, Double precio) {
        IngredienteLocal i = new IngredienteLocal();
        i.nombre = nombre;
        i.nombreNormalizado = com.softmyc.tpv.domain.NombreNormalizado.de(nombre);
        i.unidad = unidad;
        i.stock = stock;
        i.precioCompra = precio;
        return db.escandalloDao().insertarIngrediente(i);
    }

    private static RecetaLineaLocal linea(long ingrediente, double cantidad, String enCarta) {
        RecetaLineaLocal l = new RecetaLineaLocal();
        l.ingredienteId = ingrediente;
        l.cantidad = cantidad;
        l.nombreEnCarta = enCarta;
        return l;
    }

    private void receta(int codigo, RecetaLineaLocal... lineas) {
        db.escandalloDao().reemplazarReceta(codigo, new ArrayList<>(Arrays.asList(lineas)));
    }

    private Double stock(long id) {
        return db.escandalloDao().getIngrediente(id).stock;
    }

    private static PedidoItem item(int cantidad, List<String> quitados, List<String> extras) {
        PedidoItem i = new PedidoItem(new Producto(1, "Nube", 5.5, "Bocadillos", new ArrayList<>(), "UD"), cantidad);
        if (quitados != null) i.getIngredientesQuitados().addAll(quitados);
        if (extras != null) i.getIngredientesExtra().addAll(extras);
        return i;
    }

    private static VentaLocal venta(PedidoItem item) {
        return new VentaLocal(1L, 5.5 * item.getCantidad(),
                SyncManager.serializarItems(Collections.singletonList(item), new HashMap<>()), false, "Efectivo");
    }

    private long registrar(PedidoItem item) {
        return VentaStockRegistrar.registrar(db, venta(item), Collections.singletonList(item), true);
    }

    private void editar(long id, PedidoItem item) {
        VentaLocal v = venta(item);
        v.id = (int) id;
        VentaStockRegistrar.registrarEdicion(db, v, Collections.singletonList(item));
    }
}
