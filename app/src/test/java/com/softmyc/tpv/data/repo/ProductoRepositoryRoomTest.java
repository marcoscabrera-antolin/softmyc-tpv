package com.softmyc.tpv.data.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ProductoRepositoryRoomTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Código fuera del catálogo sembrado para que el sembrador en segundo plano no interfiera. */
    private static final int CODIGO = 91_001;

    private final Context context = ApplicationProvider.getApplicationContext();
    private final ExecutorService hiloDb = Executors.newSingleThreadExecutor();
    private ProductoRepositoryRoom repo;

    /** Room no admite acceso desde el hilo principal de Robolectric: las pruebas usan un hilo de disco. */
    private <T> T enDisco(Callable<T> tarea) throws Exception {
        return hiloDb.submit(tarea).get(10, TimeUnit.SECONDS);
    }

    @Before
    public void setUp() throws Exception {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putString("restaurant_id", "lanube").commit();
        repo = new ProductoRepositoryRoom(context);
        enDisco(() -> {
            AppDatabase.getDatabase(context).productoDao().deleteAll();
            return null;
        });
    }

    @After
    public void tearDown() throws Exception {
        enDisco(() -> {
            AppDatabase.getDatabase(context).productoDao().deleteAll();
            return null;
        });
        hiloDb.shutdown();
        SecurePreferencesHelper.getPrinterPrefs(context).edit().remove("restaurant_id").commit();
    }

    private static ProductoCatalogo producto(int codigo, String nombre, int stock) {
        return new ProductoCatalogo(codigo, nombre, 4.5, "Bebidas", Collections.singletonList("Hielo"), "UD", stock);
    }

    @Test
    public void guardar_yBuscarPorCodigo_devuelveLosMismosDatos() throws Exception {
        enDisco(() -> {
            repo.guardar(producto(CODIGO, "Agua", 12));
            return null;
        });

        ProductoCatalogo leido = enDisco(() -> repo.buscarPorCodigo(CODIGO));

        assertEquals("Agua", leido.nombre);
        assertEquals(4.5, leido.precio, 0.001);
        assertEquals("Bebidas", leido.categoria);
        assertEquals(List.of("Hielo"), leido.ingredientes);
        assertEquals("UD", leido.unidadMedida);
        assertEquals(12, leido.stock);
    }

    @Test
    public void buscarPorCodigo_inexistente_devuelveNull() throws Exception {
        assertNull(enDisco(() -> repo.buscarPorCodigo(CODIGO)));
    }

    @Test
    public void actualizarStock_cambiaSoloElStock() throws Exception {
        enDisco(() -> {
            repo.guardar(producto(CODIGO, "Agua", 12));
            repo.actualizarStock(CODIGO, 3);
            return null;
        });

        ProductoCatalogo leido = enDisco(() -> repo.buscarPorCodigo(CODIGO));

        assertEquals(3, leido.stock);
        assertEquals("Agua", leido.nombre);
    }

    @Test
    public void actualizarDatos_productoInexistente_devuelveCeroFilas() throws Exception {
        int filas = enDisco(() -> repo.actualizarDatos(producto(CODIGO, "Fantasma", -1)));

        assertEquals(0, filas);
    }

    @Test
    public void actualizarDatos_productoExistente_devuelveUnaFilaYCambiaElNombre() throws Exception {
        int filas = enDisco(() -> {
            repo.guardar(producto(CODIGO, "Agua", -1));
            return repo.actualizarDatos(producto(CODIGO, "Agua mineral", -1));
        });

        assertEquals(1, filas);
        assertEquals("Agua mineral", enDisco(() -> repo.buscarPorCodigo(CODIGO)).nombre);
    }

    @Test
    public void eliminar_quitaElProducto() throws Exception {
        enDisco(() -> {
            repo.guardar(producto(CODIGO, "Agua", -1));
            repo.eliminar(CODIGO);
            return null;
        });

        assertNull(enDisco(() -> repo.buscarPorCodigo(CODIGO)));
    }

    @Test
    public void listarTodos_ordenaPorNombre() throws Exception {
        List<String> nombres = enDisco(() -> {
            repo.guardar(producto(CODIGO, "Zumo", -1));
            repo.guardar(producto(CODIGO + 1, "Agua", -1));
            List<String> resultado = new ArrayList<>();
            for (ProductoCatalogo p : repo.listarTodos()) {
                resultado.add(p.nombre);
            }
            return resultado;
        });

        assertTrue(nombres.indexOf("Agua") < nombres.indexOf("Zumo"));
    }
}
