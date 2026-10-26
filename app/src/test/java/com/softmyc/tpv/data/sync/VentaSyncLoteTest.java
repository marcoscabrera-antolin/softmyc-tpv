package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.VentaLocal;

import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class VentaSyncLoteTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private AppDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
    }

    @After
    public void tearDown() {
        db.close();
    }

    private static List<VentaLocal> ventas(int n) {
        List<VentaLocal> lista = new ArrayList<>();
        for (int i = 0; i < n; i++) lista.add(new VentaLocal(1_000L + i, 5.0, "[]", false, "Efectivo"));
        return lista;
    }

    @Test
    public void trocear_divideEnLotesDeTamanoMaximo() {
        List<List<VentaLocal>> lotes = VentaSyncLote.trocear(ventas(120), VentaSyncLote.TAMANO_MAXIMO);

        assertEquals(3, lotes.size());
        assertEquals(50, lotes.get(0).size());
        assertEquals(50, lotes.get(1).size());
        assertEquals(20, lotes.get(2).size());
    }

    @Test
    public void trocear_sinVentasNoDevuelveLotes() {
        assertTrue(VentaSyncLote.trocear(new ArrayList<>(), 50).isEmpty());
    }

    @Test
    public void aJson_incluyeUuidFechaYMetodoPorDefecto() throws Exception {
        VentaLocal v = new VentaLocal(1_234L, 7.5, "[{\"nombre\":\"A\"}]", false, null);
        v.id = 3;

        JSONArray json = VentaSyncLote.aJson(java.util.Collections.singletonList(v));

        assertEquals(3, json.getJSONObject(0).getInt("localId"));
        assertEquals(v.uuid, json.getJSONObject(0).getString("uuid"));
        assertEquals("EFECTIVO", json.getJSONObject(0).getString("metodoPago"));
        assertEquals(1, json.getJSONObject(0).getJSONArray("items").length());
    }

    @Test
    public void interpretar_distingueConfirmadasYRechazadas() {
        String cuerpo = "{\"success\":true,\"syncedIds\":[1,2],\"rejected\":[{\"localId\":3,\"index\":2,\"reason\":\"total no cuadra\"}]}";

        VentaSyncLote.Resultado r = VentaSyncLote.interpretar(200, cuerpo);

        assertNull(r.error);
        assertTrue(r.confirmadas.contains(1) && r.confirmadas.contains(2));
        assertEquals(1, r.rechazadas.size());
        assertEquals(3, r.rechazadas.get(0).localId);
        assertEquals("total no cuadra", r.rechazadas.get(0).motivo);
    }

    @Test
    public void interpretar_unCodigoNo2xxEsError() {
        assertNotNull(VentaSyncLote.interpretar(413, "").error);
        assertNotNull(VentaSyncLote.interpretar(500, "{}").error);
    }

    @Test
    public void interpretar_respuestaNoJsonOSinSuccessEsError() {
        assertNotNull(VentaSyncLote.interpretar(200, "<html>").error);
        assertNotNull(VentaSyncLote.interpretar(200, "{\"success\":false}").error);
    }

    @Test
    public void markAsSyncedSiNoCambio_marcaLaVersionEnviada() {
        VentaLocal v = new VentaLocal(1_000L, 5.0, "[]", false, "Efectivo");
        db.ventaDao().insert(v);
        VentaLocal guardada = db.ventaDao().getUnsynced().get(0);

        int filas = db.ventaDao().markAsSyncedSiNoCambio(guardada.id, 5.0, "[]", "Efectivo");

        assertEquals(1, filas);
        assertTrue(db.ventaDao().getUnsynced().isEmpty());
    }

    @Test
    public void markAsSyncedSiNoCambio_noMarcaUnaVentaEditadaMientrasSeEnviaba() {
        db.ventaDao().insert(new VentaLocal(1_000L, 5.0, "[]", false, "Efectivo"));
        VentaLocal enviada = db.ventaDao().getUnsynced().get(0);
        VentaLocal editada = db.ventaDao().getById(enviada.id);
        editada.total = 9.0;
        db.ventaDao().update(editada);

        int filas = db.ventaDao().markAsSyncedSiNoCambio(enviada.id, 5.0, "[]", "Efectivo");

        assertEquals(0, filas);
        assertFalse(db.ventaDao().getUnsynced().isEmpty());
    }

    @Test
    public void markAsSyncedSiNoCambio_funcionaConMetodoDePagoNulo() {
        db.ventaDao().insert(new VentaLocal(1_000L, 5.0, "[]", false, null));
        VentaLocal enviada = db.ventaDao().getUnsynced().get(0);

        assertEquals(1, db.ventaDao().markAsSyncedSiNoCambio(enviada.id, 5.0, "[]", null));
    }

    @Test
    public void interpretar_401y403sonFalloDeCredenciales_otrosErroresNo() {
        assertTrue(VentaSyncLote.interpretar(401, "").esFalloDeCredenciales());
        assertTrue(VentaSyncLote.interpretar(403, "").esFalloDeCredenciales());
        assertFalse(VentaSyncLote.interpretar(500, "").esFalloDeCredenciales());
        assertFalse(VentaSyncLote.interpretar(400, "").esFalloDeCredenciales());
    }
}
