package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.data.InMemorySharedPreferences;

import org.junit.Test;

public class TenantContextTest {

    @Test
    public void canonicalId_keepsEachKnownTenant() {
        assertEquals("lanube", TenantContext.canonicalId("lanube"));
        assertEquals("fruteria", TenantContext.canonicalId("fruteria"));
        assertEquals("mayorista", TenantContext.canonicalId("mayorista"));
    }

    @Test
    public void canonicalId_stripsVersionSuffixPrefixAndCase() {
        assertEquals("fruteria", TenantContext.canonicalId("fruteria_v2"));
        assertEquals("taller", TenantContext.canonicalId("softmyc_taller"));
        assertEquals("mayorista", TenantContext.canonicalId("  Mayorista_V2 "));
    }

    @Test
    public void canonicalId_aliasesOfTheSameBusinessShareOneId() {
        assertEquals("elbocata", TenantContext.canonicalId("softmyc_elbocata"));
        assertEquals("elbocata", TenantContext.canonicalId("elbocata_v2"));
        assertEquals("elbocata", TenantContext.canonicalId("softmyc_bocata"));
    }

    @Test
    public void canonicalId_returnsNullWhenMissingOrInvalid() {
        assertNull(TenantContext.canonicalId(null));
        assertNull(TenantContext.canonicalId("   "));
        assertNull(TenantContext.canonicalId("softmyc"));
        assertNull(TenantContext.canonicalId("../etc;drop"));
        assertNull(TenantContext.canonicalId("a/b"));
        assertNull(TenantContext.canonicalId("La Nube Bocatería"));
        assertNull(TenantContext.canonicalId(repeat('a', 41)));
    }

    @Test
    public void canonicalId_acceptsIdsUpToFortyCharacters() {
        assertEquals(repeat('a', 40), TenantContext.canonicalId(repeat('a', 40)));
    }

    @Test
    public void databaseFileName_usesLegacyNameForLaNubeAndPrefixForOthers() {
        assertEquals("softmyc_database.db", TenantContext.databaseFileName("lanube"));
        assertEquals("softmyc_fruteria.db", TenantContext.databaseFileName("fruteria"));
    }

    @Test
    public void databaseFileName_isTheSameForEveryAliasOfABusiness() {
        assertEquals(TenantContext.databaseFileName("elbocata"),
                TenantContext.databaseFileName(TenantContext.canonicalId("softmyc_elbocata")));
        assertEquals(TenantContext.databaseFileName("elbocata"),
                TenantContext.databaseFileName(TenantContext.canonicalId("bocata")));
    }

    @Test
    public void save_writesCanonicalIdAndVersionedMirror() {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();

        TenantContext.save(prefs, "  Lanube ");

        assertEquals("lanube", prefs.getString("restaurant_id", null));
        assertEquals("lanube_v2", prefs.getString("current_tenant", null));
    }

    @Test
    public void save_invalidIdRemovesBothKeys() {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        TenantContext.save(prefs, "fruteria");

        TenantContext.save(prefs, "../etc");

        assertNull(prefs.getString("restaurant_id", null));
        assertNull(prefs.getString("current_tenant", null));
        assertNull(TenantContext.read(prefs));
    }

    @Test
    public void read_returnsNullWithoutDefaultBusiness() {
        assertNull(TenantContext.read(new InMemorySharedPreferences()));
        assertFalse(TenantContext.hasActive(new InMemorySharedPreferences()));
    }

    @Test
    public void read_prefersRestaurantIdAndFallsBackToVersionedKey() {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        prefs.edit().putString("current_tenant", "fruteria_v2").apply();
        assertEquals("fruteria", TenantContext.read(prefs));

        prefs.edit().putString("restaurant_id", "taller").apply();
        assertEquals("taller", TenantContext.read(prefs));
        assertTrue(TenantContext.hasActive(prefs));
    }

    @Test
    public void read_ignoresInvalidStoredValue() {
        InMemorySharedPreferences prefs = new InMemorySharedPreferences();
        prefs.edit().putString("restaurant_id", "../x").apply();

        assertNull(TenantContext.read(prefs));
    }

    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(c);
        return sb.toString();
    }
}
