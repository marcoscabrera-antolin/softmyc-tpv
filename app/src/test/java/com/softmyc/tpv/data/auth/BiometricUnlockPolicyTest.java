package com.softmyc.tpv.data.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BiometricUnlockPolicyTest {

    @Test
    public void decide_withoutStrongBiometric_hidesButton() {
        assertEquals(BiometricUnlockPolicy.Action.HIDDEN, BiometricUnlockPolicy.decide(false, true, true));
    }

    @Test
    public void decide_withoutUsableSession_hidesButtonEvenWithEnrollment() {
        assertEquals(BiometricUnlockPolicy.Action.HIDDEN, BiometricUnlockPolicy.decide(true, false, true));
    }

    @Test
    public void decide_withSessionAndNoEnrollment_offersEnrollment() {
        assertEquals(BiometricUnlockPolicy.Action.ENROLL, BiometricUnlockPolicy.decide(true, true, false));
    }

    @Test
    public void decide_withSessionAndEnrollment_offersUnlock() {
        assertEquals(BiometricUnlockPolicy.Action.UNLOCK, BiometricUnlockPolicy.decide(true, true, true));
    }

    @Test
    public void isUnlockMarker_acceptsTheExpectedMarker() {
        assertTrue(BiometricUnlockPolicy.isUnlockMarker(BiometricUnlockPolicy.unlockMarker()));
    }

    @Test
    public void isUnlockMarker_rejectsOtherTextNullAndEmpty() {
        assertFalse(BiometricUnlockPolicy.isUnlockMarker("otro".getBytes()));
        assertFalse(BiometricUnlockPolicy.isUnlockMarker(null));
        assertFalse(BiometricUnlockPolicy.isUnlockMarker(new byte[0]));
    }

    @Test
    public void unlockMarker_returnsACopyThatCannotAlterThePolicy() {
        byte[] copia = BiometricUnlockPolicy.unlockMarker();
        copia[0] = 0;

        assertTrue(BiometricUnlockPolicy.isUnlockMarker(BiometricUnlockPolicy.unlockMarker()));
    }
}
