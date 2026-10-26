package com.softmyc.tpv;

import static org.junit.Assert.assertEquals;

import com.softmyc.tpv.data.network.OrderSyncService;

import org.junit.Test;

public class OrderSyncServiceTest {

    @Test
    public void testCleanUrlNormalizesMalformedUrls() {
        // Casos con typos típicos de http/https y adición de ruta por defecto
        assertEquals("https://bocaterianube.com/api/tpv/orders", OrderSyncService.cleanUrl("https/bocaterianube.com"));
        assertEquals("https://bocaterianube.com/api/tpv/orders", OrderSyncService.cleanUrl("https:/bocaterianube.com"));
        assertEquals("http://192.168.0.78:3000/api/tpv/orders", OrderSyncService.cleanUrl("http/192.168.0.78:3000"));
        assertEquals("http://192.168.0.78:3000/api/tpv/orders", OrderSyncService.cleanUrl("http:/192.168.0.78:3000"));

        // Sin esquema (debe auto-agregar https:// y /api/tpv/orders)
        assertEquals("https://bocaterianube.com/api/tpv/orders", OrderSyncService.cleanUrl("bocaterianube.com"));
        assertEquals("https://localhost:3000/api/tpv/orders", OrderSyncService.cleanUrl("localhost:3000"));

        // Barras duplicadas
        assertEquals("https://bocaterianube.com/api/tpv/orders", OrderSyncService.cleanUrl("https:///bocaterianube.com"));
        assertEquals("https://bocaterianube.com/api/tpv/orders", OrderSyncService.cleanUrl("https:////bocaterianube.com"));
        assertEquals("http://192.168.1.100/api/tpv/orders", OrderSyncService.cleanUrl("http:///192.168.1.100"));

        // Nulos y vacíos
        assertEquals("", OrderSyncService.cleanUrl(null));
        assertEquals("", OrderSyncService.cleanUrl("   "));

        // URL ya válida intacta y con barra final removida
        assertEquals("https://bocaterianube.com/api/tpv/orders", OrderSyncService.cleanUrl("https://bocaterianube.com/api/tpv/orders"));
        assertEquals("https://bocaterianube.com/api/tpv/orders", OrderSyncService.cleanUrl("https://bocaterianube.com/api/tpv/orders/"));
        assertEquals("https://bocaterianube.com/api/tpv/orders", OrderSyncService.cleanUrl("https://bocaterianube.com/api/tpv/orders//"));

        // Casos con parámetros de consulta
        assertEquals("https://bocaterianube.com/api/tpv/orders", OrderSyncService.cleanUrl("bocaterianube.com?restaurantID=lanube"));
        assertEquals("https://bocaterianube.com/api/tpv/orders", OrderSyncService.cleanUrl("https://bocaterianube.com/api/tpv/orders?restaurantID=lanube"));
        assertEquals("http://192.168.0.78:3000/api/tpv/orders", OrderSyncService.cleanUrl("http://192.168.0.78:3000/?restaurantID=lanube"));
    }
}
