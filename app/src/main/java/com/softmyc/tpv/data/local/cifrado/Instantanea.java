package com.softmyc.tpv.data.local.cifrado;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Resumen verificable de una base: versión de esquema, filas por tabla e integridad. Dos instantáneas son
 * equivalentes si tienen la misma versión y los mismos recuentos.
 */
public final class Instantanea {

    private final int version;
    private final Map<String, Long> filas;
    private final boolean integra;

    public Instantanea(int version, Map<String, Long> filas, boolean integra) {
        this.version = version;
        this.filas = Collections.unmodifiableMap(new TreeMap<>(filas));
        this.integra = integra;
    }

    public int version() {
        return version;
    }

    public Map<String, Long> filas() {
        return filas;
    }

    public boolean integra() {
        return integra;
    }

    public boolean equivalenteA(Instantanea otra) {
        return version == otra.version && filas.equals(otra.filas);
    }
}
