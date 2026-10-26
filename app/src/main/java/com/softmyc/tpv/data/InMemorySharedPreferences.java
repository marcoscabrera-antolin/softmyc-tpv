package com.softmyc.tpv.data;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * SharedPreferences solo en memoria: se usa cuando el almacén cifrado no está disponible para no
 * degradar nunca a texto plano en disco. Los valores duran lo que dure el proceso.
 */
public class InMemorySharedPreferences implements SharedPreferences {

    private final Map<String, Object> values = new HashMap<>();

    @Override
    public synchronized Map<String, ?> getAll() {
        return new HashMap<>(values);
    }

    @Override
    public synchronized String getString(String key, String defValue) {
        Object v = values.get(key);
        return v instanceof String ? (String) v : defValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized Set<String> getStringSet(String key, Set<String> defValues) {
        Object v = values.get(key);
        return v instanceof Set ? new HashSet<>((Set<String>) v) : defValues;
    }

    @Override
    public synchronized int getInt(String key, int defValue) {
        Object v = values.get(key);
        return v instanceof Integer ? (Integer) v : defValue;
    }

    @Override
    public synchronized long getLong(String key, long defValue) {
        Object v = values.get(key);
        return v instanceof Long ? (Long) v : defValue;
    }

    @Override
    public synchronized float getFloat(String key, float defValue) {
        Object v = values.get(key);
        return v instanceof Float ? (Float) v : defValue;
    }

    @Override
    public synchronized boolean getBoolean(String key, boolean defValue) {
        Object v = values.get(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    @Override
    public synchronized boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new MemoryEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }

    private final class MemoryEditor implements Editor {
        private final Map<String, Object> pending = new HashMap<>();
        private final Set<String> removals = new HashSet<>();
        private boolean clear;

        @Override public Editor putString(String key, String value) { return stage(key, value); }
        @Override public Editor putStringSet(String key, Set<String> values) {
            return stage(key, values == null ? null : new HashSet<>(values));
        }
        @Override public Editor putInt(String key, int value) { return stage(key, value); }
        @Override public Editor putLong(String key, long value) { return stage(key, value); }
        @Override public Editor putFloat(String key, float value) { return stage(key, value); }
        @Override public Editor putBoolean(String key, boolean value) { return stage(key, value); }

        @Override public Editor remove(String key) {
            pending.remove(key);
            removals.add(key);
            return this;
        }

        @Override public Editor clear() {
            clear = true;
            return this;
        }

        @Override public boolean commit() {
            synchronized (InMemorySharedPreferences.this) {
                if (clear) values.clear();
                for (String key : removals) values.remove(key);
                values.putAll(pending);
            }
            return true;
        }

        @Override public void apply() {
            commit();
        }

        private Editor stage(String key, Object value) {
            if (value == null) return remove(key);
            removals.remove(key);
            pending.put(key, value);
            return this;
        }
    }
}
