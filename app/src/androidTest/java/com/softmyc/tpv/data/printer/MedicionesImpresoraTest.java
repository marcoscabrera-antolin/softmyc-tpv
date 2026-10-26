package com.softmyc.tpv.data.printer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.data.repo.ImpresionVentaFactory;
import com.softmyc.tpv.domain.usecase.ImprimirYGuardarVentaUseCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mediciones con impresora real (MC-02 completo, RNF-05) y de los fallos de impresión.
 * Usa un negocio temporal "bench": no toca las ventas de los negocios reales. Resultados por logcat (etiqueta MC_PRINT).
 * Argumentos opcionales: -e printerIp 192.168.0.20 -e unreachableIp 192.168.0.250 -e refusedIp 192.168.0.30
 *
 * El «impreso» del caso de uso se notifica cuando los bytes se han entregado al socket de la impresora, no cuando
 * el papel termina de salir: el tiempo físico de impresión no se puede medir desde el código.
 */
@RunWith(AndroidJUnit4.class)
public class MedicionesImpresoraTest {
    private static final String TAG = "MC_PRINT";
    private static final String BENCH_TENANT = "bench";
    private static final int WARMUP = 1;
    private static final int COBROS = 10;

    private Context context;
    private String previousTenant;
    private String printerIp;
    private String unreachableIp;
    private String refusedIp;
    private boolean usoBaseTemporal;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.os.Bundle args = InstrumentationRegistry.getArguments();
        printerIp = args.getString("printerIp", "192.168.0.20");
        unreachableIp = args.getString("unreachableIp", "192.168.0.250");
        refusedIp = args.getString("refusedIp", "192.168.0.30");
        previousTenant = AppDatabase.getActiveTenantId(context);
        AppDatabase.setActiveTenantId(context, BENCH_TENANT);
    }

    @After
    public void tearDown() {
        if (usoBaseTemporal) {
            // Solo si el test usó la base del negocio temporal: pedirla de nuevo la crearía (y su seeder la rellenaría).
            AppDatabase.getDatabaseForTenant(context, BENCH_TENANT).close();
            context.deleteDatabase("softmyc_" + BENCH_TENANT + ".db");
        }
        AppDatabase.setActiveTenantId(context, previousTenant);
    }

    /** Sin imprimir: coste de generar el texto del ticket y de preparar el logotipo (mapa de bits ESC/POS). */
    @Test
    public void cpu_generacionDelTicketYDelLogotipo() {
        List<PedidoItem> lista = nuevaLinea();
        List<Double> ticket = new ArrayList<>();
        String texto = null;
        for (int i = 0; i < 40; i++) {
            long t0 = System.nanoTime();
            texto = TicketGenerator.generarTicket(context, lista, "Bench", new HashMap<>(), "Efectivo").texto;
            if (i >= 10) ticket.add((System.nanoTime() - t0) / 1e6);
        }
        List<Double> logo = new ArrayList<>();
        int bytesLogo = 0;
        for (int i = 0; i < 30; i++) {
            long t0 = System.nanoTime();
            byte[] b = PrinterHelper.getLogoEscPosBytes(context);
            if (i >= 10) logo.add((System.nanoTime() - t0) / 1e6);
            bytesLogo = b.length;
        }
        informe("generarTicket (3 líneas)", ticket);
        informe("getLogoEscPosBytes (200x185 px)", logo);
        Log.i(TAG, "tamaño: ticket=" + texto.length() + " caracteres, logotipo=" + bytesLogo + " bytes");
    }

    /** Sin imprimir: coste de abrir las preferencias cifradas (EncryptedSharedPreferences), que se abren en cada cobro. */
    @Test
    public void cpu_preferenciasCifradas() {
        List<Double> printer = new ArrayList<>();
        List<Double> secure = new ArrayList<>();
        List<Double> settings = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            long t0 = System.nanoTime();
            com.softmyc.tpv.data.SecurePreferencesHelper.getPrinterPrefs(context).getInt("tamanio_letra", 0);
            double a = (System.nanoTime() - t0) / 1e6;
            long t1 = System.nanoTime();
            TicketGenerator.getSecurePrefs(context).getInt("x", 0);
            double b = (System.nanoTime() - t1) / 1e6;
            long t2 = System.nanoTime();
            TicketGenerator.getSettingsPrefs(context).getInt("x", 0);
            double c = (System.nanoTime() - t2) / 1e6;
            if (i >= 5) { printer.add(a); secure.add(b); settings.add(c); }
        }
        informe("abrir getPrinterPrefs + leer una clave", printer);
        informe("abrir TicketGenerator.getSecurePrefs (contador) + leer", secure);
        informe("abrir TicketGenerator.getSettingsPrefs (ajustes de ticket) + leer", settings);
    }

    /** MC-02 completo: pulsar cobrar hasta que el ticket se ha enviado a la impresora, con guardado y envío por separado. */
    @Test
    public void mc02_cobroCompletoConImpresoraReal() throws Exception {
        List<Double> guardado = new ArrayList<>();
        List<Double> impreso = new ArrayList<>();
        List<Double> envio = new ArrayList<>();
        for (int i = 0; i < WARMUP + COBROS; i++) {
            Resultado r = cobrar(printerIp);
            assertTrue("Fallo de impresión en el cobro " + i + ": " + r.error, r.impreso);
            if (i >= WARMUP) {
                guardado.add(r.guardadoMs);
                impreso.add(r.finMs);
                envio.add(r.finMs - r.guardadoMs);
            }
        }
        informe("cobro: hasta venta guardada", guardado);
        informe("cobro: envío del ticket a la impresora (conexión + datos)", envio);
        informe("cobro: total hasta ticket enviado", impreso);
    }

    /** Impresora que no responde (IP inexistente) y puerto cerrado: tiempo hasta el aviso y venta guardada igualmente. */
    @Test
    public void fallosDeImpresion_ipInexistenteYPuertoCerrado() throws Exception {
        for (String[] caso : new String[][]{{"IP sin respuesta", unreachableIp}, {"puerto cerrado", refusedIp}}) {
            List<Double> aviso = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Resultado r = cobrar(caso[1]);
                assertTrue("Debía fallar la impresión (" + caso[0] + ")", !r.impreso);
                assertTrue("La venta debe guardarse antes del fallo", r.guardadoMs > 0 && r.guardadoMs <= r.finMs);
                aviso.add(r.finMs);
            }
            informe("fallo (" + caso[0] + " " + caso[1] + "): tiempo hasta el aviso", aviso);
        }
        int ventas = AppDatabase.getDatabaseForTenant(context, BENCH_TENANT).ventaDao().getAll().size();
        assertEquals("Las 6 ventas de los fallos deben estar guardadas", 6, ventas);
    }

    private static class Resultado {
        double guardadoMs; double finMs; boolean impreso; String error;
    }

    private Resultado cobrar(String ip) throws Exception {
        usoBaseTemporal = true;
        ExecutorService disco = Executors.newSingleThreadExecutor();
        CountDownLatch fin = new CountDownLatch(1);
        AtomicLong tGuardado = new AtomicLong();
        AtomicLong tFin = new AtomicLong();
        Resultado r = new Resultado();
        long t0 = System.nanoTime();
        ImpresionVentaFactory.crear(context).ejecutar(nuevaLinea(), "Bench", "Efectivo", "", ip,
                new HashMap<>(), null, disco, new ImprimirYGuardarVentaUseCase.Callback() {
                    @Override public void onImprimiendo() { }
                    @Override public void onGuardado() { tGuardado.set(System.nanoTime()); }
                    @Override public void onImpreso() { r.impreso = true; tFin.set(System.nanoTime()); fin.countDown(); }
                    @Override public void onError(String m) { r.error = m; tFin.set(System.nanoTime()); fin.countDown(); }
                });
        assertTrue("Sin respuesta en 15 s", fin.await(15, TimeUnit.SECONDS));
        disco.shutdown();
        r.guardadoMs = (tGuardado.get() - t0) / 1e6;
        r.finMs = (tFin.get() - t0) / 1e6;
        Thread.sleep(1500); // deja a la impresora cortar y recibir el siguiente ticket
        return r;
    }

    private List<PedidoItem> nuevaLinea() {
        List<PedidoItem> l = new ArrayList<>();
        l.add(new PedidoItem(new Producto(1, "Manzanas Golden Bio", 2.4, "Frutas", new ArrayList<>()), 1));
        l.add(new PedidoItem(new Producto(2, "Naranjas de Mesa Valencia", 1.9, "Frutas", new ArrayList<>(Arrays.asList("a"))), 2));
        l.add(new PedidoItem(new Producto(3, "Nueces del Pais con Cascara", 6.8, "Granel", new ArrayList<>(Collections.emptyList())), 1));
        return l;
    }

    private static void informe(String titulo, List<Double> ms) {
        List<Double> s = new ArrayList<>(ms);
        Collections.sort(s);
        double mediana = s.size() % 2 == 1 ? s.get(s.size() / 2) : (s.get(s.size() / 2 - 1) + s.get(s.size() / 2)) / 2;
        Log.i(TAG, String.format(java.util.Locale.US, "%s | n=%d min=%.2f mediana=%.2f max=%.2f ms",
                titulo, s.size(), s.get(0), mediana, s.get(s.size() - 1)));
    }
}
