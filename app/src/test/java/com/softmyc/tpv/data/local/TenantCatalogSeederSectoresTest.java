package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Catálogos iniciales de los sectores de paquetería y mayorista (y de los demás negocios conocidos): cada uno
 * siembra sus productos, extras y salsas una sola vez, con unidades y precios propios del sector.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TenantCatalogSeederSectoresTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private Context context;
    private AppDatabase db;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void paqueteria_siembra_envios_cajas_y_extras_con_el_peso_por_kilo() {
        assertTrue(TenantCatalogSeeder.seedTenantSync(context, db, "paqueteria"));

        assertEquals(7, db.productoDao().getAll().size());
        ProductoLocal kilo = db.productoDao().getByCodigo(303);
        assertNotNull(kilo);
        assertEquals("KG", kilo.unidadMedida);
        assertEquals(1.80, kilo.precio, 1e-9);
        assertEquals(4, db.extraDao().getAll().size());
        assertEquals(4, db.salsaDao().getAll().size());
    }

    @Test
    public void mayorista_siembra_lotes_de_gran_formato_con_sus_extras_de_palet() {
        assertTrue(TenantCatalogSeeder.seedTenantSync(context, db, "mayorista"));

        assertEquals(5, db.productoDao().getAll().size());
        assertEquals(45.00, db.productoDao().getByCodigo(401).precio, 1e-9);
        assertEquals(3, db.extraDao().getAll().size());
        assertEquals(4, db.salsaDao().getAll().size());
    }

    @Test
    public void cada_sector_conocido_deja_un_catalogo_no_vacio_y_sin_codigos_repetidos() {
        String[] negocios = {"lanube", "elbocata", "fruteria", "taller", "paqueteria", "mayorista"};
        for (String negocio : negocios) {
            AppDatabase base = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
            try {
                assertTrue(negocio, TenantCatalogSeeder.seedTenantSync(context, base, negocio));
                int productos = base.productoDao().getAll().size();
                assertTrue(negocio + " sin productos", productos > 0);
                assertEquals(negocio + " con códigos repetidos", productos,
                        new java.util.HashSet<>(codigos(base)).size());
            } finally {
                base.close();
            }
        }
    }

    @Test
    public void el_segundo_siembra_no_duplica_el_catalogo_de_paqueteria() {
        TenantCatalogSeeder.seedTenantSync(context, db, "paqueteria");
        int antes = db.productoDao().getAll().size();

        assertTrue(TenantCatalogSeeder.seedTenantSync(context, db, "paqueteria"));

        assertEquals(antes, db.productoDao().getAll().size());
        assertFalse(db.productoDao().getAll().isEmpty());
    }

    private java.util.List<Integer> codigos(AppDatabase base) {
        java.util.List<Integer> codigos = new java.util.ArrayList<>();
        for (ProductoLocal p : base.productoDao().getAll()) {
            codigos.add(p.codigo);
        }
        return codigos;
    }
}
