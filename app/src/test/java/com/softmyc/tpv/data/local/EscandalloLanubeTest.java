package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.CsvParser;
import com.softmyc.tpv.domain.escandallo.InformeImportacion;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.usecase.ConsumoIngredientes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * El escandallo de La Nube (assets/escandallo_lanube.csv, generado desde ingredientes.md) da el mismo coste de
 * ingredientes por producto que el documento de costes del negocio (coste_bocadillos_final).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EscandalloLanubeTest {

    static {
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    /** Coste de ingredientes (€) por código de producto, copiado del documento de costes. */
    private static final Map<Integer, Double> PDF = new LinkedHashMap<>();
    static {
        double[][] t = {{1,3.50},{2,3.82},{3,2.83},{4,2.62},{5,3.22},{6,4.08},{7,3.39},{8,1.84},{10,3.42},{11,1.97},
                {12,1.13},{13,2.68},{14,1.12},{15,1.84},{16,3.25},{17,0.96},{18,3.73},{21,4.16},{22,1.76},{23,1.99},
                {25,4.24},{26,2.39},{27,2.49},{28,3.04},{29,3.28},{30,3.86},{31,3.63},{32,2.69},{33,3.10},{34,3.37},
                {35,3.96},{36,3.68},{38,4.70},{50,0.96},{51,1.91},{52,1.80},{53,0.52},{54,1.10},{55,2.56},{56,4.15},
                {57,2.35},{60,1.30}};
        for (double[] f : t) PDF.put((int) f[0], f[1]);
    }

    private Context context;
    private AppDatabase db;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        for (String[] f : new String[][]{{"menu_bocadillos_formato_visual.csv", "Bocadillos"},
                {"menu_hamburguesas_mayusculas.csv", "Hamburguesas"}, {"otros.csv", "Otros"},
                {"ingredientes_precio.csv", "Extras"}}) {
            try (InputStream is = context.getAssets().open(f[0])) {
                CsvParser.ParseResult r = CsvParser.parse(is, f[1]);
                db.productoDao().insertAll(r.productos);
                db.extraDao().insertAll(r.extras);
            }
        }
    }

    @After
    public void tearDown() {
        db.close();
    }

    private InformeImportacion importarAsset() throws Exception {
        try (InputStream is = context.getAssets().open(TenantCatalogSeeder.ESCANDALLO_LANUBE)) {
            return EscandalloCsvImporter.importar(db, is);
        }
    }

    @Test
    public void el_coste_de_cada_producto_coincide_con_el_documento_de_costes() throws Exception {
        InformeImportacion informe = importarAsset();
        Recetario r = RecetarioRoom.cargar(db);

        assertTrue("Filas rechazadas: " + informe.rechazadas, informe.rechazadas.isEmpty());
        for (Map.Entry<Integer, Double> e : PDF.entrySet()) {
            Double coste = ConsumoIngredientes.costeUnitario(e.getKey(), r);
            assertNotNull("Sin coste: producto " + e.getKey(), coste);
            assertEquals("Producto " + e.getKey(), e.getValue(), coste, 0.011);
        }
    }

    @Test
    public void carga_gastos_anadidos_por_tipo_de_producto_y_todos_los_extras() throws Exception {
        InformeImportacion informe = importarAsset();
        Recetario r = RecetarioRoom.cargar(db);

        assertEquals(Double.valueOf(1.69), r.gastosAnadidos(1));
        assertEquals(Double.valueOf(1.01), r.gastosAnadidos(14));
        assertEquals(Double.valueOf(0.15), r.gastosAnadidos(50));
        assertEquals(26, informe.extras);
        for (ExtraLocal e : db.extraDao().getAll()) {
            assertTrue("Extra sin enlazar: " + e.nombre,
                    !r.consumoExtra(com.softmyc.tpv.domain.NombreNormalizado.de(e.nombre)).isEmpty());
        }
    }

    @Test
    public void reimportar_no_duplica_y_conserva_el_stock() throws Exception {
        importarAsset();
        IngredienteLocal pollo = db.escandalloDao().getIngredientePorNombre("pollo");
        db.escandalloDao().setStock(pollo.id, 2500.0);
        int ingredientes = db.escandalloDao().contarIngredientes();
        int lineas = db.escandalloDao().getRecetaLineas().size();

        importarAsset();

        assertEquals(ingredientes, db.escandalloDao().contarIngredientes());
        assertEquals(lineas, db.escandalloDao().getRecetaLineas().size());
        assertEquals(Double.valueOf(2500.0), db.escandalloDao().getIngrediente(pollo.id).stock);
    }

    @Test
    public void las_filas_erroneas_se_rechazan_con_el_motivo_y_el_resto_se_carga() throws Exception {
        String csv = "# comentario\n"
                + "ING;;Pollo;G;;;6,99\n"
                + "ING;;;G;;;1\n"
                + "REC;1;Pollo;;100;Pollo;\n"
                + "REC;x;Pollo;;100;;\n"
                + "REC;1;Caviar;;10;;\n"
                + "REC;1;Pollo;;-3;;\n"
                + "EXT;Pollo;Pollo;;50;;\n"
                + "EXT;;Pollo;;50;;\n"
                + "EXT;Caviar;Caviar;;5;;\n"
                + "EXT;Pollo2;Pollo;;0;;\n"
                + "GAS;1;;;1.69;;\n"
                + "GAS;999;;;1.69;;\n"
                + "GAS;1;;;abc;;\n"
                + "OTRO;1\n";

        InformeImportacion informe = EscandalloCsvImporter.importar(db,
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, informe.ingredientes);
        assertEquals(1, informe.productosConReceta);
        assertEquals(1, informe.extras);
        assertEquals(1, informe.gastos);
        assertEquals(informe.rechazadas.toString(), 10, informe.rechazadas.size());
        assertEquals(Double.valueOf(6.99), db.escandalloDao().getIngredientePorNombre("pollo").precioCompra);
    }

    @Test
    public void numeros_con_coma_o_punto() {
        assertEquals(Double.valueOf(1.5), EscandalloCsvImporter.numero("1,5"));
        assertEquals(Double.valueOf(1.5), EscandalloCsvImporter.numero(" 1.5 "));
        assertEquals(null, EscandalloCsvImporter.numero(""));
        assertEquals(null, EscandalloCsvImporter.numero("NaN"));
        assertEquals(null, EscandalloCsvImporter.numero("x"));
    }

    @Test
    public void el_sembrado_de_la_nube_carga_el_escandallo_una_sola_vez() {
        assertTrue(TenantCatalogSeeder.seedTenantSync(context, db, "lanube"));
        int ingredientes = db.escandalloDao().contarIngredientes();
        assertTrue(ingredientes > 30);

        // Si el encargado borra un ingrediente, volver a abrir la app no lo resucita.
        db.escandalloDao().eliminarIngrediente(db.escandalloDao().getIngredientePorNombre("pollo").id);
        assertTrue(TenantCatalogSeeder.seedTenantSync(context, db, "lanube"));
        assertEquals(ingredientes - 1, db.escandalloDao().contarIngredientes());
    }

    @Test
    public void otros_negocios_no_reciben_el_escandallo_de_la_nube() {
        assertTrue(TenantCatalogSeeder.seedTenantSync(context, db, "fruteria"));
        assertEquals(0, db.escandalloDao().contarIngredientes());
    }
}
