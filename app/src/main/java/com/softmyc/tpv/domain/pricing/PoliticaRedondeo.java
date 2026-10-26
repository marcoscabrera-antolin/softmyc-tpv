package com.softmyc.tpv.domain.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Política única de redondeo monetario: los importes (precios unitarios, subtotales y totales)
 * se expresan en céntimos con {@link RoundingMode#HALF_UP}. El precio unitario se redondea antes
 * de multiplicar por la cantidad, de modo que el ticket, la pantalla y el servidor coinciden.
 */
public final class PoliticaRedondeo {

    public static final int DECIMALES_MONETARIOS = 2;
    public static final RoundingMode MODO = RoundingMode.HALF_UP;

    private PoliticaRedondeo() {
    }

    /** Importe en céntimos a partir de un valor exacto. */
    public static BigDecimal aCentimos(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(DECIMALES_MONETARIOS, MODO);
        }
        return valor.setScale(DECIMALES_MONETARIOS, MODO);
    }

    /** Importe en céntimos a partir de un double; usa su representación decimal, no la binaria. */
    public static BigDecimal aCentimos(double valor) {
        return aCentimos(BigDecimal.valueOf(valor));
    }
}
