package com.softmyc.tpv.util;

public class CategoryUtils {

    /**
     * Normaliza los nombres de las categorías a formato Title Case consistente
     * para evitar duplicados en la interfaz (ej. "bocadillos" -> "Bocadillos").
     */
    public static String capitalizeCategory(String str) {
        if (str == null || str.trim().isEmpty()) {
            return "Otros";
        }
        String trimmed = str.trim();
        if (trimmed.equalsIgnoreCase("bocadillos") || trimmed.equalsIgnoreCase("bocadillo") || trimmed.equalsIgnoreCase("bocata") || trimmed.equalsIgnoreCase("bocatas")) {
            return "Bocadillos";
        }
        if (trimmed.equalsIgnoreCase("hamburguesas") || trimmed.equalsIgnoreCase("hamburguesa") || trimmed.equalsIgnoreCase("burger") || trimmed.equalsIgnoreCase("burgers") || trimmed.equalsIgnoreCase("burguer")) {
            return "Hamburguesas";
        }
        if (trimmed.equalsIgnoreCase("sandwiches") || trimmed.equalsIgnoreCase("sandwich") || trimmed.equalsIgnoreCase("sándwiches") || trimmed.equalsIgnoreCase("sándwich")) {
            return "Sandwiches";
        }
        if (trimmed.equalsIgnoreCase("otros") || trimmed.equalsIgnoreCase("otro") || trimmed.equalsIgnoreCase("ración") || trimmed.equalsIgnoreCase("racion") || trimmed.equalsIgnoreCase("raciones") || trimmed.equalsIgnoreCase("complementos") || trimmed.equalsIgnoreCase("complemento")) {
            return "Otros";
        }
        if (trimmed.equalsIgnoreCase("salsas") || trimmed.equalsIgnoreCase("salsa")) {
            return "Salsas";
        }
        if (trimmed.equalsIgnoreCase("extras") || trimmed.equalsIgnoreCase("extra") || trimmed.equalsIgnoreCase("suplementos") || trimmed.equalsIgnoreCase("suplemento")) {
            return "Extras";
        }
        if (trimmed.equalsIgnoreCase("bebidas") || trimmed.equalsIgnoreCase("bebida") || trimmed.equalsIgnoreCase("refrescos") || trimmed.equalsIgnoreCase("refresco")) {
            return "Bebidas";
        }
        return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1).toLowerCase();
    }
}
