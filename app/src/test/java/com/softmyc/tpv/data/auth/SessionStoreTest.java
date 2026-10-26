package com.softmyc.tpv.data.auth;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.data.InMemorySharedPreferences;

import org.junit.Before;
import org.junit.Test;

public class SessionStoreTest {

    private InMemorySharedPreferences prefs;
    private SessionStore store;

    @Before
    public void setUp() {
        prefs = new InMemorySharedPreferences();
        store = new SessionStore(prefs);
    }

    private static AuthSession session() {
        return new AuthSession("at_acceso", "rt_refresco", 1_000L, 9_000L, "ana", "encargado", "fruteria", false);
    }

    @Test
    public void load_withoutSavedSession_returnsNull() {
        assertNull(store.load());
    }

    @Test
    public void saveThenLoad_roundTripsEveryField() {
        store.save(session());

        AuthSession s = store.load();

        assertNotNull(s);
        assertEquals("at_acceso", s.accessToken);
        assertEquals("rt_refresco", s.refreshToken);
        assertEquals(1_000L, s.accessExpiresAtMs);
        assertEquals(9_000L, s.refreshExpiresAtMs);
        assertEquals("ana", s.username);
        assertEquals("encargado", s.role);
        assertEquals("fruteria", s.restaurantId);
        assertFalse(s.demo);
    }

    @Test
    public void save_alsoPublishesRoleUserAndTenantForTheScreens() {
        store.save(session());

        assertEquals("encargado", prefs.getString("user_role", null));
        assertEquals("ana", prefs.getString("user_name", null));
        assertEquals("fruteria", prefs.getString("restaurant_id", null));
    }

    @Test
    public void clear_removesTokensAndIdentity() {
        store.save(session());

        store.clear();

        assertNull(store.load());
        assertNull(prefs.getString("user_role", null));
        assertNull(prefs.getString("restaurant_id", null));
    }

    @Test
    public void load_withMissingTokens_returnsNullInsteadOfAHalfSession() {
        store.save(session());
        prefs.edit().remove("auth.refresh").commit();

        assertNull(store.load());
    }

    @Test
    public void demoSession_isFlaggedAndRoundTrips() {
        store.save(AuthSession.demo("taller", "tpv_operator", "Operador Taller", 5_000L));

        AuthSession s = store.load();

        assertTrue(s.demo);
        assertEquals("local_demo_token_taller", s.accessToken);
        assertEquals("taller", s.restaurantId);
    }

    @Test
    public void withTokens_keepsIdentityAndReplacesOnlyTheTokens() {
        AuthSession renewed = session().withTokens("at_nuevo", "rt_nuevo", 2_000L, 10_000L);

        assertEquals("at_nuevo", renewed.accessToken);
        assertEquals("rt_nuevo", renewed.refreshToken);
        assertEquals(2_000L, renewed.accessExpiresAtMs);
        assertEquals(10_000L, renewed.refreshExpiresAtMs);
        assertEquals("ana", renewed.username);
        assertEquals("fruteria", renewed.restaurantId);
    }

    @Test
    public void withTenant_changesBusinessAndRoleForAnAdminSwitch() {
        AuthSession switched = session().withTenant("taller", "admin");

        assertEquals("taller", switched.restaurantId);
        assertEquals("admin", switched.role);
        assertEquals("at_acceso", switched.accessToken);
    }

    @Test
    public void issuerIsPersisted() {
        store.save(new AuthSession("a", "r", 1L, 2L, "ana", "x", "y", false, "https://api.ejemplo.es"));

        assertEquals("https://api.ejemplo.es", store.load().issuer);
    }

    @Test
    public void saveIfCurrent_savesOnlyWhileTheExpectedSessionIsStillTheStoredOne() {
        store.save(session());

        assertTrue(store.saveIfCurrent("at_acceso", session().withTokens("at_nuevo", "rt_nuevo", 1L, 2L)));
        assertEquals("at_nuevo", store.load().accessToken);
        assertFalse("el token esperado ya no es el guardado", store.saveIfCurrent("at_acceso", session().withTokens("at_otro", "rt_otro", 1L, 2L)));
        assertEquals("at_nuevo", store.load().accessToken);
    }

    @Test
    public void saveIfCurrent_afterClear_doesNothing() {
        store.save(session());
        store.clear();

        assertFalse(store.saveIfCurrent("at_acceso", session()));
        assertNull(store.load());
    }

    @Test
    public void biometricEnrollment_roundTripsIvAndCiphertext() {
        store.saveBiometricEnrollment(new SessionStore.BiometricEnrollment(new byte[] {1, 2, 3}, new byte[] {9, 8}));

        SessionStore.BiometricEnrollment registro = store.loadBiometricEnrollment();

        assertNotNull(registro);
        assertArrayEquals(new byte[] {1, 2, 3}, registro.iv);
        assertArrayEquals(new byte[] {9, 8}, registro.ciphertext);
    }

    @Test
    public void biometricEnrollment_isAbsentUntilSaved() {
        assertNull(store.loadBiometricEnrollment());
    }

    @Test
    public void clearBiometricEnrollment_removesOnlyTheEnrollment() {
        store.save(session());
        store.saveBiometricEnrollment(new SessionStore.BiometricEnrollment(new byte[] {1}, new byte[] {2}));

        store.clearBiometricEnrollment();

        assertNull(store.loadBiometricEnrollment());
        assertNotNull(store.load());
    }

    @Test
    public void clear_alsoRemovesBiometricEnrollment() {
        store.save(session());
        store.saveBiometricEnrollment(new SessionStore.BiometricEnrollment(new byte[] {1}, new byte[] {2}));

        store.clear();

        assertNull(store.loadBiometricEnrollment());
    }
}

