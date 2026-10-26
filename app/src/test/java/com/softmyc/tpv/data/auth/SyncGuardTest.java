package com.softmyc.tpv.data.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SyncGuardTest {

    private static AuthSession real(String tenant) {
        return new AuthSession("a", "r", 1L, 2L, "ana", "tpv_operator", tenant, false);
    }

    @Test
    public void sameBusinessAsTheSession_canSync() {
        assertTrue(SyncGuard.canUploadFor(real("fruteria"), "fruteria"));
    }

    @Test
    public void localBusinessIdIsNormalizedBeforeComparing() {
        assertTrue(SyncGuard.canUploadFor(real("fruteria"), "softmyc_fruteria_v2"));
        assertTrue(SyncGuard.canUploadFor(real("lanube"), "LaNube".toLowerCase()));
    }

    @Test
    public void differentBusiness_mustNotUploadBecauseTheServerFilesSalesUnderTheSessionBusiness() {
        assertFalse(SyncGuard.canUploadFor(real("taller"), "fruteria"));
    }

    @Test
    public void invalidLocalBusiness_mustNotFallBackToLaNubeAndUpload() {
        assertFalse(SyncGuard.canUploadFor(real("lanube"), "../etc;drop"));
        assertFalse(SyncGuard.canUploadFor(real("lanube"), null));
    }

    @Test
    public void sessionWithoutBusiness_mustNotUpload() {
        assertFalse(SyncGuard.canUploadFor(real(null), "fruteria"));
    }

    @Test
    public void noSession_orDemoSession_followsTheLegacyApiKeyPath() {
        assertTrue(SyncGuard.canUploadFor(null, "fruteria"));
        assertTrue(SyncGuard.canUploadFor(AuthSession.demo("taller", "tpv_operator", "x", 0L), "fruteria"));
    }
}
