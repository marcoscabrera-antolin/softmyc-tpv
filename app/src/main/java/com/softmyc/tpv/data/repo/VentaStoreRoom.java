package com.softmyc.tpv.data.repo;

import android.content.Context;

import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.VentaLocal;
import com.softmyc.tpv.data.local.VentaStockRegistrar;
import com.softmyc.tpv.domain.port.VentaCobro;
import com.softmyc.tpv.domain.port.VentaStore;

/** Implementación de {@link VentaStore} sobre Room: la venta y su stock se guardan en una transacción. */
public final class VentaStoreRoom implements VentaStore {

    private final Context appContext;

    public VentaStoreRoom(Context appContext) {
        this.appContext = appContext;
    }

    @Override
    public long guardar(VentaCobro cobro) {
        AppDatabase db = AppDatabase.getDatabase(appContext);
        if (cobro.ventaId == null) {
            VentaLocal nueva = new VentaLocal(cobro.ahoraMs, cobro.total, cobro.itemsJson, false, cobro.metodoPago);
            return VentaStockRegistrar.registrar(db, nueva, cobro.lineas, true);
        }
        VentaLocal edicion = new VentaLocal();
        edicion.id = cobro.ventaId;
        edicion.total = cobro.total;
        edicion.itemsJson = cobro.itemsJson;
        edicion.isSynced = false;
        edicion.metodoPago = cobro.metodoPago;
        // Editar una venta no cambia su identidad: se conserva el uuid para que el servidor la actualice, no la duplique.
        VentaLocal existente = db.ventaDao().getById(cobro.ventaId);
        edicion.uuid = VentaLocal.uuidParaEdicion(existente);
        edicion.fecha = VentaLocal.fechaParaEdicion(existente, cobro.ahoraMs);
        return VentaStockRegistrar.registrarEdicion(db, edicion, cobro.lineas);
    }
}
