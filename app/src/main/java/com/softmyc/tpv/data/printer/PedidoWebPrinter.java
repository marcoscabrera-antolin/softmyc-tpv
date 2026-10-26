package com.softmyc.tpv.data.printer;

import android.content.Context;
import android.content.SharedPreferences;

import com.softmyc.tpv.domain.port.AjustesImpresora;
import com.softmyc.tpv.data.SecurePreferencesHelper;

/**
 * Imprime el ticket de cocina de un pedido web en la impresora del dispositivo. Informa del resultado real: quien lo
 * llama solo debe dar el pedido por impreso cuando {@code ok} es true (ERR-05).
 */
public final class PedidoWebPrinter {

    private static final String CABECERA = "=== TICKET DE COCINA (WEB) ===\n\n";
    private static final String PIE = "\n\n";
    private static final String IP_POR_DEFECTO = "192.168.0.20";
    private static final int TAMANIO_LETRA_POR_DEFECTO = 1;

    /** Resultado de la impresión; puede llegar desde el hilo de la impresora. */
    public interface ResultadoImpresion {
        void onResult(boolean ok, String error);
    }

    /** Canal por el que se envía el texto a la impresora. */
    public interface Destino {
        void enviar(String contenido, ResultadoImpresion resultado);
    }

    private final Destino destino;

    public PedidoWebPrinter(Destino destino) {
        this.destino = destino;
    }

    /** Impresora configurada en Ajustes, con el logo del negocio como el resto de tickets. */
    public static PedidoWebPrinter delDispositivo(Context context) {
        return new PedidoWebPrinter((contenido, resultado) -> {
            SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(context);
            String ip = prefs.getString("impresora_ip", IP_POR_DEFECTO);
            int puerto = prefs.getInt("impresora_puerto", AjustesImpresora.PUERTO_POR_DEFECTO);
            int tamanioLetra = prefs.getInt("tamanio_letra", TAMANIO_LETRA_POR_DEFECTO);
            new PrinterHelper(ip, puerto, tamanioLetra).print(context, contenido, resultado::onResult);
        });
    }

    /** Envía el ticket; cualquier fallo al enviarlo se comunica como impresión no confirmada. */
    public void imprimirTicketCocina(String texto, ResultadoImpresion resultado) {
        try {
            destino.enviar(CABECERA + texto + PIE, resultado);
        } catch (RuntimeException e) {
            resultado.onResult(false, e.getMessage());
        }
    }
}
