package com.softmyc.tpv;

import static org.junit.Assert.assertEquals;

import com.softmyc.tpv.data.network.OrderSyncService;

import org.junit.Test;

public class SanitizeTenantIdTest {

    @Test
    public void keepsEachKnownTenantDistinct() {
        for (String tenant : new String[]{"lanube", "elbocata", "fruteria", "taller", "paqueteria", "mayorista"}) {
            assertEquals(tenant, OrderSyncService.sanitizeTenantId(tenant));
        }
    }

    @Test
    public void stripsVersionSuffixStoredInCurrentTenant() {
        assertEquals("fruteria", OrderSyncService.sanitizeTenantId("fruteria_v2"));
        assertEquals("lanube", OrderSyncService.sanitizeTenantId("lanube_v2"));
    }

    @Test
    public void stripsSoftmycPrefixAndNormalizesCase() {
        assertEquals("taller", OrderSyncService.sanitizeTenantId("softmyc_taller"));
        assertEquals("mayorista", OrderSyncService.sanitizeTenantId("  Mayorista_V2 "));
    }

    @Test
    public void keepsLegacyBocataAliasMappedToElBocata() {
        assertEquals("elbocata", OrderSyncService.sanitizeTenantId("softmyc_bocata"));
    }

    @Test
    public void returnsEmptyWhenMissingOrInvalidInsteadOfAssumingABusiness() {
        assertEquals("", OrderSyncService.sanitizeTenantId(null));
        assertEquals("", OrderSyncService.sanitizeTenantId("   "));
        assertEquals("", OrderSyncService.sanitizeTenantId("../etc;drop"));
    }
}
