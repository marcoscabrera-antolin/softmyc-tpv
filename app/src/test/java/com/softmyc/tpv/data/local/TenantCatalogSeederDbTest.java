package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.mockito.Mockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TenantCatalogSeederDbTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String DB_NAME = "seeder_test.db";
    private static final int FRUTERIA_PRODUCTS = 8;

    private Context context;
    private AppDatabase db;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB_NAME);
    }

    @After
    public void tearDown() {
        if (db != null) db.close();
        context.deleteDatabase(DB_NAME);
    }

    @Test
    public void seedTenantSync_seedsEmptyCatalogAndReportsSuccess() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();

        assertTrue(TenantCatalogSeeder.seedTenantSync(context, db, "fruteria"));

        assertEquals(FRUTERIA_PRODUCTS, db.productoDao().getAll().size());
    }

    @Test
    public void seedTenantSync_isIdempotentAndReportsSuccessWhenCatalogExists() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        TenantCatalogSeeder.seedTenantSync(context, db, "fruteria");

        assertTrue(TenantCatalogSeeder.seedTenantSync(context, db, "fruteria"));

        assertEquals(FRUTERIA_PRODUCTS, db.productoDao().getAll().size());
    }

    @Test
    public void seedTenantSync_concurrentCallsSeedOnlyOnce() throws Exception {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        final int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger inserts = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    if (TenantCatalogSeeder.seedTenantSync(context, db, "fruteria")) inserts.incrementAndGet();
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();

        assertEquals(threads, inserts.get());
        assertEquals(FRUTERIA_PRODUCTS, db.productoDao().getAll().size());
    }

    @Test
    public void seedTenantSync_returnsFalseWhenDatabaseCannotBeOpened() {
        // Base en la versión actual pero sin tablas: Room rechaza el esquema al abrirla.
        SQLiteDatabase broken = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DB_NAME), null);
        broken.setVersion(5);
        broken.close();
        db = Room.databaseBuilder(context, AppDatabase.class, DB_NAME).allowMainThreadQueries().build();

        assertFalse(TenantCatalogSeeder.seedTenantSync(context, db, "fruteria"));
    }

    @Test
    public void seedTenantSync_returnsFalseWithoutContextOrDatabase() {
        assertFalse(TenantCatalogSeeder.seedTenantSync(null, null, "fruteria"));
    }

    @Test
    public void seedTenantSync_unknownTenantStaysEmptyAndReportsFailure() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();

        assertFalse(TenantCatalogSeeder.seedTenantSync(context, db, "negocio_desconocido"));

        assertTrue(db.productoDao().getAll().isEmpty());
    }

    @Test
    public void seedTenantSync_doesNotReseedCatalogThatWasEmptiedOnPurpose() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        TenantCatalogSeeder.seedTenantSync(context, db, "fruteria");
        db.productoDao().deleteAll();

        assertTrue(TenantCatalogSeeder.seedTenantSync(context, db, "fruteria"));

        assertTrue(db.productoDao().getAll().isEmpty());
    }

    @Test
    public void seedTenantSync_failedSeedIsRolledBackSoNoPartialCatalogRemains() throws Exception {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        AssetManager realAssets = context.getAssets();
        AssetManager assetsWithOneMissingFile = Mockito.mock(AssetManager.class);
        Mockito.when(assetsWithOneMissingFile.open(Mockito.anyString())).thenAnswer(invocation -> {
            String file = invocation.getArgument(0);
            if ("menu_bocadillos_formato_visual.csv".equals(file)) return realAssets.open(file);
            throw new IOException("asset roto: " + file);
        });
        Context brokenContext = new ContextWrapper(context) {
            @Override
            public AssetManager getAssets() {
                return assetsWithOneMissingFile;
            }
        };

        assertFalse(TenantCatalogSeeder.seedTenantSync(brokenContext, db, "lanube"));

        assertTrue(db.productoDao().getAll().isEmpty());
        assertTrue(TenantCatalogSeeder.seedTenantSync(context, db, "lanube"));
        assertFalse(db.productoDao().getAll().isEmpty());
    }
}
