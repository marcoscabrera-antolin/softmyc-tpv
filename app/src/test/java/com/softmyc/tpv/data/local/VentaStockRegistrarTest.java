package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class VentaStockRegistrarTest {

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

    private void saveProduct(int code, int stock) {
        ProductoLocal p = new ProductoLocal(code, "P" + code, 2.0, "Cat", new ArrayList<>());
        p.stock = stock;
        db.productoDao().insert(p);
    }

    private static PedidoItem item(int code, int qty) {
        return new PedidoItem(new Producto(code, "P" + code, 2.0, "Cat", Collections.emptyList()), qty);
    }

    private static VentaLocal venta() {
        return new VentaLocal(1L, 4.0, "[]", false, "Efectivo");
    }

    /** NEG-09: una línea al peso no descuenta unidades de stock, aunque su cantidad redondeada sea mayor. */
    @Test
    public void registrar_lineaAlPesoNoDescuentaStock() {
        saveProduct(5, 10);
        PedidoItem alPeso = item(5, 3);
        alPeso.setUnidadMedida("KG");
        alPeso.setCantidadMedida(0.300);

        VentaStockRegistrar.registrar(db, venta(), Arrays.asList(alPeso), true);

        assertEquals(10, db.productoDao().getByCodigo(5).stock);
    }

    @Test
    public void registrar_decrementsStockOfControlledProducts() {
        saveProduct(1, 10);

        VentaStockRegistrar.registrar(db, venta(), Arrays.asList(item(1, 3)), true);

        assertEquals(7, db.productoDao().getByCodigo(1).stock);
        assertEquals(1, db.ventaDao().getAll().size());
    }

    @Test
    public void registrar_neverGoesBelowZero() {
        saveProduct(2, 2);

        VentaStockRegistrar.registrar(db, venta(), Arrays.asList(item(2, 5)), true);

        assertEquals(0, db.productoDao().getByCodigo(2).stock);
    }

    @Test
    public void registrar_leavesUntrackedProductsAtMinusOne() {
        saveProduct(3, -1);

        VentaStockRegistrar.registrar(db, venta(), Arrays.asList(item(3, 4)), true);

        assertEquals(-1, db.productoDao().getByCodigo(3).stock);
    }

    @Test
    public void registrar_withoutStockDiscount_keepsStockUntouched() {
        saveProduct(4, 10);

        VentaStockRegistrar.registrar(db, venta(), Arrays.asList(item(4, 3)), false);

        assertEquals(10, db.productoDao().getByCodigo(4).stock);
        assertEquals(1, db.ventaDao().getAll().size());
    }

    @Test
    public void registrar_ignoresItemsOfUnknownProducts() {
        List<PedidoItem> lista = Arrays.asList(item(999, 1));

        VentaStockRegistrar.registrar(db, venta(), lista, true);

        assertEquals(1, db.ventaDao().getAll().size());
    }

    @Test
    public void registrar_sumsRepeatedLinesOfTheSameProduct() {
        saveProduct(5, 10);

        VentaStockRegistrar.registrar(db, venta(), Arrays.asList(item(5, 2), item(5, 3)), true);

        assertEquals(5, db.productoDao().getByCodigo(5).stock);
    }
}
