package com.softmyc.tpv.data.printer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** ERR-05: el pedido web solo se da por impreso si la impresora confirma la impresión del ticket. */
public class PedidoWebPrinterTest {

    private static final String TICKET = "Pedido 7\nBocata x2";

    /** Destino falso: guarda lo que se envía y responde como se le indique. */
    private static final class DestinoFalso implements PedidoWebPrinter.Destino {
        final List<String> enviados = new ArrayList<>();
        boolean ok;
        String error;
        RuntimeException lanzar;

        @Override
        public void enviar(String contenido, PedidoWebPrinter.ResultadoImpresion resultado) {
            enviados.add(contenido);
            if (lanzar != null) throw lanzar;
            resultado.onResult(ok, error);
        }
    }

    private static final class Recogida implements PedidoWebPrinter.ResultadoImpresion {
        Boolean ok;
        String error;

        @Override
        public void onResult(boolean ok, String error) {
            this.ok = ok;
            this.error = error;
        }
    }

    @Test
    public void imprimirTicketCocinaAntePoneCabeceraAlTexto() {
        DestinoFalso destino = new DestinoFalso();
        destino.ok = true;

        new PedidoWebPrinter(destino).imprimirTicketCocina(TICKET, new Recogida());

        assertEquals(1, destino.enviados.size());
        assertTrue(destino.enviados.get(0).startsWith("=== TICKET DE COCINA (WEB) ===\n\n"));
        assertTrue(destino.enviados.get(0).contains(TICKET));
    }

    @Test
    public void impresionConfirmadaInformaOk() {
        DestinoFalso destino = new DestinoFalso();
        destino.ok = true;
        Recogida recogida = new Recogida();

        new PedidoWebPrinter(destino).imprimirTicketCocina(TICKET, recogida);

        assertEquals(Boolean.TRUE, recogida.ok);
        assertEquals(null, recogida.error);
    }

    @Test
    public void impresionFallidaInformaErrorSinConfirmar() {
        DestinoFalso destino = new DestinoFalso();
        destino.ok = false;
        destino.error = "Connection refused";
        Recogida recogida = new Recogida();

        new PedidoWebPrinter(destino).imprimirTicketCocina(TICKET, recogida);

        assertEquals(Boolean.FALSE, recogida.ok);
        assertEquals("Connection refused", recogida.error);
    }

    @Test
    public void excepcionAlEnviarSeInformaComoFallo() {
        DestinoFalso destino = new DestinoFalso();
        destino.lanzar = new IllegalStateException("sin impresora configurada");
        Recogida recogida = new Recogida();

        new PedidoWebPrinter(destino).imprimirTicketCocina(TICKET, recogida);

        assertFalse(recogida.ok);
        assertEquals("sin impresora configurada", recogida.error);
    }
}
