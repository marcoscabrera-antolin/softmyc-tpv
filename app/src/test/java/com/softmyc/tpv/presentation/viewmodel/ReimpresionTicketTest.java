package com.softmyc.tpv.presentation.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.port.AjustesImpresora;
import com.softmyc.tpv.domain.port.TicketComposer;
import com.softmyc.tpv.domain.repository.VentaRepository;
import com.softmyc.tpv.domain.repository.VentaResumen;
import com.softmyc.tpv.util.FalloImpresion;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class ReimpresionTicketTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }
    private static final int TICKET = 42;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<String> toast = new MutableLiveData<>();
    private final MutableLiveData<FalloImpresion> fallo = new MutableLiveData<>();
    private final List<String> impresos = new ArrayList<>();
    private final List<String> ipsUsadas = new ArrayList<>();
    private final List<AjustesImpresora> ajustesUsados = new ArrayList<>();
    private Context contexto;
    private VentaResumen ventaGuardada;
    private boolean impresionOk;
    private boolean composicionFalla;
    private boolean deserializacionFalla;
    private boolean consultaVentaFalla;

    @Before
    public void setUp() {
        contexto = ApplicationProvider.getApplicationContext();
        ventaGuardada = new VentaResumen(TICKET, 0L, 10.0, "Efectivo", "json", true);
        impresionOk = true;
        composicionFalla = false;
        deserializacionFalla = false;
        consultaVentaFalla = false;
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }

    private ReimpresionTicket crear() {
        VentaRepository ventas = new VentaRepository() {
            @Override public List<VentaResumen> getDesde(long inicioMs) { return Collections.emptyList(); }
            @Override public List<VentaResumen> getRecientes(int limite) { return Collections.emptyList(); }
            @Override public VentaResumen getById(int id) {
                if (consultaVentaFalla) throw new IllegalStateException("base cerrada");
                return id == TICKET ? ventaGuardada : null;
            }
        };
        TicketComposer compositor = (lineas, tienda, precios, metodo, numero) -> {
            if (composicionFalla) throw new IllegalStateException("sin plantilla");
            return new TicketComposer.Composicion("ticket " + numero + " " + metodo + " " + lineas.size(), 10.0);
        };
        MutableLiveData<List<Producto>> productos = new MutableLiveData<>(Collections.emptyList());
        MutableLiveData<Map<String, Double>> precios = new MutableLiveData<>(Collections.emptyMap());
        LiveData<String> ip = new MutableLiveData<>("192.168.0.50");
        return new ReimpresionTicket(executor, ventas,
                (json, catalogo) -> {
                    if (deserializacionFalla) throw new IllegalArgumentException("json ilegible");
                    return new ArrayList<PedidoItem>();
                },
                compositor,
                (ipImpresora, ajustes, texto, resultado) -> {
                    ipsUsadas.add(ipImpresora);
                    ajustesUsados.add(ajustes);
                    impresos.add(texto);
                    resultado.onResultado(impresionOk, impresionOk ? null : "sin papel");
                },
                () -> AjustesImpresora.of(2, 9101),
                toast, fallo, productos, precios, ip);
    }

    private void esperarTarea() throws Exception {
        executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void reimprimir_ventaExistente_imprimeConIpYAjustesGuardadosYAvisa() throws Exception {
        crear().reimprimir(contexto, "Tienda", TICKET);
        esperarTarea();

        assertEquals(Collections.singletonList("ticket 42 Efectivo 0"), impresos);
        assertEquals("192.168.0.50", ipsUsadas.get(0));
        assertEquals(9101, ajustesUsados.get(0).puerto);
        assertEquals(2, ajustesUsados.get(0).fontStyle);
        assertTrue(toast.getValue().contains("42"));
        assertNull(fallo.getValue());
    }

    @Test
    public void reimprimir_ventaInexistente_avisaYNoImprime() throws Exception {
        crear().reimprimir(contexto, "Tienda", 999);
        esperarTarea();

        assertTrue(impresos.isEmpty());
        assertTrue(toast.getValue().contains("999"));
        assertNull(fallo.getValue());
    }

    @Test
    public void reimprimir_impresoraFalla_publicaElFalloConElMotivo() throws Exception {
        impresionOk = false;

        crear().reimprimir(contexto, "Tienda", TICKET);
        esperarTarea();

        FalloImpresion f = fallo.getValue();
        assertNotNull(f);
        assertEquals(TICKET, f.getTicketId());
        assertEquals("sin papel", f.getDetalle());
        assertNull(toast.getValue());
    }

    @Test
    public void reimprimir_noSePuedeComponerElTicket_publicaFalloSinImprimir() throws Exception {
        composicionFalla = true;

        crear().reimprimir(contexto, "Tienda", TICKET);
        esperarTarea();

        assertTrue(impresos.isEmpty());
        assertNotNull(fallo.getValue());
        assertEquals(TICKET, fallo.getValue().getTicketId());
    }

    @Test
    public void reimprimir_lasLineasGuardadasSonIlegibles_publicaFalloSinImprimir() throws Exception {
        deserializacionFalla = true;

        crear().reimprimir(contexto, "Tienda", TICKET);
        esperarTarea();

        assertTrue(impresos.isEmpty());
        assertNotNull(fallo.getValue());
    }

    @Test
    public void reimprimir_laConsultaDeLaVentaFalla_tratalaVentaComoInexistente() throws Exception {
        consultaVentaFalla = true;

        crear().reimprimir(contexto, "Tienda", TICKET);
        esperarTarea();

        assertTrue(impresos.isEmpty());
        assertTrue(toast.getValue().contains("42"));
    }
}
