package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EdicionConservaDatosTest {

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

    @Test
    public void fechaParaEdicion_conservaLaFechaOriginal() {
        VentaLocal existente = new VentaLocal(1_000L, 10.0, "[]", true, "Efectivo");

        assertEquals(1_000L, VentaLocal.fechaParaEdicion(existente, 9_999L));
    }

    @Test
    public void fechaParaEdicion_usaAhoraSiNoHayVentaPrevia() {
        assertEquals(9_999L, VentaLocal.fechaParaEdicion(null, 9_999L));
    }

    @Test
    public void fechaParaEdicion_usaAhoraSiLaFechaGuardadaEsCero() {
        VentaLocal existente = new VentaLocal(0L, 10.0, "[]", true, "Efectivo");

        assertEquals(9_999L, VentaLocal.fechaParaEdicion(existente, 9_999L));
    }

    @Test
    public void actualizarDatosCatalogo_noTocaStockNiUnidad() {
        ProductoLocal existente = new ProductoLocal(7, "Manzanas", 2.0, "Frutería", Collections.emptyList(), "KG");
        existente.stock = 12;
        db.productoDao().insert(existente);

        int filas = db.productoDao().actualizarDatosCatalogo(7, "Manzanas rojas", 2.5, "Frutería", Arrays.asList("a"));

        ProductoLocal editado = db.productoDao().getByCodigo(7);
        assertEquals(1, filas);
        assertEquals(2.5, editado.precio, 0.0);
        assertEquals("Manzanas rojas", editado.nombre);
        assertEquals(12, editado.stock);
        assertEquals("KG", editado.unidadMedida);
    }

    @Test
    public void actualizarDatosCatalogo_devuelveCeroSiElProductoNoExiste() {
        assertEquals(0, db.productoDao().actualizarDatosCatalogo(99, "X", 1.0, "C", Collections.emptyList()));
    }

    @Test
    public void actualizarDatosCatalogo_respetaProductoSinControlDeStock() {
        db.productoDao().insert(new ProductoLocal(8, "Pera", 1.0, "Frutería", Collections.emptyList()));

        db.productoDao().actualizarDatosCatalogo(8, "Pera", 1.2, "Frutería", Collections.emptyList());

        assertEquals(-1, db.productoDao().getByCodigo(8).stock);
    }
}
