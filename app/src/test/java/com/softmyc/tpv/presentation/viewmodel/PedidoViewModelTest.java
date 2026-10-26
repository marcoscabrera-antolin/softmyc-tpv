package com.softmyc.tpv.presentation.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.FakeApiServer;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;
import com.softmyc.tpv.data.local.VentaLocal;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.PedidoItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Lógica del pedido en curso del {@link PedidoViewModel}: selección, carrito, edición y consultas a la base local.
 * Las cargas de Room corren en el hilo de disco del ViewModel; se espera a que lleguen con {@link #esperarA}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PedidoViewModelTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final long TIMEOUT_MS = 5000;
    /** Negocio fuera de los catálogos iniciales: su base no se siembra en segundo plano y solo tiene lo que el test inserta. */
    private static final String NEGOCIO_SIN_CATALOGO = "pedidotest";

    private Application app;
    private FakeApiServer server;
    private ExecutorService fondo;
    private PedidoViewModel vm;

    @Before
    public void setUp() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
        server = new FakeApiServer();
        // El ViewModel intenta sincronizar con el servidor: se apunta a un servidor de prueba que no tiene datos
        // (404) para que la descarga del catálogo no escriba en la base mientras corre el test.
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear()
                .putString("backend_base_url", server.baseUrl() + "/api/tpv/orders")
                .putString("backend_api_key", "clave-test")
                .commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO_SIN_CATALOGO);
        fondo = Executors.newSingleThreadExecutor();
        sembrarCatalogo(
                producto(3, "Tarta", 4.0, "postres", null),
                producto(1, "Café", 1.5, "bebidas", null),
                producto(2, "Bocadillo", 3.0, "bocadillos", 7));
        vm = new PedidoViewModel(app);
        esperarA(() -> vm.productosDisponibles.getValue() != null && vm.productosDisponibles.getValue().size() == 3);
    }

    @After
    public void tearDown() {
        server.close();
        fondo.shutdownNow();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(app);
    }

    @Test
    public void catalogo_se_carga_desde_room_ordenado_por_codigo() {
        List<Producto> productos = vm.productosDisponibles.getValue();

        assertEquals(Arrays.asList(1, 2, 3), codigos(productos));
        assertEquals("Café", productos.get(0).getNombre());
    }

    @Test
    public void categorias_incluyen_todos_y_una_entrada_por_categoria_sin_repetir() {
        List<String> nombres = new ArrayList<>();
        for (com.softmyc.tpv.domain.Category c : vm.categorias.getValue()) {
            nombres.add(c.getName());
        }

        assertEquals("Todos", nombres.get(0));
        assertEquals(4, nombres.size());
        assertTrue(nombres.contains("Postres"));
    }

    @Test
    public void stockDisponible_devuelve_el_stock_local_o_menos_uno_si_no_se_controla() {
        assertEquals(7, vm.stockDisponible(2));
        assertEquals(-1, vm.stockDisponible(3));
        assertEquals(-1, vm.stockDisponible(999));
    }

    @Test
    public void seleccionarProductoPorId_prepara_el_item_temporal_con_la_cantidad_actual() {
        vm.actualizarCantidad(3);

        vm.seleccionarProductoPorId(3);

        assertEquals("Tarta", vm.productoActual.getValue().getNombre());
        assertEquals(3, vm.itemTemporal.getValue().getCantidad());
        assertEquals("Seleccionado: Tarta", vm.toastMessage.getValue());
    }

    @Test
    public void seleccionarProductoPorId_con_codigo_inexistente_avisa_y_no_cambia_el_item() {
        vm.seleccionarProductoPorId(999);

        assertNull(vm.itemTemporal.getValue());
        assertEquals("ID 999 no encontrado", vm.toastMessage.getValue());
    }

    @Test
    public void seleccionarProductoPorId_sin_catalogo_cargado_avisa_de_la_busqueda_local() {
        vm.productosDisponibles.getValue().clear();

        vm.seleccionarProductoPorId(3);

        assertEquals("Buscando en base de datos local...", vm.toastMessage.getValue());
        assertNull(vm.itemTemporal.getValue());
    }

    @Test
    public void agregarAlPedido_sin_seleccion_avisa_y_no_modifica_el_pedido() {
        vm.agregarAlPedido();

        assertEquals("Selecciona un producto primero", vm.toastMessage.getValue());
        assertTrue(vm.pedido.getValue().isEmpty());
    }

    @Test
    public void agregarAlPedido_anade_el_item_recalcula_el_total_y_limpia_la_seleccion() {
        vm.seleccionarProductoPorId(3);
        vm.actualizarCantidad(2);

        vm.agregarAlPedido();

        assertEquals(1, vm.pedido.getValue().size());
        assertEquals(8.0, vm.totalPedido.getValue(), 1e-9);
        assertNull(vm.itemTemporal.getValue());
        assertNull(vm.productoActual.getValue());
        assertEquals(Integer.valueOf(1), vm.cantidadActual.getValue());
    }

    @Test
    public void agregarCustomizedItem_nulo_o_sin_producto_no_hace_nada() {
        vm.agregarCustomizedItem(null);

        assertTrue(vm.pedido.getValue().isEmpty());
        assertNull(vm.toastMessage.getValue());
    }

    @Test
    public void eliminarItem_con_posicion_invalida_deja_el_pedido_intacto() {
        vm.setPedidoDirectamente(new ArrayList<>(Collections.singletonList(itemTarta(1))));

        vm.eliminarItem(5);
        vm.eliminarItem(-1);

        assertEquals(1, vm.pedido.getValue().size());
    }

    @Test
    public void eliminarItem_quita_la_linea_y_actualiza_el_total() {
        vm.setPedidoDirectamente(new ArrayList<>(Arrays.asList(itemTarta(1), itemCafe(2))));

        vm.eliminarItem(0);

        assertEquals(1, vm.pedido.getValue().size());
        assertEquals(3.0, vm.totalPedido.getValue(), 1e-9);
    }

    @Test
    public void modificarItemCantidad_suma_y_recalcula_el_total() {
        vm.setPedidoDirectamente(new ArrayList<>(Collections.singletonList(itemCafe(1))));

        vm.modificarItemCantidad(0, 2);

        assertEquals(3, vm.pedido.getValue().get(0).getCantidad());
        assertEquals(4.5, vm.totalPedido.getValue(), 1e-9);
    }

    @Test
    public void modificarItemCantidad_que_bajaria_de_uno_no_cambia_nada() {
        vm.setPedidoDirectamente(new ArrayList<>(Collections.singletonList(itemCafe(1))));

        vm.modificarItemCantidad(0, -1);

        assertEquals(1, vm.pedido.getValue().get(0).getCantidad());
        assertEquals(1.5, vm.totalPedido.getValue(), 1e-9);
    }

    @Test
    public void restaurarItem_inserta_en_la_posicion_original_y_acota_posiciones_fuera_de_rango() {
        vm.setPedidoDirectamente(new ArrayList<>(Arrays.asList(itemCafe(1), itemCafe(1))));

        vm.restaurarItem(1, itemTarta(1));
        vm.restaurarItem(99, itemTarta(1));

        List<PedidoItem> pedido = vm.pedido.getValue();
        assertEquals(4, pedido.size());
        assertEquals("Tarta", pedido.get(1).getProducto().getNombre());
        assertEquals("Tarta", pedido.get(3).getProducto().getNombre());
    }

    @Test
    public void restaurarItem_nulo_no_hace_nada() {
        vm.setPedidoDirectamente(new ArrayList<>(Collections.singletonList(itemCafe(1))));

        vm.restaurarItem(0, null);

        assertEquals(1, vm.pedido.getValue().size());
    }

    @Test
    public void iniciarEdicion_saca_la_linea_del_pedido_y_la_deja_como_item_temporal() {
        vm.setPedidoDirectamente(new ArrayList<>(Arrays.asList(itemCafe(1), itemTarta(4))));

        vm.iniciarEdicion(1);

        assertEquals(1, vm.pedido.getValue().size());
        assertEquals("Tarta", vm.itemTemporal.getValue().getProducto().getNombre());
        assertEquals(4, vm.itemTemporal.getValue().getCantidad());
        assertEquals(Integer.valueOf(4), vm.cantidadActual.getValue());
        assertEquals("Editando: Tarta", vm.toastMessage.getValue());
    }

    @Test
    public void iniciarEdicion_con_posicion_fuera_de_rango_no_cambia_nada() {
        vm.setPedidoDirectamente(new ArrayList<>(Collections.singletonList(itemCafe(1))));

        vm.iniciarEdicion(3);

        assertEquals(1, vm.pedido.getValue().size());
        assertNull(vm.itemTemporal.getValue());
    }

    @Test
    public void filterByCategory_null_o_todos_muestra_el_catalogo_completo() {
        vm.filterByCategory(null);
        assertEquals(3, vm.productosFiltrados.getValue().size());

        vm.filterByCategory("TODOS");
        assertEquals(3, vm.productosFiltrados.getValue().size());
    }

    @Test
    public void filterByCategory_filtra_sin_distinguir_mayusculas() {
        vm.filterByCategory("POSTRES");

        assertEquals(Collections.singletonList(3), codigos(vm.productosFiltrados.getValue()));
    }

    @Test
    public void filterByCategory_categoria_desconocida_devuelve_lista_vacia() {
        vm.filterByCategory("Ferretería");

        assertTrue(vm.productosFiltrados.getValue().isEmpty());
    }

    @Test
    public void vaciarPedido_limpia_lineas_total_hora_y_venta_recuperada() {
        vm.setPedidoDirectamente(new ArrayList<>(Collections.singletonList(itemTarta(2))));
        vm.actualizarHoraRecogida("13:30");
        vm.setRecuperadoVentaId(42);

        vm.vaciarPedido();

        assertTrue(vm.pedido.getValue().isEmpty());
        assertEquals(0.0, vm.totalPedido.getValue(), 1e-9);
        assertEquals("", vm.horaRecogida.getValue());
        assertNull(vm.getRecuperadoVentaId());
    }

    @Test
    public void setIngredientesQuitados_y_extra_sustituyen_las_listas_del_item_temporal() {
        vm.seleccionarProductoPorId(3);

        vm.setIngredientesQuitados(Arrays.asList("nata"));
        vm.setIngredientesExtra(Arrays.asList("chocolate"));
        vm.setSalsas(Arrays.asList("caramelo"));
        vm.setSalsas(Arrays.asList("fresa"));

        PedidoItem temporal = vm.itemTemporal.getValue();
        assertEquals(Collections.singletonList("nata"), temporal.getIngredientesQuitados());
        assertEquals(Collections.singletonList("chocolate"), temporal.getIngredientesExtra());
        assertEquals(Collections.singletonList("fresa"), temporal.getSalsas());
    }

    @Test
    public void setIngredientes_sin_item_temporal_no_lanza() {
        vm.setIngredientesQuitados(Arrays.asList("nata"));
        vm.setIngredientesExtra(Arrays.asList("chocolate"));
        vm.setSalsas(Arrays.asList("fresa"));

        assertNull(vm.itemTemporal.getValue());
    }

    @Test
    public void actualizarCantidad_se_aplica_al_item_temporal_si_existe() {
        vm.seleccionarProductoPorId(1);

        vm.actualizarCantidad(5);

        assertEquals(5, vm.itemTemporal.getValue().getCantidad());
        assertEquals(Integer.valueOf(5), vm.cantidadActual.getValue());
    }

    @Test
    public void cargarConfiguracionImpresora_usa_la_ip_por_defecto_y_la_guardada() {
        vm.cargarConfiguracionImpresora();
        assertEquals("192.168.0.20", vm.ipImpresora.getValue());

        SecurePreferencesHelper.getPrinterPrefs(app).edit().putString("impresora_ip", "10.0.0.9").commit();
        vm.cargarConfiguracionImpresora();

        assertEquals("10.0.0.9", vm.ipImpresora.getValue());
    }

    @Test
    public void clearToast_y_clearFalloImpresion_dejan_los_mensajes_a_null() {
        vm.agregarAlPedido();
        vm.clearToast();
        vm.clearFalloImpresion();

        assertNull(vm.toastMessage.getValue());
        assertNull(vm.falloImpresion.getValue());
    }

    @Test
    public void actualizarHoraRecogida_publica_la_hora() {
        vm.actualizarHoraRecogida("09:15");

        assertEquals("09:15", vm.horaRecogida.getValue());
    }

    @Test
    public void getVentasRecientes_sin_ventas_entrega_lista_vacia() throws Exception {
        List<com.softmyc.tpv.domain.repository.VentaResumen> recibidas = new ArrayList<>();
        CountDownLatch hecho = new CountDownLatch(1);

        vm.getVentasRecientes(ventas -> {
            recibidas.addAll(ventas);
            hecho.countDown();
        });

        assertTrue(hecho.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(recibidas.isEmpty());
    }

    @Test
    public void getVentasRecientes_devuelve_como_mucho_las_cinco_ultimas() throws Exception {
        for (int i = 0; i < 7; i++) {
            insertarVenta(1000L + i, 1.0 + i, "[]");
        }
        List<com.softmyc.tpv.domain.repository.VentaResumen> recibidas = new ArrayList<>();
        CountDownLatch hecho = new CountDownLatch(1);

        vm.getVentasRecientes(ventas -> {
            recibidas.addAll(ventas);
            hecho.countDown();
        });

        assertTrue(hecho.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(5, recibidas.size());
    }

    @Test
    public void cargarVentaPorId_inexistente_informa_del_numero_buscado() throws Exception {
        String[] error = new String[1];
        CountDownLatch hecho = new CountDownLatch(1);

        vm.cargarVentaPorId(999, (items, metodoPago, mensajeError) -> {
            error[0] = mensajeError;
            hecho.countDown();
        });

        assertTrue(hecho.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals("No se encontró el número de pedido 999", error[0]);
    }

    @Test
    public void cargarVentaPorId_existente_devuelve_items_y_metodo_de_pago() throws Exception {
        long id = insertarVenta(1L, 3.0, "[]");
        List<PedidoItem>[] items = new List[1];
        String[] metodo = new String[1];
        String[] error = new String[1];
        CountDownLatch hecho = new CountDownLatch(1);

        vm.cargarVentaPorId((int) id, (lista, metodoPago, mensajeError) -> {
            items[0] = lista;
            metodo[0] = metodoPago;
            error[0] = mensajeError;
            hecho.countDown();
        });

        assertTrue(hecho.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertNull(error[0]);
        assertTrue(items[0].isEmpty());
        assertEquals("Efectivo", metodo[0]);
    }

    @Test
    public void cargarVentaPorId_con_json_corrupto_no_lanza_al_llamante() throws Exception {
        long id = insertarVenta(2L, 3.0, "{no es json");
        String[] error = new String[1];
        List<PedidoItem>[] items = new List[1];
        CountDownLatch hecho = new CountDownLatch(1);

        vm.cargarVentaPorId((int) id, (lista, metodoPago, mensajeError) -> {
            items[0] = lista;
            error[0] = mensajeError;
            hecho.countDown();
        });

        assertTrue(hecho.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertNull("una venta ilegible no se carga como pedido vacío", items[0]);
        assertNotNull(error[0]);
    }

    /**
     * NEG-06: {@code SyncManager.deserializarItems} ya no traga el JSON corrupto; el error llega al llamante.
     */
    @Test
    public void cargarVentaPorId_con_json_corrupto_informa_de_error() throws Exception {
        long id = insertarVenta(2L, 3.0, "{no es json");
        String[] error = new String[1];
        CountDownLatch hecho = new CountDownLatch(1);

        vm.cargarVentaPorId((int) id, (lista, metodoPago, mensajeError) -> {
            error[0] = mensajeError;
            hecho.countDown();
        });

        assertTrue(hecho.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertNotNull(error[0]);
    }

    @Test
    public void al_arrancar_descarga_el_catalogo_del_servidor_y_sustituye_el_local() throws Exception {
        server.on("/api/tpv/products", 200,
                "{\"success\":true,"
                        + "\"products\":[{\"codigo\":50,\"nombre\":\"Empanada\",\"precio\":2.25,"
                        + "\"categoria\":\"bocadillos\",\"ingredientes\":[\"carne\"]}],"
                        + "\"extras\":[{\"nombre\":\"Extra Queso\",\"precio\":0.5}],"
                        + "\"salsas\":[\"Picante\"]}");

        PedidoViewModel recargado = new PedidoViewModel(app);
        esperarA(() -> recargado.productosDisponibles.getValue() != null
                && recargado.productosDisponibles.getValue().size() == 1
                && recargado.ingredientesDisponibles.getValue() != null
                && !recargado.ingredientesDisponibles.getValue().isEmpty());

        assertEquals(Collections.singletonList(50), codigos(recargado.productosDisponibles.getValue()));
        assertEquals("Empanada", recargado.productosDisponibles.getValue().get(0).getNombre());
        assertEquals(Collections.singletonList("Extra Queso"), recargado.ingredientesDisponibles.getValue());
        assertEquals(0.5, recargado.preciosIngredientes.getValue().get("Extra Queso"), 1e-9);
        assertEquals(Collections.singletonList("Picante"), recargado.salsasDisponibles.getValue());
        assertTrue(recargado.categorias.getValue().stream().anyMatch(c -> "Bocadillos".equals(c.getName())));
    }

    @Test
    public void una_respuesta_del_servidor_sin_productos_no_toca_el_catalogo_local() throws Exception {
        server.on("/api/tpv/products", 200, "{\"success\":true,\"products\":[]}");

        PedidoViewModel recargado = new PedidoViewModel(app);
        esperarA(() -> recargado.productosDisponibles.getValue() != null
                && recargado.productosDisponibles.getValue().size() == 3);
        esperarA(() -> server.countOf("/api/tpv/products") >= 1);
        Thread.sleep(200);
        ShadowLooper.idleMainLooper();

        assertEquals(Arrays.asList(1, 2, 3), codigos(recargado.productosDisponibles.getValue()));
    }

    @Test
    public void un_error_del_servidor_al_descargar_deja_el_catalogo_local() throws Exception {
        server.on("/api/tpv/products", 500, "{\"error\":\"fallo\"}");

        PedidoViewModel recargado = new PedidoViewModel(app);
        esperarA(() -> recargado.productosDisponibles.getValue() != null
                && recargado.productosDisponibles.getValue().size() == 3);
        esperarA(() -> server.countOf("/api/tpv/products") >= 1);
        Thread.sleep(200);
        ShadowLooper.idleMainLooper();

        assertEquals(Arrays.asList(1, 2, 3), codigos(recargado.productosDisponibles.getValue()));
    }

    private static List<Integer> codigos(List<Producto> productos) {
        List<Integer> codigos = new ArrayList<>();
        for (Producto p : productos) {
            codigos.add(p.getCodigo());
        }
        return codigos;
    }

    private static ProductoLocal producto(int codigo, String nombre, double precio, String categoria, Integer stock) {
        ProductoLocal producto = new ProductoLocal(codigo, nombre, precio, categoria, new ArrayList<>());
        if (stock != null) {
            producto.stock = stock;
        }
        return producto;
    }

    private PedidoItem itemTarta(int cantidad) {
        return new PedidoItem(new Producto(3, "Tarta", 4.0, "Postres", new ArrayList<>(), "UD"), cantidad);
    }

    private PedidoItem itemCafe(int cantidad) {
        return new PedidoItem(new Producto(1, "Café", 1.5, "Bebidas", new ArrayList<>(), "UD"), cantidad);
    }

    private long insertarVenta(long fecha, double total, String itemsJson) throws Exception {
        Future<Long> id = fondo.submit(() -> AppDatabase.getDatabase(app).ventaDao()
                .insert(new VentaLocal(fecha, total, itemsJson, false, "Efectivo")));
        return id.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void sembrarCatalogo(ProductoLocal... productos) throws Exception {
        Future<?> alta = fondo.submit(() -> AppDatabase.getDatabase(app).productoDao()
                .insertAll(Arrays.asList(productos)));
        alta.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /** Procesa la cola del hilo principal hasta que se cumpla la condición (los LiveData llegan con postValue). */
    private static void esperarA(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("Tiempo agotado esperando la carga inicial del ViewModel");
            }
            Thread.sleep(10);
        }
        ShadowLooper.idleMainLooper();
    }
}
