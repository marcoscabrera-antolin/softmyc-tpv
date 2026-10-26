package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class VentaDaoPeriodoTest {

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

    private long guardar(long fecha) {
        return db.ventaDao().insert(new VentaLocal(fecha, 1.0, "[]", false, "Efectivo"));
    }

    private static List<Long> fechas(List<VentaLocal> ventas) {
        List<Long> r = new ArrayList<>();
        for (VentaLocal v : ventas) r.add(v.fecha);
        return r;
    }

    @Test
    public void getDesde_returnsOnlySalesFromTheStartInstant_newestFirst() {
        guardar(100L);
        guardar(300L);
        guardar(200L);
        guardar(50L);

        List<VentaLocal> r = db.ventaDao().getDesde(200L);

        assertEquals(java.util.Arrays.asList(300L, 200L), fechas(r));
    }

    @Test
    public void getDesde_includesASaleAtExactlyTheStartInstant() {
        guardar(1000L);

        assertEquals(1, db.ventaDao().getDesde(1000L).size());
        assertEquals(0, db.ventaDao().getDesde(1001L).size());
    }

    @Test
    public void getDesde_onEmptyTableReturnsEmptyList() {
        assertTrue(db.ventaDao().getDesde(0L).isEmpty());
    }

    @Test
    public void getDesde_matchesTheInMemoryFilterOfTheOldImplementation() {
        Random rnd = new Random(42);
        for (int i = 0; i < 300; i++) guardar(rnd.nextInt(10_000));
        long inicio = 6_000L;

        List<Long> esperado = new ArrayList<>();
        for (VentaLocal v : db.ventaDao().getAll()) {
            if (v.fecha >= inicio) esperado.add(v.fecha);
        }

        assertEquals(esperado, fechas(db.ventaDao().getDesde(inicio)));
    }

    @Test
    public void getDesde_usesTheIndexOnFecha() {
        guardar(1L);
        StringBuilder plan = new StringBuilder();
        try (Cursor c = db.getOpenHelper().getReadableDatabase()
                .query("EXPLAIN QUERY PLAN SELECT * FROM ventas WHERE fecha >= 5 ORDER BY fecha DESC")) {
            while (c.moveToNext()) plan.append(c.getString(c.getColumnCount() - 1)).append(' ');
        }
        assertTrue("Plan sin índice: " + plan, plan.toString().contains("index_ventas_fecha"));
    }

    @Test
    public void getRecientes_returnsTheNewestSalesFirstUpToTheLimit() {
        for (long f : new long[]{10L, 50L, 30L, 20L, 40L, 60L, 5L}) guardar(f);

        assertEquals(java.util.Arrays.asList(60L, 50L, 40L), fechas(db.ventaDao().getRecientes(3)));
    }

    @Test
    public void getRecientes_withFewerSalesThanTheLimitReturnsAllOfThem() {
        guardar(7L);
        guardar(9L);

        assertEquals(java.util.Arrays.asList(9L, 7L), fechas(db.ventaDao().getRecientes(5)));
    }

    @Test
    public void getRecientes_onEmptyTableReturnsEmptyList() {
        assertTrue(db.ventaDao().getRecientes(5).isEmpty());
    }

    @Test
    public void getRecientes_matchesTheFirstFiveOfTheOldImplementation() {
        Random rnd = new Random(7);
        for (int i = 0; i < 200; i++) guardar(rnd.nextInt(100_000));

        List<Long> esperado = fechas(db.ventaDao().getAll()).subList(0, 5);

        assertEquals(esperado, fechas(db.ventaDao().getRecientes(5)));
    }

    @Test
    public void getRecientes_usesTheIndexOnFecha() {
        guardar(1L);
        StringBuilder plan = new StringBuilder();
        try (Cursor c = db.getOpenHelper().getReadableDatabase()
                .query("EXPLAIN QUERY PLAN SELECT * FROM ventas ORDER BY fecha DESC LIMIT 5")) {
            while (c.moveToNext()) plan.append(c.getString(c.getColumnCount() - 1)).append(' ');
        }
        assertTrue("Plan sin índice: " + plan, plan.toString().contains("index_ventas_fecha"));
    }
}
