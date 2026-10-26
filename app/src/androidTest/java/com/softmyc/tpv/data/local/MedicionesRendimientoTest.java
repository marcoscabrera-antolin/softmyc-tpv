package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Mediciones en dispositivo real de MC-02 (latencia de guardado de un cobro) y MC-05 (consulta del historial).
 * Usan una base de datos propia y temporal: no tocan las bases de los negocios. Resultados por logcat (etiqueta MC_BENCH).
 * Ejecución: ver evidencias/medir_mc02_mc05.sh.
 */
@RunWith(AndroidJUnit4.class)
public class MedicionesRendimientoTest {
    private static final String TAG = "MC_BENCH";
    private static final String DB_NAME = "bench_mediciones.db";
    private static final int WARMUP = 5;
    private static final int MUESTRAS = 30;

    private Context context;
    private AppDatabase db;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DB_NAME);
        db = Room.databaseBuilder(context, AppDatabase.class, DB_NAME).addMigrations(AppDatabase.ALL_MIGRATIONS).build();
    }

    @After
    public void tearDown() {
        db.close();
        context.deleteDatabase(DB_NAME);
    }

    /** MC-02: serializar las líneas y guardar la venta descontando stock, en una transacción (lo que hace ejecutar() antes de imprimir). */
    @Test
    public void mc02_latenciaDeGuardadoDeUnCobro() {
        db.productoDao().insert(new ProductoLocal(1, "Manzanas", 2.4, "Frutas", new ArrayList<>(), "KG"));
        db.productoDao().insert(new ProductoLocal(2, "Naranjas", 1.9, "Frutas", new ArrayList<>(), "KG"));
        db.productoDao().insert(new ProductoLocal(3, "Nueces", 6.8, "Granel", new ArrayList<>(), "KG"));
        db.getOpenHelper().getWritableDatabase().execSQL("UPDATE productos SET stock = 1000000");

        List<Double> ms = new ArrayList<>();
        for (int i = 0; i < WARMUP + MUESTRAS; i++) {
            List<PedidoItem> lista = nuevaLinea();
            long t0 = System.nanoTime();
            String items = SyncManager.serializarItems(lista, new HashMap<>());
            VentaStockRegistrar.registrar(db, new VentaLocal(System.currentTimeMillis(), 10.0, items, false, "Efectivo"), lista, true);
            double dt = (System.nanoTime() - t0) / 1e6;
            if (i >= WARMUP) ms.add(dt);
        }
        informe("MC02 guardado de cobro (3 líneas, stock controlado)", ms);
        assertEquals(WARMUP + MUESTRAS, db.ventaDao().getAll().size());
    }

    /** MC-05: consulta del historial con 50, 500 y 5000 ventas. */
    @Test
    public void mc05_consultaDelHistorial() {
        int insertadas = 0;
        for (int objetivo : new int[]{50, 500, 5000}) {
            insertarVentas(objetivo - insertadas);
            insertadas = objetivo;
            List<Double> getAll = new ArrayList<>();
            List<Double> limit50 = new ArrayList<>();
            for (int i = 0; i < WARMUP + MUESTRAS; i++) {
                long t0 = System.nanoTime();
                List<VentaLocal> todas = db.ventaDao().getAll();
                double a = (System.nanoTime() - t0) / 1e6;
                assertEquals(objetivo, todas.size());

                long t1 = System.nanoTime();
                int filas = 0;
                try (android.database.Cursor c = db.getOpenHelper().getReadableDatabase()
                        .query("SELECT * FROM ventas ORDER BY fecha DESC LIMIT 50")) {
                    while (c.moveToNext()) filas++;
                }
                double b = (System.nanoTime() - t1) / 1e6;
                assertTrue(filas == 50);
                if (i >= WARMUP) { getAll.add(a); limit50.add(b); }
            }
            informe("MC05 getAll() con " + objetivo + " ventas (lo que usa el historial)", getAll);
            informe("MC05 SELECT ... LIMIT 50 con " + objetivo + " ventas (consulta de referencia)", limit50);
        }
    }

    /** MC-05 (mejora): periodo de ~100 ventas (un día de un negocio activo), filtro en memoria (antes) contra SQL (después). */
    @Test
    public void mc05_periodoDeUnDia_filtroEnMemoriaContraSql() {
        int insertadas = 0;
        for (int objetivo : new int[]{500, 5000, 20000}) {
            insertarVentas(objetivo - insertadas);
            insertadas = objetivo;
            long inicio;
            try (android.database.Cursor c = db.getOpenHelper().getReadableDatabase()
                    .query("SELECT fecha FROM ventas ORDER BY fecha DESC LIMIT 1 OFFSET 99")) {
                c.moveToFirst();
                inicio = c.getLong(0);
            }
            List<Double> antes = new ArrayList<>();
            List<Double> despues = new ArrayList<>();
            for (int i = 0; i < WARMUP + MUESTRAS; i++) {
                long t0 = System.nanoTime();
                List<VentaLocal> r1 = new ArrayList<>();
                for (VentaLocal v : db.ventaDao().getAll()) if (v.fecha >= inicio) r1.add(v);
                double a1 = (System.nanoTime() - t0) / 1e6;

                long t1 = System.nanoTime();
                List<VentaLocal> r2 = db.ventaDao().getDesde(inicio);
                double b1 = (System.nanoTime() - t1) / 1e6;

                assertEquals(100, r1.size());
                assertEquals(100, r2.size());
                if (i >= WARMUP) { antes.add(a1); despues.add(b1); }
            }
            informe("MC05 periodo de 100 ventas con " + objetivo + " en total, ANTES (getAll + filtro en memoria)", antes);
            informe("MC05 periodo de 100 ventas con " + objetivo + " en total, DESPUES (getDesde, SQL con índice)", despues);
        }
    }

    private void insertarVentas(int n) {
        String items = "[{\"nombre\":\"Manzanas\",\"cantidad\":1,\"precio_unitario\":2.4,\"categoria\":\"Frutas\",\"importe_linea\":2.4,"
                + "\"extras\":[],\"quitados\":[],\"salsas\":[]},{\"nombre\":\"Naranjas\",\"cantidad\":2,\"precio_unitario\":1.9,"
                + "\"categoria\":\"Frutas\",\"importe_linea\":3.8,\"extras\":[],\"quitados\":[],\"salsas\":[]}]";
        long base = System.currentTimeMillis() - 86_400_000L * 365 + db.ventaDao().getAll().size() * 60_000L;
        db.runInTransaction(() -> {
            for (int i = 0; i < n; i++) {
                db.ventaDao().insert(new VentaLocal(base + i * 60_000L, 6.2, items, i % 2 == 0, i % 3 == 0 ? "Tarjeta" : "Efectivo"));
            }
        });
    }

    private List<PedidoItem> nuevaLinea() {
        List<PedidoItem> l = new ArrayList<>();
        l.add(new PedidoItem(new Producto(1, "Manzanas", 2.4, "Frutas", new ArrayList<>(Collections.emptyList())), 1));
        l.add(new PedidoItem(new Producto(2, "Naranjas", 1.9, "Frutas", new ArrayList<>(Arrays.asList("a"))), 2));
        l.add(new PedidoItem(new Producto(3, "Nueces", 6.8, "Granel", new ArrayList<>()), 1));
        return l;
    }

    private static void informe(String titulo, List<Double> ms) {
        List<Double> s = new ArrayList<>(ms);
        Collections.sort(s);
        double mediana = s.size() % 2 == 1 ? s.get(s.size() / 2) : (s.get(s.size() / 2 - 1) + s.get(s.size() / 2)) / 2;
        double p95 = s.get((int) Math.ceil(s.size() * 0.95) - 1);
        Log.i(TAG, String.format(java.util.Locale.US, "%s | n=%d min=%.2f mediana=%.2f p95=%.2f max=%.2f ms",
                titulo, s.size(), s.get(0), mediana, p95, s.get(s.size() - 1)));
    }
}
