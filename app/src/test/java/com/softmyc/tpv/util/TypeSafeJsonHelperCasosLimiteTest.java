package com.softmyc.tpv.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Casos límite de los accesores tolerantes a JSON: tipos inesperados, cadenas no numéricas,
 * claves ausentes y mapas nulos. Cada accesor debe devolver el valor por defecto en lugar de lanzar.
 */
public class TypeSafeJsonHelperCasosLimiteTest {

    @Test
    public void safeGetInt_parseaEnteroDesdeCadena() {
        Map<String, Object> datos = mapa("cantidad", "12");

        assertEquals(12, TypeSafeJsonHelper.safeGetInt(datos, "cantidad", 1));
    }

    @Test
    public void safeGetInt_devuelveDefectoSiLaCadenaNoEsEntera() {
        Map<String, Object> datos = mapa("cantidad", "doce");

        assertEquals(1, TypeSafeJsonHelper.safeGetInt(datos, "cantidad", 1));
    }

    @Test
    public void safeGetInt_devuelveDefectoSiLaCadenaTieneDecimales() {
        Map<String, Object> datos = mapa("cantidad", "2.5");

        assertEquals(7, TypeSafeJsonHelper.safeGetInt(datos, "cantidad", 7));
    }

    @Test
    public void safeGetInt_truncaUnDoubleAEntero() {
        Map<String, Object> datos = mapa("cantidad", 3.9);

        assertEquals(3, TypeSafeJsonHelper.safeGetInt(datos, "cantidad", 1));
    }

    @Test
    public void safeGetInt_devuelveDefectoSiElTipoNoEsNumericoNiTexto() {
        Map<String, Object> datos = mapa("cantidad", Collections.emptyList());

        assertEquals(5, TypeSafeJsonHelper.safeGetInt(datos, "cantidad", 5));
    }

    @Test
    public void safeGetInt_devuelveDefectoConMapaONombreNulos() {
        assertEquals(4, TypeSafeJsonHelper.safeGetInt(null, "cantidad", 4));
        assertEquals(4, TypeSafeJsonHelper.safeGetInt(mapa("cantidad", 9), null, 4));
    }

    @Test
    public void safeGetDouble_parseaDecimalDesdeCadena() {
        Map<String, Object> datos = mapa("precio", "2.50");

        assertEquals(2.5, TypeSafeJsonHelper.safeGetDouble(datos, "precio", 0.0), 1e-9);
    }

    @Test
    public void safeGetDouble_devuelveDefectoSiLaCadenaEstaVacia() {
        Map<String, Object> datos = mapa("precio", "");

        assertEquals(0.0, TypeSafeJsonHelper.safeGetDouble(datos, "precio", 0.0), 1e-9);
    }

    @Test
    public void safeGetDouble_devuelveDefectoConValorNulo() {
        Map<String, Object> datos = mapa("precio", null);

        assertEquals(8.0, TypeSafeJsonHelper.safeGetDouble(datos, "precio", 8.0), 1e-9);
    }

    @Test
    public void safeGetDouble_devuelveDefectoSiLaClaveNoExiste() {
        assertEquals(1.25, TypeSafeJsonHelper.safeGetDouble(new HashMap<>(), "precio", 1.25), 1e-9);
    }

    @Test
    public void safeGetString_devuelveDefectoConValorNulo() {
        Map<String, Object> datos = mapa("nombre", null);

        assertEquals("Cliente", TypeSafeJsonHelper.safeGetString(datos, "nombre", "Cliente"));
    }

    @Test
    public void safeGetString_convierteNumeroAItsRepresentacionTextual() {
        Map<String, Object> datos = mapa("telefono", 600123);

        assertEquals("600123", TypeSafeJsonHelper.safeGetString(datos, "telefono", "-"));
    }

    @Test
    public void safeGetString_conservaCadenaVaciaSinUsarDefecto() {
        Map<String, Object> datos = mapa("nota", "");

        assertEquals("", TypeSafeJsonHelper.safeGetString(datos, "nota", "sin nota"));
    }

    @Test
    public void safeGetString_devuelveDefectoConMapaNulo() {
        assertEquals("x", TypeSafeJsonHelper.safeGetString(null, "nombre", "x"));
    }

    @Test
    public void safeGetStringList_encapsulaUnEscalarEnUnaLista() {
        Map<String, Object> datos = mapa("extras", "queso");

        assertEquals(Collections.singletonList("queso"), TypeSafeJsonHelper.safeGetStringList(datos, "extras"));
    }

    @Test
    public void safeGetStringList_convierteNumerosYOmiteNulos() {
        List<Object> mezcla = Arrays.asList("a", null, 3, "b");
        Map<String, Object> datos = mapa("codigos", mezcla);

        assertEquals(Arrays.asList("a", "3", "b"), TypeSafeJsonHelper.safeGetStringList(datos, "codigos"));
    }

    @Test
    public void safeGetStringList_devuelveListaVaciaSiLaClaveNoExisteOElValorEsNulo() {
        assertTrue(TypeSafeJsonHelper.safeGetStringList(new HashMap<>(), "extras").isEmpty());
        assertTrue(TypeSafeJsonHelper.safeGetStringList(mapa("extras", null), "extras").isEmpty());
    }

    @Test
    public void safeGetStringList_devuelveListaVaciaConMapaNulo() {
        assertTrue(TypeSafeJsonHelper.safeGetStringList(null, "extras").isEmpty());
    }

    @Test
    public void safeGetMapList_descartaElementosQueNoSonMapas() {
        Map<String, Object> linea = new HashMap<>();
        linea.put("nombre", "Café");
        Map<String, Object> datos = mapa("items", Arrays.asList(linea, "basura", 42));

        List<Map<String, Object>> items = TypeSafeJsonHelper.safeGetMapList(datos, "items");

        assertEquals(1, items.size());
        assertEquals("Café", items.get(0).get("nombre"));
    }

    @Test
    public void safeGetMapList_devuelveListaVaciaSiNoEsUnaLista() {
        Map<String, Object> datos = mapa("items", new HashMap<String, Object>());

        assertTrue(TypeSafeJsonHelper.safeGetMapList(datos, "items").isEmpty());
        assertTrue(TypeSafeJsonHelper.safeGetMapList(null, "items").isEmpty());
        assertTrue(TypeSafeJsonHelper.safeGetMapList(datos, null).isEmpty());
    }

    @Test
    public void safeGetMap_devuelveNullSiElValorNoEsUnMapa() {
        Map<String, Object> datos = mapa("cliente", "Juan");

        assertEquals(null, TypeSafeJsonHelper.safeGetMap(datos, "cliente"));
        assertEquals(null, TypeSafeJsonHelper.safeGetMap(null, "cliente"));
        assertEquals(null, TypeSafeJsonHelper.safeGetMap(datos, null));
    }

    private static Map<String, Object> mapa(String clave, Object valor) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put(clave, valor);
        return mapa;
    }
}
