package com.softmyc.tpv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.softmyc.tpv.util.TypeSafeJsonHelper;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class TypeSafeJsonHelperTest {

    @Test
    public void testReproduceAndFixCrashPayload() {
        // Payload JSON malformado similar al que genera cierres inesperados (ClassCastException)
        String malformedJsonPayload = "[\n" +
                "  {\n" +
                "    \"_id\": \"64d0f1a23b4f6c8d9e0f1234\",\n" +
                "    \"restaurantId\": \"lanube\",\n" +
                "    \"stripeSessionId\": \"cs_test_12345\",\n" +
                "    \"fecha\": \"2026-08-19T10:43:08.000Z\",\n" +
                "    \"total\": \"12.50\",\n" + // total como String en lugar de Number
                "    \"estado\": \"pendiente\",\n" +
                "    \"cliente\": \"Juan Perez\",\n" + // cliente como String en lugar de Objeto/Map
                "    \"items\": [\n" +
                "      {\n" +
                "        \"nombre\": \"Burger Nube\",\n" +
                "        \"cantidad\": 2,\n" +
                "        \"precio_unitario\": 6.25,\n" +
                "        \"extras\": \"Queso Cheddar\",\n" + // extras como String en lugar de Array/List
                "        \"salsas\": [\"Mayo\", \"Ketchup\"],\n" +
                "        \"quitados\": null\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "]";

        Gson gson = new Gson();
        Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
        List<Map<String, Object>> orders = gson.fromJson(malformedJsonPayload, listType);

        assertNotNull(orders);
        assertEquals(1, orders.size());

        Map<String, Object> order = orders.get(0);

        // 1. Demostrar y comprobar que un casteo directo causaría ClassCastException
        try {
            // El campo cliente es un String en el JSON. Intentar castear a Map causará crash.
            @SuppressWarnings("unused")
            Map<String, Object> clienteDirect = (Map<String, Object>) order.get("cliente");
            fail("Se esperaba ClassCastException al realizar casteo directo de 'cliente' (String -> Map)");
        } catch (ClassCastException e) {
            // Éxito: el casteo directo falla
        }

        // 2. Verificar que safeGetMap recupera null de forma segura en lugar de crashear
        Map<String, Object> safeCliente = TypeSafeJsonHelper.safeGetMap(order, "cliente");
        assertNull(safeCliente);

        // 3. Verificar que safeGetString obtiene el valor de texto correspondiente
        String clienteNombre = TypeSafeJsonHelper.safeGetString(order, "cliente", "Cliente Online");
        assertEquals("Juan Perez", clienteNombre);

        // 4. Verificar conversión segura de 'total' de String a double
        double safeTotal = TypeSafeJsonHelper.safeGetDouble(order, "total", 0.0);
        assertEquals(12.50, safeTotal, 0.001);

        // 5. Demostrar y comprobar que castear directamente 'extras' de un item a List lanza ClassCastException
        List<Map<String, Object>> items = TypeSafeJsonHelper.safeGetMapList(order, "items");
        assertEquals(1, items.size());
        Map<String, Object> item = items.get(0);

        try {
            // El campo 'extras' es un String en el JSON. Intentar castear a List causará crash.
            @SuppressWarnings("unused")
            List<String> extrasDirect = (List<String>) item.get("extras");
            fail("Se esperaba ClassCastException al realizar casteo directo de 'extras' (String -> List)");
        } catch (ClassCastException e) {
            // Éxito: el casteo directo falla
        }

        // 6. Verificar que safeGetStringList maneja un String convirtiéndolo a lista de un elemento
        List<String> safeExtras = TypeSafeJsonHelper.safeGetStringList(item, "extras");
        assertNotNull(safeExtras);
        assertEquals(1, safeExtras.size());
        assertEquals("Queso Cheddar", safeExtras.get(0));

        // 7. Verificar que safeGetStringList maneja listas correctas (salsas)
        List<String> safeSalsas = TypeSafeJsonHelper.safeGetStringList(item, "salsas");
        assertNotNull(safeSalsas);
        assertEquals(2, safeSalsas.size());
        assertEquals("Mayo", safeSalsas.get(0));
        assertEquals("Ketchup", safeSalsas.get(1));

        // 8. Verificar que safeGetStringList maneja nulos de forma segura devolviendo lista vacía
        List<String> safeQuitados = TypeSafeJsonHelper.safeGetStringList(item, "quitados");
        assertNotNull(safeQuitados);
        assertTrue(safeQuitados.isEmpty());
    }
}
