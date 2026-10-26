package com.softmyc.tpv.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class InMemorySharedPreferencesTest {

    @Test
    public void putAndGet_allSupportedTypes() {
        InMemorySharedPreferences p = new InMemorySharedPreferences();
        p.edit().putString("s", "x").putInt("i", 3).putLong("l", 9L).putBoolean("b", true).putFloat("f", 1.5f).commit();

        assertEquals("x", p.getString("s", null));
        assertEquals(3, p.getInt("i", 0));
        assertEquals(9L, p.getLong("l", 0L));
        assertTrue(p.getBoolean("b", false));
        assertEquals(1.5f, p.getFloat("f", 0f), 0.0001f);
    }

    @Test
    public void get_missingKey_returnsDefault() {
        InMemorySharedPreferences p = new InMemorySharedPreferences();

        assertEquals("def", p.getString("none", "def"));
        assertEquals(7, p.getInt("none", 7));
        assertFalse(p.contains("none"));
    }

    @Test
    public void remove_and_clear() {
        InMemorySharedPreferences p = new InMemorySharedPreferences();
        p.edit().putString("a", "1").putString("b", "2").apply();

        p.edit().remove("a").apply();
        assertFalse(p.contains("a"));
        assertTrue(p.contains("b"));

        p.edit().clear().apply();
        assertTrue(p.getAll().isEmpty());
    }

    @Test
    public void stringSet_isStoredAsCopy() {
        InMemorySharedPreferences p = new InMemorySharedPreferences();
        Set<String> original = new HashSet<>(Arrays.asList("a", "b"));
        p.edit().putStringSet("set", original).apply();
        original.add("c");

        assertEquals(2, p.getStringSet("set", null).size());
    }
}
