package com.softmyc.tpv.domain.port;

/** Ajustes de impresión del ticket: estilo de letra (2 = letra pequeña, 24 columnas) y puerto RAW. */
public final class AjustesImpresora {

    /** Puerto RAW de las impresoras térmicas de red; el ajuste `impresora_puerto` lo sustituye. */
    public static final int PUERTO_POR_DEFECTO = 9100;
    private static final int ESTILO_LETRA_PEQUENA = 2;
    private static final int COLUMNAS_LETRA_PEQUENA = 24;
    private static final int COLUMNAS_LETRA_NORMAL = 48;

    public final int fontStyle;
    public final int puerto;

    private AjustesImpresora(int fontStyle, int puerto) {
        this.fontStyle = fontStyle;
        this.puerto = puerto;
    }

    /** Crea los ajustes; un puerto fuera de rango se sustituye por {@link #PUERTO_POR_DEFECTO}. */
    public static AjustesImpresora of(int fontStyle, int puerto) {
        boolean puertoValido = ValidadorImpresora.esPuertoValido(puerto);
        return new AjustesImpresora(fontStyle, puertoValido ? puerto : PUERTO_POR_DEFECTO);
    }

    /** Columnas del ticket según el estilo de letra. */
    public int columnas() {
        return fontStyle == ESTILO_LETRA_PEQUENA ? COLUMNAS_LETRA_PEQUENA : COLUMNAS_LETRA_NORMAL;
    }
}
