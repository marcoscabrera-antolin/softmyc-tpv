package com.softmyc.tpv.data.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import okhttp3.HttpUrl;

import org.junit.Test;

public class OrderSyncUrlsTest {

    @Test
    public void apiRootOf_quitaElSufijoDePedidos() {
        HttpUrl root = OrderSyncUrls.apiRootOf(HttpUrl.get("https://host.test/api/tpv/orders?x=1"));

        assertEquals("https://host.test/", root.toString());
    }

    @Test
    public void apiRootOf_conPrefijoDeRuta_conservaElPrefijo() {
        HttpUrl root = OrderSyncUrls.apiRootOf(HttpUrl.get("https://host.test/v2/api/tpv/orders/"));

        assertEquals("https://host.test/v2", root.toString());
    }

    @Test
    public void parse_conUrlInvalida_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> OrderSyncUrls.parse("no es una url"));
    }

    @Test
    public void apiRootUrl_conOrigenExplicito_devuelveSoloEsquemaHostYPuerto() {
        assertEquals("http://192.168.0.78:3000", OrderSyncUrls.apiRootUrl("http://192.168.0.78:3000/api/tpv/orders"));
    }

    @Test
    public void apiRootUrl_vacia_devuelveLaUrlPorDefecto() {
        assertEquals(com.softmyc.tpv.BuildConfig.DEFAULT_BACKEND_URL, OrderSyncUrls.apiRootUrl("  "));
    }
}
