package com.softmyc.tpv.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.port.AjustesImpresora;
import com.softmyc.tpv.domain.port.ItemsSerializer;
import com.softmyc.tpv.domain.port.PrinterSettings;
import com.softmyc.tpv.domain.port.TicketComposer;
import com.softmyc.tpv.domain.port.TicketPrinter;
import com.softmyc.tpv.domain.port.VentaCobro;
import com.softmyc.tpv.domain.port.VentaStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Cobro en JVM pura: los puertos se sustituyen por dobles que registran lo que reciben. */
public class ImprimirYGuardarVentaUseCaseJvmTest {

    private static final long AHORA = 1_700_000_000_000L;
    private static final long ID_VENTA = 7L;
    private static final String IP = "10.0.0.5";

    private final List<String> orden = Collections.synchronizedList(new ArrayList<>());
    private final List<VentaCobro> guardados = Collections.synchronizedList(new ArrayList<>());
    private final List<String> impresos = Collections.synchronizedList(new ArrayList<>());
    private final List<AjustesImpresora> ajustesUsados = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private RuntimeException errorDeGuardado;
    private RuntimeException errorDeComposicionTrasGuardar;
    private boolean impresionFalla;
    private String motivoFalloImpresion;
    private AjustesImpresora ajustes = AjustesImpresora.of(0, 9100);

    private ImprimirYGuardarVentaUseCase useCase;

    @Before
    public void setUp() {
        VentaStore store = cobro -> {
            orden.add("guardar");
            if (errorDeGuardado != null) throw errorDeGuardado;
            guardados.add(cobro);
            return cobro.ventaId != null ? cobro.ventaId : ID_VENTA;
        };
        TicketComposer composer = (lineas, tienda, precios, metodo, numero) -> {
            if (numero > 0) {
                orden.add("componer-ticket");
                if (errorDeComposicionTrasGuardar != null) throw errorDeComposicionTrasGuardar;
            }
            String texto = "TICKET " + tienda + " n=" + numero + " items=" + lineas.size();
            return new TicketComposer.Composicion(texto, 12.5);
        };
        ItemsSerializer serializer = (items, precios) -> "json-" + items.size();
        TicketPrinter printer = (ip, ajustesImpresora, texto, resultado) -> {
            orden.add("imprimir");
            ajustesUsados.add(ajustesImpresora);
            impresos.add(texto);
            if (impresionFalla) resultado.onResultado(false, motivoFalloImpresion);
            else resultado.onResultado(true, null);
        };
        PrinterSettings settings = () -> ajustes;
        useCase = new ImprimirYGuardarVentaUseCase(store, composer, serializer, printer, settings, () -> AHORA);
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }

    private static PedidoItem linea(int cantidad) {
        return new PedidoItem(new Producto(1, "Burger", 6.0, "Hamburguesas", Collections.emptyList()), cantidad);
    }

    /** Ejecuta el cobro y espera al aviso final (impreso o error). */
    private Eventos cobrar(List<PedidoItem> lista, String hora, Integer ventaId) throws InterruptedException {
        Eventos ev = new Eventos();
        useCase.ejecutar(lista, "Tienda", "Efectivo", hora, IP, new HashMap<>(), ventaId, executor, ev);
        assertTrue("el cobro no terminó", ev.fin.await(5, TimeUnit.SECONDS));
        return ev;
    }

    private static final class Eventos implements ImprimirYGuardarVentaUseCase.Callback {
        final List<String> avisos = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch fin = new CountDownLatch(1);
        volatile long idGuardado = -1;
        volatile String error;

        @Override public void onImprimiendo() { avisos.add("imprimiendo"); }
        @Override public void onGuardado(long idVenta) { idGuardado = idVenta; avisos.add("guardado"); }
        @Override public void onImpreso() { avisos.add("impreso"); fin.countDown(); }
        @Override public void onError(String mensaje) { avisos.add("error"); error = mensaje; fin.countDown(); }
    }

    @Test
    public void cobroNormal_guardaAntesDeImprimirYNotificaElIdReal() throws Exception {
        Eventos ev = cobrar(Collections.singletonList(linea(2)), null, null);

        assertEquals(List.of("guardar", "componer-ticket", "imprimir"), orden);
        assertEquals(List.of("guardado", "imprimiendo", "impreso"), ev.avisos);
        assertEquals(ID_VENTA, ev.idGuardado);
        assertEquals(1, guardados.size());
    }

    @Test
    public void ventaNueva_seGuardaConTotalItemsMetodoYHoraDelReloj() throws Exception {
        cobrar(Collections.singletonList(linea(2)), null, null);

        VentaCobro c = guardados.get(0);
        assertNull(c.ventaId);
        assertEquals(12.5, c.total, 0.001);
        assertEquals("json-1", c.itemsJson);
        assertEquals("Efectivo", c.metodoPago);
        assertEquals(AHORA, c.ahoraMs);
    }

    @Test
    public void ticketImpreso_llevaElIdDeLaVentaYLasColumnasDelAjuste() throws Exception {
        ajustes = AjustesImpresora.of(2, 9100);

        cobrar(Collections.singletonList(linea(1)), null, null);

        assertEquals("TICKET Tienda n=7 items=1", impresos.get(0));
        assertEquals(24, ajustesUsados.get(0).columnas());
    }

    @Test
    public void horaDeRecogida_seAnadeAlTicket() throws Exception {
        cobrar(Collections.singletonList(linea(1)), "21:30", null);

        assertTrue(impresos.get(0).contains("RECOGIDA: 21:30"));
    }

    @Test
    public void errorDeGuardado_avisaYNoImprime() throws Exception {
        errorDeGuardado = new IllegalStateException("disco lleno");

        Eventos ev = cobrar(Collections.singletonList(linea(1)), null, null);

        assertEquals(List.of("error"), ev.avisos);
        assertEquals("Error al guardar venta: disco lleno", ev.error);
        assertFalse(orden.contains("imprimir"));
    }

    @Test
    public void fallaImpresion_laVentaYaEstaGuardadaYSeAvisa() throws Exception {
        impresionFalla = true;
        motivoFalloImpresion = "timeout";

        Eventos ev = cobrar(Collections.singletonList(linea(1)), null, null);

        assertEquals(List.of("guardado", "imprimiendo", "error"), ev.avisos);
        assertEquals(1, guardados.size());
        assertTrue(ev.error.startsWith("⚠️ Venta guardada, pero falló la impresión del ticket (timeout)"));
    }

    @Test
    public void fallaGenerarTicketTrasGuardar_noSeDuplicaLaVenta() throws Exception {
        errorDeComposicionTrasGuardar = new IllegalStateException("plantilla");

        Eventos ev = cobrar(Collections.singletonList(linea(1)), null, null);

        assertEquals(List.of("guardado", "error"), ev.avisos);
        assertEquals(ID_VENTA, ev.idGuardado);
        assertTrue(ev.error.contains("ticket n.º 7"));
        assertFalse(orden.contains("imprimir"));
    }

    @Test
    public void edicion_pasaElIdDeLaVentaAlGuardado() throws Exception {
        Eventos ev = cobrar(Collections.singletonList(linea(3)), null, 42);

        assertEquals(List.of("guardado", "imprimiendo", "impreso"), ev.avisos);
        assertEquals(Integer.valueOf(42), guardados.get(0).ventaId);
        assertEquals(42L, ev.idGuardado);
    }

    @Test
    public void pedidoVacio_noGuardaNiImprimeYAvisa() throws Exception {
        Eventos ev = new Eventos();

        useCase.ejecutar(new ArrayList<>(), "Tienda", "Efectivo", null, IP, new HashMap<>(), null, executor, ev);

        assertEquals("Pedido vacío", ev.error);
        assertEquals(List.of("error"), ev.avisos);
        assertTrue(orden.isEmpty());
    }

    @Test
    public void pedidoNulo_seTrataComoVacio() {
        Eventos ev = new Eventos();

        useCase.ejecutar(null, "Tienda", "Efectivo", null, IP, new HashMap<>(), null, executor, ev);

        assertEquals("Pedido vacío", ev.error);
        assertTrue(orden.isEmpty());
    }

    @Test
    public void metodoPagoYExtras_llegaNAlGuardadoEImpresion() throws Exception {
        Map<String, Double> precios = new HashMap<>();
        precios.put("Queso", 1.0);
        Eventos ev = new Eventos();

        useCase.ejecutar(Collections.singletonList(linea(1)), "Tienda", "Tarjeta", null, IP, precios, null, executor, ev);
        assertTrue(ev.fin.await(5, TimeUnit.SECONDS));

        assertEquals("Tarjeta", guardados.get(0).metodoPago);
        assertTrue(ev.avisos.contains("impreso"));
    }
}
