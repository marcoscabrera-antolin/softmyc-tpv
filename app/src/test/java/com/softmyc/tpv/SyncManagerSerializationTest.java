package com.softmyc.tpv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SyncManagerSerializationTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static PedidoItem burger(int qty) {
        return new PedidoItem(new Producto(1, "Burger", 6.0, "Hamburguesas", Collections.emptyList()), qty);
    }

    @Test
    public void serializarItems_withPrices_storesCategoryAndLineAmount() throws Exception {
        PedidoItem item = burger(2);
        item.getIngredientesExtra().add("Queso");
        Map<String, Double> extras = new HashMap<>();
        extras.put("Queso", 1.0);

        JSONArray arr = new JSONArray(SyncManager.serializarItems(Collections.singletonList(item), extras));

        assertEquals("Hamburguesas", arr.getJSONObject(0).getString("categoria"));
        assertEquals(14.0, arr.getJSONObject(0).getDouble("importe_linea"), 0.001);
    }

    @Test
    public void serializarItems_withoutPrices_omitsLineAmountButKeepsCategory() throws Exception {
        List<PedidoItem> items = Collections.singletonList(burger(1));

        JSONArray arr = new JSONArray(SyncManager.serializarItems(items));

        assertFalse(arr.getJSONObject(0).has("importe_linea"));
        assertEquals("Hamburguesas", arr.getJSONObject(0).getString("categoria"));
    }
}
