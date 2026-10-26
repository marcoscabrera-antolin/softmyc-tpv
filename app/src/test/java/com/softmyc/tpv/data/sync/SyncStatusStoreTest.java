package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SyncStatusStoreTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private SharedPreferences prefs;
    private SyncStatusStore store;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences("sync_status_test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        store = new SyncStatusStore(prefs);
    }

    @Test
    public void estadoInicialNoTieneMotivoNiExito() {
        SyncStatus estado = store.leer();

        assertEquals(SyncOmision.NINGUNA, estado.getOmision());
        assertEquals(0, estado.getRechazadasUltimoIntento());
        assertEquals(0L, estado.getUltimoExitoMs());
    }

    @Test
    public void elUltimoMotivoSobrevivePorqueSeLeeDesdeLasPreferencias() {
        store.registrarOmision(SyncOmision.SIN_RED);

        assertEquals(SyncOmision.SIN_RED, new SyncStatusStore(prefs).leer().getOmision());
    }

    @Test
    public void iniciarIntentoLimpiaMotivoYRechazadasPeroConservaElUltimoExito() {
        store.registrarLoteAceptado(2, 5_000L);
        store.registrarOmision(SyncOmision.SERVIDOR_ERROR);

        store.iniciarIntento();

        SyncStatus estado = store.leer();
        assertEquals(SyncOmision.NINGUNA, estado.getOmision());
        assertEquals(0, estado.getRechazadasUltimoIntento());
        assertEquals(5_000L, estado.getUltimoExitoMs());
    }

    @Test
    public void loteAceptadoAcumulaRechazadasDelIntentoYActualizaElExito() {
        store.iniciarIntento();
        store.registrarLoteAceptado(1, 1_000L);
        store.registrarLoteAceptado(2, 2_000L);

        SyncStatus estado = store.leer();
        assertEquals(3, estado.getRechazadasUltimoIntento());
        assertEquals(2_000L, estado.getUltimoExitoMs());
    }
}
