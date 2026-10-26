package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.data.InMemorySharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CatalogOutboxTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private InMemorySharedPreferences prefs;
    private CatalogOutbox outbox;

    @Before
    public void setUp() {
        prefs = new InMemorySharedPreferences();
        outbox = new CatalogOutbox(prefs);
    }

    @Test
    public void colaNuevaEstaVacia() {
        assertTrue(outbox.isEmpty("fruteria"));
    }

    @Test
    public void add_dejaUnaSolaOperacionPorProductoLaUltima() {
        outbox.add("fruteria", CatalogOutbox.UPSERT, 5);
        outbox.add("fruteria", CatalogOutbox.DELETE, 5);

        assertEquals(1, outbox.pending("fruteria").size());
        assertEquals(CatalogOutbox.DELETE, outbox.pending("fruteria").get(0).tipo);
    }

    @Test
    public void remove_quitaSoloLaOperacionEnviada() {
        outbox.add("fruteria", CatalogOutbox.UPSERT, 5);
        outbox.add("fruteria", CatalogOutbox.UPSERT, 6);
        CatalogOutbox.Op enviada = outbox.pending("fruteria").get(0);

        outbox.remove("fruteria", enviada);

        assertEquals(1, outbox.pending("fruteria").size());
        assertEquals(6, outbox.pending("fruteria").get(0).codigo);
    }

    @Test
    public void remove_noQuitaUnaReedicionDelMismoProductoMientrasSeEnviaba() {
        outbox.add("fruteria", CatalogOutbox.UPSERT, 5);
        CatalogOutbox.Op enviada = outbox.pending("fruteria").get(0);
        // El usuario vuelve a editar el 5 mientras la primera edición está en vuelo.
        outbox.add("fruteria", CatalogOutbox.UPSERT, 5);

        outbox.remove("fruteria", enviada);

        assertFalse("la segunda edición debe seguir pendiente", outbox.isEmpty("fruteria"));
    }

    @Test
    public void remove_noQuitaUnBorradoPosteriorDelMismoProducto() {
        outbox.add("fruteria", CatalogOutbox.UPSERT, 5);
        CatalogOutbox.Op enviada = outbox.pending("fruteria").get(0);
        outbox.add("fruteria", CatalogOutbox.DELETE, 5);

        outbox.remove("fruteria", enviada);

        assertEquals(CatalogOutbox.DELETE, outbox.pending("fruteria").get(0).tipo);
    }

    @Test
    public void lasColasSonPorNegocio() {
        outbox.add("fruteria", CatalogOutbox.UPSERT, 5);

        assertTrue(outbox.isEmpty("taller"));
    }

    @Test
    public void colaIlegibleSeTrataComoVacia() {
        prefs.edit().putString("catalog_outbox_fruteria", "no es json").apply();

        assertTrue(new CatalogOutbox(prefs).isEmpty("fruteria"));
    }

    @Test
    public void addDesdeVariosHilosYVariasInstanciasNoPierdeOperaciones() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> tareas = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            final int codigo = i;
            tareas.add(pool.submit(() -> new CatalogOutbox(prefs).add("fruteria", CatalogOutbox.UPSERT, codigo)));
        }
        for (Future<?> t : tareas) t.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(40, outbox.pending("fruteria").size());
    }

    @Test
    public void esRechazoDefinitivo_distingueErroresDefinitivosDeLosReintentables() {
        assertTrue(CatalogOutboxSender.esRechazoDefinitivo(400));
        assertTrue(CatalogOutboxSender.esRechazoDefinitivo(403));
        assertTrue(CatalogOutboxSender.esRechazoDefinitivo(422));
        assertFalse(CatalogOutboxSender.esRechazoDefinitivo(401));
        assertFalse(CatalogOutboxSender.esRechazoDefinitivo(408));
        assertFalse(CatalogOutboxSender.esRechazoDefinitivo(429));
        assertFalse(CatalogOutboxSender.esRechazoDefinitivo(500));
        assertFalse(CatalogOutboxSender.esRechazoDefinitivo(503));
        assertFalse(CatalogOutboxSender.esRechazoDefinitivo(200));
    }
}
