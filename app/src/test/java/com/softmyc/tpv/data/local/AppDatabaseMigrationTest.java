package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

/**
 * Abre bases creadas con el esquema exacto de cada versión histórica (v1..v4) a través de Room,
 * sin fallbackToDestructiveMigration: si una migración falta o deja un esquema distinto al de las
 * entidades, Room lanza IllegalStateException y el test falla; si se perdieran datos, lo detectan las aserciones.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppDatabaseMigrationTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String DB_NAME = "migration_test.db";
    private Context context;
    private AppDatabase openDb;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB_NAME);
    }

    @After
    public void tearDown() {
        if (openDb != null) openDb.close();
        context.deleteDatabase(DB_NAME);
    }

    @Test
    public void migrates_from_v1_keepingAllData_andFillingDefaults() {
        createLegacyDatabase(1);
        AppDatabase db = openWithMigrations();
        assertDataIntact(db);
        assertEquals("Efectivo", scalar(db, "SELECT metodoPago FROM ventas WHERE id = 1"));
        assertEquals("UD", scalar(db, "SELECT unidadMedida FROM productos WHERE codigo = 1"));
        assertEquals("-1", scalar(db, "SELECT stock FROM productos WHERE codigo = 1"));
    }

    @Test
    public void migrates_from_v2_keepingAllData() {
        createLegacyDatabase(2);
        AppDatabase db = openWithMigrations();
        assertDataIntact(db);
        assertEquals("Tarjeta", scalar(db, "SELECT metodoPago FROM ventas WHERE id = 1"));
    }

    @Test
    public void migrates_from_v3_keepingAllData() {
        createLegacyDatabase(3);
        AppDatabase db = openWithMigrations();
        assertDataIntact(db);
        assertEquals("UD", scalar(db, "SELECT unidadMedida FROM productos WHERE codigo = 1"));
    }

    @Test
    public void migrates_from_v4_keepingAllData_andDefaultingStockToUncontrolled() {
        createLegacyDatabase(4);
        AppDatabase db = openWithMigrations();
        assertDataIntact(db);
        assertEquals("KG", scalar(db, "SELECT unidadMedida FROM productos WHERE codigo = 1"));
        assertEquals("-1", scalar(db, "SELECT stock FROM productos WHERE codigo = 1"));
    }

    @Test
    public void migrates_from_v4_thatAlreadyHasStockColumn_preservingItsValue() {
        createLegacyDatabase(4);
        SQLiteDatabase sqlite = SQLiteDatabase.openDatabase(
                context.getDatabasePath(DB_NAME).getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        sqlite.execSQL("ALTER TABLE productos ADD COLUMN stock INTEGER NOT NULL DEFAULT -1");
        sqlite.execSQL("UPDATE productos SET stock = 7 WHERE codigo = 1");
        sqlite.close();

        AppDatabase db = openWithMigrations();
        assertDataIntact(db);
        assertEquals("7", scalar(db, "SELECT stock FROM productos WHERE codigo = 1"));
    }

    @Test
    public void migrates_from_v5_keepingAllData_andAssigningUniqueUuidToEachSale() {
        createLegacyDatabase(5);
        AppDatabase db = openWithMigrations();
        assertDataIntact(db);
        assertEquals("2", scalar(db, "SELECT COUNT(DISTINCT uuid) FROM ventas"));
        assertEquals("0", scalar(db, "SELECT COUNT(*) FROM ventas WHERE uuid IS NULL OR length(uuid) != 36"));
        assertEquals("2", scalar(db, "SELECT COUNT(*) FROM ventas WHERE uuid GLOB '????????-????-4???-[89ab]???-????????????'"));
        assertEquals("1", scalar(db, "SELECT isSynced FROM ventas WHERE id = 2"));
    }

    @Test
    public void migrates_from_v1_giving_every_sale_a_uuid() {
        createLegacyDatabase(1);
        AppDatabase db = openWithMigrations();
        assertEquals("2", scalar(db, "SELECT COUNT(DISTINCT uuid) FROM ventas"));
    }

    @Test
    public void opensFreshDatabaseAtCurrentVersion() {
        AppDatabase db = openWithMigrations();
        assertEquals(0, Integer.parseInt(scalar(db, "SELECT COUNT(*) FROM ventas")));
    }

    private AppDatabase openWithMigrations() {
        AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, DB_NAME)
                .addMigrations(AppDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build();
        openDb = db;
        db.getOpenHelper().getWritableDatabase(); // fuerza apertura, migración y validación del esquema
        return db;
    }

    private void assertDataIntact(AppDatabase db) {
        assertEquals("1", scalar(db, "SELECT COUNT(*) FROM productos"));
        assertEquals("2", scalar(db, "SELECT COUNT(*) FROM ventas"));
        assertEquals("1", scalar(db, "SELECT COUNT(*) FROM extras"));
        assertEquals("1", scalar(db, "SELECT COUNT(*) FROM salsas"));
        assertEquals("2", scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' "
                + "AND name IN ('index_ventas_fecha', 'index_ventas_isSynced')"));
        assertEquals("Hamburguesa", scalar(db, "SELECT nombre FROM productos WHERE codigo = 1"));
        assertEquals("12.5", scalar(db, "SELECT total FROM ventas WHERE id = 2"));
        assertEquals("1", scalar(db, "SELECT isSynced FROM ventas WHERE id = 2"));
    }

    private static String scalar(AppDatabase db, String sql) {
        try (Cursor c = db.getOpenHelper().getReadableDatabase().query(sql)) {
            assertTrue("Sin filas para: " + sql, c.moveToFirst());
            return c.getString(0);
        }
    }

    /** Crea la base con el DDL que Room generaba en cada versión y la rellena con datos de prueba. */
    private void createLegacyDatabase(int version) {
        File file = context.getDatabasePath(DB_NAME);
        SQLiteDatabase sqlite = SQLiteDatabase.openOrCreateDatabase(file, null);
        try {
            boolean hasPayment = version >= 2;
            boolean hasIndices = version >= 3;
            boolean hasUnit = version >= 4;
            boolean hasStock = version >= 5;

            sqlite.execSQL("CREATE TABLE productos (codigo INTEGER NOT NULL, nombre TEXT, precio REAL NOT NULL, "
                    + "categoria TEXT, ingredientes TEXT" + (hasUnit ? ", unidadMedida TEXT DEFAULT 'UD'" : "")
                    + (hasStock ? ", stock INTEGER NOT NULL DEFAULT -1" : "")
                    + ", PRIMARY KEY(codigo))");
            sqlite.execSQL("CREATE TABLE extras (nombre TEXT NOT NULL, precio REAL NOT NULL, PRIMARY KEY(nombre))");
            sqlite.execSQL("CREATE TABLE salsas (nombre TEXT NOT NULL, PRIMARY KEY(nombre))");
            sqlite.execSQL("CREATE TABLE ventas (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fecha INTEGER NOT NULL, "
                    + "total REAL NOT NULL, itemsJson TEXT, isSynced INTEGER NOT NULL"
                    + (hasPayment ? ", metodoPago TEXT DEFAULT 'Efectivo'" : "") + ")");
            if (hasIndices) {
                sqlite.execSQL("CREATE INDEX `index_ventas_fecha` ON `ventas` (`fecha`)");
                sqlite.execSQL("CREATE INDEX `index_ventas_isSynced` ON `ventas` (`isSynced`)");
            }

            sqlite.execSQL("INSERT INTO productos (codigo, nombre, precio, categoria, ingredientes"
                    + (hasUnit ? ", unidadMedida" : "") + ") VALUES (1, 'Hamburguesa', 4.5, 'Comida', '[\"Pan\"]'"
                    + (hasUnit ? ", 'KG'" : "") + ")");
            sqlite.execSQL("INSERT INTO extras VALUES ('Queso', 0.5)");
            sqlite.execSQL("INSERT INTO salsas VALUES ('Mayonesa')");
            String paymentCols = hasPayment ? ", metodoPago" : "";
            sqlite.execSQL("INSERT INTO ventas (id, fecha, total, itemsJson, isSynced" + paymentCols + ") VALUES "
                    + "(1, 1700000000000, 5.0, '[]', 0" + (hasPayment ? ", 'Tarjeta'" : "") + ")");
            sqlite.execSQL("INSERT INTO ventas (id, fecha, total, itemsJson, isSynced" + paymentCols + ") VALUES "
                    + "(2, 1700000100000, 12.5, '[]', 1" + (hasPayment ? ", 'Efectivo'" : "") + ")");
            sqlite.setVersion(version);
        } finally {
            sqlite.close();
        }
    }
}
