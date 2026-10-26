package com.softmyc.tpv.domain.port;

import java.util.regex.Pattern;

/** Validación de la dirección y el puerto de la impresora de red (IPv4 o hostname). */
public final class ValidadorImpresora {

    /** Valor devuelto por {@link #puertoValido(String)} cuando el texto no es un puerto válido. */
    public static final int PUERTO_INVALIDO = -1;

    private static final int PUERTO_MINIMO = 1;
    private static final int PUERTO_MAXIMO = 65535;
    private static final int LONGITUD_MAXIMA_HOSTNAME = 253;
    private static final Pattern IPV4 = Pattern.compile(
            "((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    private static final Pattern ETIQUETA_HOSTNAME = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    private static final Pattern SOLO_DIGITOS = Pattern.compile("\\d+");

    private ValidadorImpresora() {
    }

    /** Un puerto TCP está en el rango 1..65535. */
    public static boolean esPuertoValido(int puerto) {
        return puerto >= PUERTO_MINIMO && puerto <= PUERTO_MAXIMO;
    }

    /** Puerto TCP entre 1 y 65535 a partir de texto, o {@link #PUERTO_INVALIDO} si no lo es. */
    public static int puertoValido(String texto) {
        try {
            int puerto = Integer.parseInt(texto);
            return esPuertoValido(puerto) ? puerto : PUERTO_INVALIDO;
        } catch (NumberFormatException e) {
            return PUERTO_INVALIDO;
        }
    }

    /**
     * Acepta una IPv4 con octetos 0..255 o un hostname RFC 1123 (etiquetas alfanuméricas
     * con guiones internos, no vacías, la última no numérica). Rechaza espacios, esquemas y puertos.
     */
    public static boolean esDireccionValida(String direccion) {
        if (direccion == null || direccion.isEmpty() || direccion.length() > LONGITUD_MAXIMA_HOSTNAME) {
            return false;
        }
        if (IPV4.matcher(direccion).matches()) {
            return true;
        }
        return esHostnameValido(direccion);
    }

    private static boolean esHostnameValido(String hostname) {
        String[] etiquetas = hostname.split("\\.", -1);
        for (String etiqueta : etiquetas) {
            if (!ETIQUETA_HOSTNAME.matcher(etiqueta).matches()) {
                return false;
            }
        }
        return !SOLO_DIGITOS.matcher(etiquetas[etiquetas.length - 1]).matches();
    }
}
