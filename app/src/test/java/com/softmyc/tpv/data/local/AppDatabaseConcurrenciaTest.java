package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** TASK 2.2: abrir bases de varios negocios desde varios hilos a la vez no crea instancias duplicadas ni mezcla datos. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppDatabaseConcurrenciaTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final int HILOS = 12;
    private static final long TIMEOUT_S = 20;
    private static final String[] NEGOCIOS = {"concurrenciaa", "concurrenciab", "concurrenciac"};

    private final Context context = ApplicationProvider.getApplicationContext();

    @After
    public void limpiar() {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(context);
    }

    @Test
    public void abrir_la_misma_base_desde_muchos_hilos_devuelve_una_unica_instancia() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(HILOS);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<AppDatabase>> resultados = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            resultados.add(hilos.submit(() -> {
                salida.await();
                return AppDatabase.getDatabaseForTenant(context, NEGOCIOS[0]);
            }));
        }
        salida.countDown();

        AppDatabase primera = resultados.get(0).get(TIMEOUT_S, TimeUnit.SECONDS);
        for (Future<AppDatabase> r : resultados) {
            assertSame(primera, r.get(TIMEOUT_S, TimeUnit.SECONDS));
        }
        hilos.shutdownNow();
    }

    @Test
    public void escrituras_simultaneas_en_negocios_distintos_no_se_mezclan() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(HILOS);
        CountDownLatch salida = new CountDownLatch(1);
        int porNegocio = 4;
        List<Future<?>> tareas = new ArrayList<>();
        for (int n = 0; n < NEGOCIOS.length; n++) {
            final String negocio = NEGOCIOS[n];
            final int base = (n + 1) * 100;
            for (int k = 0; k < porNegocio; k++) {
                final int codigo = base + k;
                tareas.add(hilos.submit(() -> {
                    salida.await();
                    AppDatabase db = AppDatabase.getDatabaseForTenant(context, negocio);
                    db.productoDao().insertAll(java.util.Arrays.asList(
                            new ProductoLocal(codigo, "P" + codigo, 1.0, "cat", new ArrayList<>())));
                    return null;
                }));
            }
        }
        salida.countDown();
        for (Future<?> t : tareas) t.get(TIMEOUT_S, TimeUnit.SECONDS);

        ExecutorService lector = Executors.newSingleThreadExecutor();
        for (int n = 0; n < NEGOCIOS.length; n++) {
            final String negocio = NEGOCIOS[n];
            final int base = (n + 1) * 100;
            List<ProductoLocal> productos = lector.submit(
                    () -> AppDatabase.getDatabaseForTenant(context, negocio).productoDao().getAll())
                    .get(TIMEOUT_S, TimeUnit.SECONDS);
            assertEquals(porNegocio, productos.size());
            for (ProductoLocal p : productos) {
                assertTrue("el producto " + p.codigo + " no es del negocio " + negocio,
                        p.codigo >= base && p.codigo < base + porNegocio);
            }
        }
        assertNotSame(AppDatabase.getDatabaseForTenant(context, NEGOCIOS[0]),
                AppDatabase.getDatabaseForTenant(context, NEGOCIOS[1]));
        lector.shutdownNow();
        hilos.shutdownNow();
    }
}
