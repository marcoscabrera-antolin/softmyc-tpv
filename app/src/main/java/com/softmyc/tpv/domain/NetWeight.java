package com.softmyc.tpv.domain;

/** Peso neto facturable: el motor de precios exige que el peso bruto supere la tara. */
public final class NetWeight {

    private NetWeight() { }

    public static boolean isValid(double grossKg, double tareKg) {
        return net(grossKg, tareKg) > 0.0;
    }

    /** Peso neto en kg redondeado al gramo. */
    public static double net(double grossKg, double tareKg) {
        return Math.round((grossKg - tareKg) * 1000.0) / 1000.0;
    }
}
