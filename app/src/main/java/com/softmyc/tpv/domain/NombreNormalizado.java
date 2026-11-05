package com.softmyc.tpv.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Regla única para comparar nombres escritos a mano ("Jamón" = "jamon", "Tomate  Natural" = "tomate natural"):
 * minúsculas, sin acentos y con los espacios normalizados. La usan el buscador y el escandallo.
 */
public final class NombreNormalizado {

    private NombreNormalizado() { }

    public static String de(String s) {
        if (s == null) return "";
        String sinAcentos = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
