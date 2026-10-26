package com.softmyc.tpv.data.repo;

import android.content.Context;

import com.softmyc.tpv.data.printer.TicketGenerator;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.port.TicketComposer;

import java.util.List;
import java.util.Map;

/** Implementación de {@link TicketComposer} con el generador de tickets actual (ajustes del establecimiento). */
public final class TicketComposerAndroid implements TicketComposer {

    private final Context appContext;

    public TicketComposerAndroid(Context appContext) {
        this.appContext = appContext;
    }

    @Override
    public Composicion componer(List<PedidoItem> lineas, String nombreTienda, Map<String, Double> preciosExtras,
                                String metodoPago, int numeroPedido) {
        TicketGenerator.TicketResult r = TicketGenerator.generarTicket(
                appContext, lineas, nombreTienda, preciosExtras, metodoPago, numeroPedido);
        return new Composicion(r.texto, r.total);
    }
}
