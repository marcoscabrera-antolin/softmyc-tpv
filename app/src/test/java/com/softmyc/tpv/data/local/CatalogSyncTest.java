package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.softmyc.tpv.data.InMemorySharedPreferences;
import com.softmyc.tpv.data.sync.CatalogOutbox;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

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
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CatalogSyncTest {

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

    private static ProductoLocal product(int code, String name, double price) {
        return new ProductoLocal(code, name, price, "Cat", new ArrayList<>());
    }

    private void seed(ProductoLocal... products) {
        db.productoDao().insertAll(Arrays.asList(products));
    }

    @Test
    public void syncFromServer_keepsLocalProductWithPendingUpsertEvenIfServerDoesNotHaveIt() {
        seed(product(5, "Alta local", 3.0));
        Map<Integer, String> pendientes = Collections.singletonMap(5, CatalogOutbox.UPSERT);

        CatalogSync.syncFromServer(db, Arrays.asList(product(1, "Servidor", 1.0)),
                Collections.emptyList(), Collections.emptyList(), pendientes);

        assertNotNull(db.productoDao().getByCodigo(5));
        assertEquals("Alta local", db.productoDao().getByCodigo(5).nombre);
        assertNotNull(db.productoDao().getByCodigo(1));
    }

    @Test
    public void syncFromServer_keepsLocalVersionOfPendingUpsertInsteadOfServerVersion() {
        seed(product(2, "Editado aquí", 9.0));
        Map<Integer, String> pendientes = Collections.singletonMap(2, CatalogOutbox.UPSERT);

        CatalogSync.syncFromServer(db, Arrays.asList(product(2, "Del servidor", 1.0)),
                Collections.emptyList(), Collections.emptyList(), pendientes);

        assertEquals("Editado aquí", db.productoDao().getByCodigo(2).nombre);
    }

    @Test
    public void syncFromServer_doesNotResurrectProductDeletedLocallyWhilePending() {
        seed(product(3, "Ya borrado", 1.0));
        db.productoDao().delete(db.productoDao().getByCodigo(3));
        Map<Integer, String> pendientes = Collections.singletonMap(3, CatalogOutbox.DELETE);

        CatalogSync.syncFromServer(db, Arrays.asList(product(3, "Ya borrado", 1.0), product(4, "Otro", 2.0)),
                Collections.emptyList(), Collections.emptyList(), pendientes);

        assertNull(db.productoDao().getByCodigo(3));
        assertNotNull(db.productoDao().getByCodigo(4));
    }

    @Test
    public void pendientesDe_mapsEachCodeToItsLatestOperation() {
        CatalogOutbox outbox = new CatalogOutbox(new InMemorySharedPreferences());
        outbox.add("fruteria", CatalogOutbox.UPSERT, 1);
        outbox.add("fruteria", CatalogOutbox.DELETE, 2);

        Map<Integer, String> pendientes = CatalogSync.pendientesDe(outbox.pending("fruteria"));

        assertEquals(CatalogOutbox.UPSERT, pendientes.get(1));
        assertEquals(CatalogOutbox.DELETE, pendientes.get(2));
    }

    @Test
    public void syncFromServer_keepsLocalStockAndUnitOfExistingProducts() {
        ProductoLocal local = product(1, "Manzana", 2.0);
        local.stock = 7;
        local.unidadMedida = "KG";
        seed(local);

        boolean applied = CatalogSync.syncFromServer(db, Arrays.asList(product(1, "Manzana", 2.5)),
                Collections.emptyList(), Collections.emptyList());

        assertTrue(applied);
        ProductoLocal after = db.productoDao().getByCodigo(1);
        assertEquals(2.5, after.precio, 0.001);
        assertEquals(7, after.stock);
        assertEquals("KG", after.unidadMedida);
    }

    @Test
    public void syncFromServer_insertsNewProductsWithDefaults() {
        CatalogSync.syncFromServer(db, Arrays.asList(product(5, "Nuevo", 1.0)), Collections.emptyList(), Collections.emptyList());

        ProductoLocal created = db.productoDao().getByCodigo(5);
        assertNotNull(created);
        assertEquals(-1, created.stock);
        assertEquals("UD", created.unidadMedida);
    }

    @Test
    public void syncFromServer_removesProductsMissingFromServer() {
        seed(product(1, "A", 1.0), product(2, "B", 1.0));

        CatalogSync.syncFromServer(db, Arrays.asList(product(1, "A", 1.0)), Collections.emptyList(), Collections.emptyList());

        assertNull(db.productoDao().getByCodigo(2));
    }

    @Test
    public void syncFromServer_withEmptyProductList_leavesCatalogUntouched() {
        seed(product(1, "A", 1.0));

        boolean applied = CatalogSync.syncFromServer(db, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertFalse(applied);
        assertNotNull(db.productoDao().getByCodigo(1));
    }

    @Test
    public void replaceCatalog_withNoProducts_doesNotDeleteAnything() {
        seed(product(1, "A", 1.0));
        db.extraDao().insertAll(Arrays.asList(new ExtraLocal("Queso", 1.0)));

        boolean applied = CatalogSync.replaceCatalog(db, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertFalse(applied);
        assertEquals(1, db.productoDao().getAll().size());
        assertEquals(1, db.extraDao().getAll().size());
    }

    @Test
    public void replaceCatalog_keepsExistingExtrasAndSaucesWhenCsvHasNone() {
        seed(product(1, "A", 1.0));
        db.extraDao().insertAll(Arrays.asList(new ExtraLocal("Queso", 1.0)));
        db.salsaDao().insertAll(Arrays.asList(new SalsaLocal("Ketchup")));

        CatalogSync.replaceCatalog(db, Arrays.asList(product(2, "B", 2.0)), Collections.emptyList(), Collections.emptyList());

        assertEquals(1, db.extraDao().getAll().size());
        assertEquals(1, db.salsaDao().getAll().size());
        assertNull(db.productoDao().getByCodigo(1));
        assertNotNull(db.productoDao().getByCodigo(2));
    }

    @Test
    public void replaceCatalog_replacesExtrasAndSaucesWhenCsvHasThem() {
        db.extraDao().insertAll(Arrays.asList(new ExtraLocal("Queso", 1.0)));
        db.salsaDao().insertAll(Arrays.asList(new SalsaLocal("Ketchup")));

        CatalogSync.replaceCatalog(db, Arrays.asList(product(2, "B", 2.0)),
                Arrays.asList(new ExtraLocal("Bacon", 1.5)), Arrays.asList(new SalsaLocal("Mayonesa")));

        List<ExtraLocal> extras = db.extraDao().getAll();
        assertEquals(1, extras.size());
        assertEquals("Bacon", extras.get(0).nombre);
        assertEquals("Mayonesa", db.salsaDao().getAll().get(0).nombre);
    }

    @Test
    public void replaceCatalog_keepsStockOfProductsPresentInTheCsv() {
        ProductoLocal local = product(1, "A", 1.0);
        local.stock = 4;
        seed(local);

        CatalogSync.replaceCatalog(db, Arrays.asList(product(1, "A", 9.0)), Collections.emptyList(), Collections.emptyList());

        ProductoLocal after = db.productoDao().getByCodigo(1);
        assertEquals(9.0, after.precio, 0.001);
        assertEquals(4, after.stock);
    }
}
