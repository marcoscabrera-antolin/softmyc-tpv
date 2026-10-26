package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;

import com.softmyc.tpv.data.auth.AuthSession;

import org.junit.Test;

public class SessionPolicyTest {

    private static final long NOW = 1_000_000L;
    private static final long MIN = 60_000L;
    private static final long DAY = 24 * 60 * MIN;

    private static AuthSession session(long accessExp, long refreshExp) {
        return new AuthSession("at_a", "rt_r", accessExp, refreshExp, "ana", "tpv_operator", "fruteria", false);
    }

    @Test
    public void withoutSession_isNoSession() {
        assertEquals(SessionPolicy.State.NO_SESSION, SessionPolicy.evaluate(null, NOW, true));
        assertEquals(SessionPolicy.State.NO_SESSION, SessionPolicy.evaluate(null, NOW, false));
    }

    @Test
    public void validAccessToken_isValid() {
        assertEquals(SessionPolicy.State.VALID, SessionPolicy.evaluate(session(NOW + 20 * MIN, NOW + 7 * DAY), NOW, true));
    }

    @Test
    public void accessTokenAboutToExpire_needsRefreshWhenOnline() {
        assertEquals(SessionPolicy.State.NEEDS_REFRESH, SessionPolicy.evaluate(session(NOW + 10_000L, NOW + 7 * DAY), NOW, true));
    }

    @Test
    public void expiredAccessTokenWithLiveRefresh_needsRefreshWhenOnline() {
        assertEquals(SessionPolicy.State.NEEDS_REFRESH, SessionPolicy.evaluate(session(NOW - MIN, NOW + 7 * DAY), NOW, true));
    }

    @Test
    public void expiredAccessTokenWithLiveRefresh_keepsSellingWhenOffline() {
        assertEquals(SessionPolicy.State.VALID, SessionPolicy.evaluate(session(NOW - MIN, NOW + 7 * DAY), NOW, false));
    }

    @Test
    public void expiredRefreshToken_isExpiredOnlineAndOffline() {
        assertEquals(SessionPolicy.State.EXPIRED, SessionPolicy.evaluate(session(NOW - 8 * DAY, NOW - MIN), NOW, true));
        assertEquals(SessionPolicy.State.EXPIRED, SessionPolicy.evaluate(session(NOW - 8 * DAY, NOW - MIN), NOW, false));
    }

    @Test
    public void refreshExpiryIsExclusive() {
        assertEquals(SessionPolicy.State.EXPIRED, SessionPolicy.evaluate(session(NOW + MIN, NOW), NOW, true));
    }

    @Test
    public void demoSession_isValidWhileItsLifetimeLasts() {
        AuthSession demo = AuthSession.demo("fruteria", "tpv_operator", "Operador", NOW);
        assertEquals(SessionPolicy.State.VALID, SessionPolicy.evaluate(demo, NOW + MIN, true));
        assertEquals(SessionPolicy.State.EXPIRED, SessionPolicy.evaluate(demo, NOW + 8 * DAY, true));
    }

    @Test
    public void needsBusiness_isTrueOnlyForARealSessionWithoutBusiness() {
        assertEquals(true, SessionPolicy.needsBusiness(new AuthSession("a", "r", 1L, 2L, "root", "admin", null, false)));
        assertEquals(false, SessionPolicy.needsBusiness(new AuthSession("a", "r", 1L, 2L, "ana", "tpv_operator", "fruteria", false)));
        assertEquals(false, SessionPolicy.needsBusiness(AuthSession.demo("taller", "admin", "x", 0L)));
        assertEquals(false, SessionPolicy.needsBusiness(null));
    }
}
