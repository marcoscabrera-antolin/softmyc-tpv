package com.softmyc.tpv.data.repo;

import android.content.Context;

import com.softmyc.tpv.data.printer.PrinterHelper;
import com.softmyc.tpv.domain.port.AjustesImpresora;
import com.softmyc.tpv.domain.port.TicketPrinter;

/** Implementación de {@link TicketPrinter} sobre la impresora térmica de red (socket RAW). */
public final class TicketPrinterAndroid implements TicketPrinter {

    private final Context appContext;

    public TicketPrinterAndroid(Context appContext) {
        this.appContext = appContext;
    }

    @Override
    public void imprimir(String ip, AjustesImpresora ajustes, String texto, ResultadoImpresion resultado) {
        new PrinterHelper(ip, ajustes.puerto, ajustes.fontStyle)
                .print(appContext, texto, resultado::onResultado);
    }
}
