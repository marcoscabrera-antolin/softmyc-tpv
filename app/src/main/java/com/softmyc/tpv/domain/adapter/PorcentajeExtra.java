package com.softmyc.tpv.domain.adapter;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae el porcentaje explícito del nombre de un extra, como "Urgencia +20%" (NEG-05).
 *
 * Solo cuenta como porcentaje si lleva signo (+ o -) y el % va al final del nombre, opcionalmente
 * seguido de ")". Así "Queso 100% vaca" o "Urgencia 30%" (sin signo) no se interpretan y usan el precio
 * configurado del extra. El valor debe estar entre 0 (exclusive) y 100 (inclusive) en valor absoluto.
 */
final class PorcentajeExtra {

    /** Porcentaje máximo admitido en valor absoluto. */
    static final BigDecimal MAXIMO = new BigDecimal("100");

    private static final Pattern PATRON_FINAL =
            Pattern.compile("([+-])\\s*(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*%\\s*\\)?\\s*$");

    private PorcentajeExtra() { }

    /**
     * @return el porcentaje con su signo (negativo = descuento), o null si el nombre no declara
     *         un porcentaje explícito y válido.
     */
    static BigDecimal extraer(String nombre) {
        if (nombre == null) return null;
        Matcher m = PATRON_FINAL.matcher(nombre.trim());
        if (!m.find()) return null;
        BigDecimal valor = new BigDecimal(m.group(2).replace(',', '.'));
        if (valor.signum() == 0 || valor.compareTo(MAXIMO) > 0) return null;
        return "-".equals(m.group(1)) ? valor.negate() : valor;
    }
}
