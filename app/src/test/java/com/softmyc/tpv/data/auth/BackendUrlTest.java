package com.softmyc.tpv.data.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BackendUrlTest {

    @Test
    public void rootOf_stripsTheOrdersPathAndTrailingSlashes() {
        assertEquals("https://api.ejemplo.es", BackendUrl.rootOf("https://api.ejemplo.es/api/tpv/orders"));
        assertEquals("https://api.ejemplo.es", BackendUrl.rootOf("https://api.ejemplo.es/"));
        assertEquals("https://api.ejemplo.es", BackendUrl.rootOf("https://api.ejemplo.es/orders"));
    }

    @Test
    public void rootOf_addsTheSchemeWhenMissing() {
        assertEquals("https://api.ejemplo.es", BackendUrl.rootOf("api.ejemplo.es"));
    }

    @Test
    public void rootOf_keepsAnExplicitPort() {
        assertEquals("https://api.ejemplo.es:8443", BackendUrl.rootOf("https://api.ejemplo.es:8443/api/tpv"));
    }
}
