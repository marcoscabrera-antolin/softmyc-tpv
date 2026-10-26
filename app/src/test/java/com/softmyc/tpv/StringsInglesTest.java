package com.softmyc.tpv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Garantiza que values-en traduce todas las claves de values y conserva sus especificadores de formato. */
public class StringsInglesTest {
    private static final Pattern STRING = Pattern.compile("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", Pattern.DOTALL);
    private static final Pattern FORMATO = Pattern.compile("%(?:\\d+\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]");

    private static Map<String, String> leer(String carpeta) throws Exception {
        File f = new File("src/main/res/" + carpeta + "/strings.xml");
        String xml = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        Map<String, String> out = new TreeMap<>();
        Matcher m = STRING.matcher(xml);
        while (m.find()) out.put(m.group(1), m.group(2));
        return out;
    }

    private static List<String> formatos(String texto) {
        List<String> res = new ArrayList<>();
        Matcher m = FORMATO.matcher(texto.replace("%%", ""));
        while (m.find()) res.add(m.group().replaceAll("^%\\d+\\$", "%"));
        java.util.Collections.sort(res);
        return res;
    }

    @Test
    public void valuesEn_tieneLasMismasClavesQueValues() throws Exception {
        Map<String, String> es = leer("values");
        Map<String, String> en = leer("values-en");

        assertTrue("hay textos", es.size() > 200);
        assertEquals(es.keySet(), en.keySet());
    }

    @Test
    public void valuesEn_conservaLosEspecificadoresDeFormato() throws Exception {
        Map<String, String> es = leer("values");
        Map<String, String> en = leer("values-en");

        for (Map.Entry<String, String> e : es.entrySet()) {
            assertEquals("formato de " + e.getKey(), formatos(e.getValue()), formatos(en.get(e.getKey())));
        }
    }
}
