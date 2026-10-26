package com.softmyc.tpv.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;
import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.local.VentaLocal;
import com.softmyc.tpv.data.repo.ImpresionVentaFactory;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** TEST-01: el cobro completo (guardar la venta, descontar stock, imprimir) contra una impresora falsa por TCP. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ImprimirYGuardarVentaUseCaseTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Código fuera del catálogo sembrado: el sembrador de catálogo (en segundo plano) no puede pisarlo. */
    private static final int CODIGO = 90_001;

    private final Context context = ApplicationProvider.getApplicationContext();
    private final ExecutorService hiloDb = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private ServerSocket impresora;
    private final AtomicReference<byte[]> recibido = new AtomicReference<>();
    private final CountDownLatch ticketRecibido = new CountDownLatch(1);

    private <T> T enDb(Callable<T> tarea) throws Exception {
        return hiloDb.submit(tarea).get(10, TimeUnit.SECONDS);
    }

    @Before
    public void setUp() throws Exception {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putString("restaurant_id", "lanube").commit();
        db = AppDatabase.getDatabase(context);
        enDb(() -> {
            db.ventaDao().deleteAll();
            db.productoDao().deleteAll();
            ProductoLocal p = new ProductoLocal(CODIGO, "Burger", 6.0, "Hamburguesas", Collections.emptyList());
            p.stock = 10;
            db.productoDao().insert(p);
            return null;
        });
        impresora = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(() -> {
            try (Socket s = impresora.accept(); InputStream in = s.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) buf.write(b, 0, n);
                recibido.set(buf.toByteArray());
            } catch (Exception ignored) {
                // la impresora falsa se cerró
            }
            ticketRecibido.countDown();
        });
        t.setDaemon(true);
        t.start();
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putInt("impresora_puerto", impresora.getLocalPort()).commit();
    }

    @After
    public void tearDown() throws Exception {
        enDb(() -> {
            db.ventaDao().deleteAll();
            db.productoDao().deleteAll();
            return null;
        });
        hiloDb.shutdown();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(context);
        impresora.close();
        SecurePreferencesHelper.getPrinterPrefs(context).edit().remove("impresora_puerto").remove("restaurant_id").commit();
    }

    private static PedidoItem burger(int cantidad) {
        return new PedidoItem(new Producto(CODIGO, "Burger", 6.0, "Hamburguesas", Collections.emptyList()), cantidad);
    }

    /** Registra los avisos en orden y cierra el pestillo al llegar el aviso final (impreso o error). */
    private static final class Eventos implements ImprimirYGuardarVentaUseCase.Callback {
        final List<String> orden = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch fin = new CountDownLatch(1);
        volatile String error;

        @Override public void onImprimiendo() { orden.add("imprimiendo"); }
        volatile long idGuardado = -1;
        @Override public void onGuardado(long idVenta) { idGuardado = idVenta; orden.add("guardado"); }
        @Override public void onImpreso() { orden.add("impreso"); fin.countDown(); }
        @Override public void onError(String mensaje) { orden.add("error"); error = mensaje; fin.countDown(); }
    }

    private Eventos cobrar(List<PedidoItem> lista, String metodo, String hora, Integer ventaId) throws Exception {
        Eventos ev = new Eventos();
        ImpresionVentaFactory.crear(context).ejecutar(lista, "Tienda", metodo, hora, "127.0.0.1",
                new HashMap<>(), ventaId, hiloDb, ev);
        assertTrue("el cobro no terminó a tiempo", ev.fin.await(15, TimeUnit.SECONDS));
        return ev;
    }

    private String ticketImpreso() throws Exception {
        assertTrue("la impresora falsa no recibió nada", ticketRecibido.await(10, TimeUnit.SECONDS));
        return new String(recibido.get(), StandardCharsets.ISO_8859_1);
    }

    @Test
    public void cobroNormal_guardaLaVentaDescuentaStockEImprimeElTicket() throws Exception {
        Eventos ev = cobrar(Collections.singletonList(burger(2)), "Tarjeta", null, null);

        assertEquals(List.of("guardado", "imprimiendo", "impreso"), ev.orden);
        List<VentaLocal> ventas = enDb(() -> db.ventaDao().getAll());
        assertEquals(1, ventas.size());
        assertEquals(12.0, ventas.get(0).total, 0.001);
        assertEquals("Tarjeta", ventas.get(0).metodoPago);
        assertFalse(ventas.get(0).isSynced);
        assertNotNull(ventas.get(0).uuid);
        assertEquals(8, (int) enDb(() -> db.productoDao().getByCodigo(CODIGO).stock));
        String ticket = ticketImpreso();
        assertTrue(ticket.contains("Burger"));
        assertTrue("el ticket lleva el id de la venta", ticket.contains("PEDIDO N " + ventas.get(0).id));
        assertTrue("termina con el corte de papel", ticket.endsWith("\u001dV\u0001"));
    }

    @Test
    public void elCallbackRecibeElIdRealDeLaVentaGuardada() throws Exception {
        Eventos ev = cobrar(Collections.singletonList(burger(1)), "Efectivo", null, null);

        VentaLocal guardada = enDb(() -> db.ventaDao().getAll()).get(0);
        assertEquals(guardada.id, ev.idGuardado);
    }

    @Test
    public void laVentaSeGuardaAntesDeImprimir() throws Exception {
        Eventos ev = cobrar(Collections.singletonList(burger(1)), "Efectivo", null, null);

        assertTrue(ev.orden.indexOf("guardado") < ev.orden.indexOf("imprimiendo"));
    }

    @Test
    public void impresoraCaida_laVentaQuedaGuardadaYSeAvisaDelFallo() throws Exception {
        int puertoMuerto = impresora.getLocalPort();
        impresora.close();   // nadie escucha
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putInt("impresora_puerto", puertoMuerto).commit();

        Eventos ev = cobrar(Collections.singletonList(burger(1)), "Efectivo", null, null);

        assertEquals(List.of("guardado", "imprimiendo", "error"), ev.orden);
        assertTrue(ev.error.contains("Venta guardada, pero falló la impresión"));
        assertEquals(1, enDb(() -> db.ventaDao().getAll()).size());
        assertEquals(9, (int) enDb(() -> db.productoDao().getByCodigo(CODIGO).stock));
    }

    @Test
    public void pedidoVacio_noGuardaNadaYAvisa() throws Exception {
        Eventos ev = cobrar(new ArrayList<>(), "Efectivo", null, null);

        assertEquals(List.of("error"), ev.orden);
        assertEquals("Pedido vacío", ev.error);
        assertTrue(enDb(() -> db.ventaDao().getAll()).isEmpty());
    }

    @Test
    public void horaDeRecogida_apareceEnElTicket() throws Exception {
        cobrar(Collections.singletonList(burger(1)), "Efectivo", "21:30", null);

        assertTrue(ticketImpreso().contains("RECOGIDA: 21:30"));
    }

    @Test
    public void editarUnaVenta_conservaIdentidadYFechaYAjustaElStock() throws Exception {
        cobrar(Collections.singletonList(burger(2)), "Efectivo", null, null);
        VentaLocal original = enDb(() -> db.ventaDao().getAll()).get(0);
        // El segundo cobro no tiene impresora (la falsa solo atiende una conexión): solo se comprueba lo guardado.
        impresora.close();
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putInt("impresora_puerto", 1).commit();

        Eventos ev = cobrar(Collections.singletonList(burger(5)), "Tarjeta", null, original.id);

        assertEquals("guardado", ev.orden.get(0));
        List<VentaLocal> ventas = enDb(() -> db.ventaDao().getAll());
        assertEquals("editar no crea otra venta", 1, ventas.size());
        assertEquals(original.uuid, ventas.get(0).uuid);
        assertEquals(original.fecha, ventas.get(0).fecha);
        assertEquals(30.0, ventas.get(0).total, 0.001);
        assertEquals("Tarjeta", ventas.get(0).metodoPago);
        assertFalse("tras editar vuelve a estar pendiente de subir", ventas.get(0).isSynced);
        assertEquals("se descontó 2 y luego 3 más", 5, (int) enDb(() -> db.productoDao().getByCodigo(CODIGO).stock));
    }

    @Test
    public void productoSinControlDeStock_noSeToca() throws Exception {
        enDb(() -> {
            db.productoDao().setStock(CODIGO, -1);
            return null;
        });

        cobrar(Collections.singletonList(burger(3)), "Efectivo", null, null);

        assertEquals(-1, (int) enDb(() -> db.productoDao().getByCodigo(CODIGO).stock));
    }

    @Test
    public void ventaConPesoYTara_guardaElImporteDelMotorDePrecios() throws Exception {
        Producto fruta = new Producto(CODIGO, "Manzanas", 3.0, "Frutas", Collections.emptyList(), "KG");
        PedidoItem item = new PedidoItem(fruta, 2);
        item.setCantidadMedida(1.850);
        item.setUnidadMedida("KG");
        item.setTaraKg(0.120);

        cobrar(Collections.singletonList(item), "Efectivo", null, null);

        VentaLocal v = enDb(() -> db.ventaDao().getAll()).get(0);
        assertEquals("1,850 kg - 0,120 kg de tara a 3 EUR/kg", 5.19, v.total, 0.001);
        assertTrue(v.itemsJson.contains("\"cantidad_medida\":1.85"));
    }

    @Test
    public void ventaConExtras_sumaElPrecioDelExtraAlTotal() throws Exception {
        PedidoItem item = burger(2);
        item.getIngredientesExtra().add("Queso");
        java.util.Map<String, Double> precios = new HashMap<>();
        precios.put("Queso", 1.0);
        Eventos ev = new Eventos();
        ImpresionVentaFactory.crear(context).ejecutar(Collections.singletonList(item), "Tienda", "Efectivo", null,
                "127.0.0.1", precios, null, hiloDb, ev);
        assertTrue(ev.fin.await(15, TimeUnit.SECONDS));

        assertEquals(14.0, enDb(() -> db.ventaDao().getAll()).get(0).total, 0.001);
    }

    @Test
    public void puertoDeImpresoraFueraDeRango_seUsaElPorDefectoYNoSeHundeElCobro() throws Exception {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putInt("impresora_puerto", 70_000).commit();

        Eventos ev = cobrar(Collections.singletonList(burger(1)), "Efectivo", null, null);

        assertEquals("guardado", ev.orden.get(0));
        assertEquals(1, enDb(() -> db.ventaDao().getAll()).size());
    }

    @Test
    public void dosCobrosSeguidosDelMismoProducto_descuentanElStockDeAmbos() throws Exception {
        cobrar(Collections.singletonList(burger(3)), "Efectivo", null, null);
        cobrar(Collections.singletonList(burger(4)), "Efectivo", null, null);

        assertEquals(2, enDb(() -> db.ventaDao().getAll()).size());
        assertEquals(3, (int) enDb(() -> db.productoDao().getByCodigo(CODIGO).stock));
    }
}
