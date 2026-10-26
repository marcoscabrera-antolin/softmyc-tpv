package com.softmyc.tpv.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TypeSafeJsonHelper {

    @SuppressWarnings("unchecked")
    public static Map<String, Object> safeGetMap(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object val = map.get(key);
        if (val instanceof Map) {
            return (Map<String, Object>) val;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> safeGetMapList(Map<String, Object> map, String key) {
        if (map == null || key == null) return Collections.emptyList();
        Object val = map.get(key);
        if (val instanceof List) {
            List<?> rawList = (List<?>) val;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object obj : rawList) {
                if (obj instanceof Map) {
                    result.add((Map<String, Object>) obj);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    public static List<String> safeGetStringList(Map<String, Object> map, String key) {
        if (map == null || key == null) return Collections.emptyList();
        Object val = map.get(key);
        if (val instanceof List) {
            List<?> rawList = (List<?>) val;
            List<String> result = new ArrayList<>();
            for (Object obj : rawList) {
                if (obj != null) {
                    result.add(obj.toString());
                }
            }
            return result;
        } else if (val != null) {
            // Si es un String único o número en lugar de array, lo encapsula en una lista para evitar fallos
            List<String> result = new ArrayList<>();
            result.add(val.toString());
            return result;
        }
        return Collections.emptyList();
    }

    public static double safeGetDouble(Map<String, Object> map, String key, double defaultValue) {
        if (map == null || key == null) return defaultValue;
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        } else if (val instanceof String) {
            try {
                return Double.parseDouble((String) val);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static int safeGetInt(Map<String, Object> map, String key, int defaultValue) {
        if (map == null || key == null) return defaultValue;
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        } else if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static String safeGetString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || key == null) return defaultValue;
        Object val = map.get(key);
        if (val == null) return defaultValue;
        return val.toString();
    }
}
