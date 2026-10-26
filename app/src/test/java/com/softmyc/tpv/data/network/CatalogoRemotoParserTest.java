package com.softmyc.tpv.data.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.repository.ExtraCatalogo;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class CatalogoRemotoParserTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    @Test
    public void productos_conCamposAusentes_usaValoresPorDefecto() throws JSONException {
        JSONArray arr = new JSONArray("[{\"nombre\":\"Cola\"}]");

        List<ProductoCatalogo> productos = CatalogoRemotoParser.productos(arr);

        assertEquals(1, productos.size());
        ProductoCatalogo p = productos.get(0);
        assertEquals(1, p.codigo);
        assertEquals("Cola", p.nombre);
        assertEquals(0.0, p.precio, 0.0);
        assertEquals("Otros", p.categoria);
        assertEquals("UD", p.unidadMedida);
        assertEquals(-1, p.stock);
        assertTrue(p.ingredientes.isEmpty());
    }

    @Test
    public void productos_conIngredientesYCategoriaAlias_normalizaLaCategoria() throws JSONException {
        JSONArray arr = new JSONArray(
                "[{\"codigo\":7,\"nombre\":\"Serranito\",\"precio\":4.5,\"categoria\":\"bocata\","
                        + "\"ingredientes\":[\"Jamón\",\"Lomo\"]}]");

        ProductoCatalogo p = CatalogoRemotoParser.productos(arr).get(0);

        assertEquals(7, p.codigo);
        assertEquals("Bocadillos", p.categoria);
        assertEquals(2, p.ingredientes.size());
        assertEquals("Lomo", p.ingredientes.get(1));
    }

    @Test(expected = JSONException.class)
    public void productos_conElementoQueNoEsObjeto_lanzaJsonException() throws JSONException {
        CatalogoRemotoParser.productos(new JSONArray("[\"texto\"]"));
    }

    @Test
    public void extras_sinArray_devuelveListaVacia() throws JSONException {
        assertTrue(CatalogoRemotoParser.extras(null).isEmpty());
    }

    @Test
    public void extras_conPrecioAusente_tomaCero() throws JSONException {
        List<ExtraCatalogo> extras = CatalogoRemotoParser.extras(
                new JSONArray("[{\"nombre\":\"Queso\",\"precio\":0.5},{\"nombre\":\"Huevo\"}]"));

        assertEquals(2, extras.size());
        assertEquals(0.5, extras.get(0).precio, 0.0);
        assertEquals(0.0, extras.get(1).precio, 0.0);
    }

    @Test
    public void salsas_admiteCadenasYObjetosYDescartaVacias() throws JSONException {
        JSONArray arr = new JSONArray(
                "[\" Alioli \",{\"nombre\":\"Brava\"},{\"name\":\"Mayonesa\"},\"  \",{},5]");

        List<String> salsas = CatalogoRemotoParser.salsas(arr);

        assertEquals(java.util.Arrays.asList("Alioli", "Brava", "Mayonesa"), salsas);
    }

    @Test
    public void salsas_sinArray_devuelveListaVacia() throws JSONException {
        assertTrue(CatalogoRemotoParser.salsas(null).isEmpty());
    }
}
