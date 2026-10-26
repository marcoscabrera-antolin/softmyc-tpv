package com.softmyc.tpv.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.softmyc.tpv.data.local.cifrado.GestorBaseCifrada;
import com.softmyc.tpv.data.local.cifrado.ProveedorCifrado;

import java.io.File;

@Database(entities = {ProductoLocal.class, ExtraLocal.class, SalsaLocal.class, VentaLocal.class}, version = 6, exportSchema = true)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static final java.util.Map<String, AppDatabase> TENANT_INSTANCES = new java.util.concurrent.ConcurrentHashMap<>();
    /** Un único hilo daemon para sembrar catálogos: evita un executor sin cerrar por cada tenant. */
    private static final java.util.concurrent.ExecutorService SEEDER_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "tenant-catalog-seeder");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * Proveedor del cifrado de cada base. Producción: SQLCipher con la clave del Keystore. Tests JVM: null
     * (bases en claro), porque las libs nativas de SQLCipher no cargan en Robolectric.
     */
    private static volatile ProveedorCifrado proveedorCifrado = GestorBaseCifrada.PREDETERMINADO;

    /** Solo tests: sustituye el proveedor de cifrado (null = bases en claro). */
    public static void setProveedorCifradoParaTests(ProveedorCifrado proveedor) {
        proveedorCifrado = proveedor;
    }

    public abstract ProductoDao productoDao();
    public abstract ExtraDao extraDao();
    public abstract SalsaDao salsaDao();
    public abstract VentaDao ventaDao();

    /**
     * C4: Migración de v1 a v2.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE ventas ADD COLUMN metodoPago TEXT DEFAULT 'Efectivo'");
        }
    };

    /**
     * P2: Migración de v2 a v3.
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_ventas_fecha` ON `ventas` (`fecha`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_ventas_isSynced` ON `ventas` (`isSynced`)");
        }
    };

    /**
     * Migración de v3 a v4: Añade unidad de medida para el motor comercial universal de Fase 2.
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE productos ADD COLUMN unidadMedida TEXT DEFAULT 'UD'");
        }
    };

    /**
     * Migración de v4 a v5: añade el stock por producto (-1 = sin control de stock).
     */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Idempotente: builds de desarrollo previos ya crearon la columna en una base v4.
            boolean hasStock = false;
            try (android.database.Cursor c = database.query("PRAGMA table_info(productos)")) {
                int nameIdx = c.getColumnIndexOrThrow("name");
                while (c.moveToNext()) {
                    if ("stock".equals(c.getString(nameIdx))) hasStock = true;
                }
            }
            if (!hasStock) {
                database.execSQL("ALTER TABLE productos ADD COLUMN stock INTEGER NOT NULL DEFAULT -1");
            }
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE ventas ADD COLUMN uuid TEXT");
            // UUID v4 por fila (randomblob se evalúa en cada fila): las ventas ya guardadas también se pueden subir sin duplicarse.
            database.execSQL("UPDATE ventas SET uuid = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' "
                    + "|| substr(hex(randomblob(2)), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) "
                    + "|| substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))");
        }
    };

    /** Cadena completa de migraciones; la usan la base real y los tests de migración. */
    static final Migration[] ALL_MIGRATIONS = {MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6};

    /**
     * Negocio activo (id canónico) según la sesión, o null si no hay ninguno válido. No hay valor por defecto.
     */
    public static String getActiveTenantId(final Context context) {
        if (context == null) return null;
        return TenantContext.read(com.softmyc.tpv.data.SecurePreferencesHelper.getPrinterPrefs(context));
    }

    /** Guarda el negocio activo; un id inválido o nulo deja la app sin negocio. */
    public static void setActiveTenantId(final Context context, final String tenantId) {
        if (context == null) return;
        TenantContext.save(com.softmyc.tpv.data.SecurePreferencesHelper.getPrinterPrefs(context), tenantId);
    }

    /**
     * Base del negocio activo.
     *
     * @throws TenantContext.NoActiveTenantException si no hay un negocio válido activo.
     */
    public static AppDatabase getDatabase(final Context context) {
        return getDatabaseForTenant(context, getActiveTenantId(context));
    }

    /** Construye la base Room de un fichero, cifrada con el proveedor vigente (si lo hay). */
    private static AppDatabase construirBase(Context appContext, String dbFileName) {
        RoomDatabase.Builder<AppDatabase> builder = Room.databaseBuilder(appContext, AppDatabase.class, dbFileName)
                .addMigrations(ALL_MIGRATIONS);
        ProveedorCifrado proveedor = proveedorCifrado;
        if (proveedor != null) {
            File fichero = appContext.getDatabasePath(dbFileName);
            builder.openHelperFactory(proveedor.preparar(appContext, fichero));
        }
        return builder.build();
    }

    /**
     * Base de un negocio. Todos los alias del mismo negocio devuelven la misma instancia y el mismo fichero.
     *
     * @throws TenantContext.NoActiveTenantException si el id no es un negocio válido.
     */
    public static AppDatabase getDatabaseForTenant(final Context context, final String tenantId) {
        final String safeTenant = TenantContext.canonicalId(tenantId);
        if (safeTenant == null) {
            throw new TenantContext.NoActiveTenantException(TenantContext.NO_TENANT_MESSAGE);
        }
        AppDatabase instance = TENANT_INSTANCES.get(safeTenant);
        if (instance == null) {
            synchronized (AppDatabase.class) {
                instance = TENANT_INSTANCES.get(safeTenant);
                if (instance == null) {
                    String dbFileName = TenantContext.databaseFileName(safeTenant);

                    instance = construirBase(context.getApplicationContext(), dbFileName);

                    final AppDatabase finalDb = instance;
                    SEEDER_EXECUTOR.execute(() -> {
                        if (!TenantCatalogSeeder.seedTenantSync(context.getApplicationContext(), finalDb, safeTenant)) {
                            android.util.Log.e("AppDatabase", "No se pudo preparar la base del tenant " + safeTenant);
                        }
                    });

                    TENANT_INSTANCES.put(safeTenant, instance);
                }
            }
        }
        return instance;
    }
}
