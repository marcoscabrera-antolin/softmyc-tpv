package com.softmyc.tpv.data.repo;

import android.content.Context;

import com.softmyc.tpv.domain.usecase.ImprimirYGuardarVentaUseCase;

/** Monta el caso de uso de cobro con sus implementaciones Android. Único punto de cableado de data. */
public final class ImpresionVentaFactory {

    private ImpresionVentaFactory() { }

    public static ImprimirYGuardarVentaUseCase crear(Context context) {
        Context app = context.getApplicationContext();
        return new ImprimirYGuardarVentaUseCase(
                new VentaStoreRoom(app),
                new TicketComposerAndroid(app),
                new ItemsSerializerSync(),
                new TicketPrinterAndroid(app),
                new PrinterSettingsPrefs(app),
                System::currentTimeMillis);
    }
}
