package com.softmyc.tpv.data.repo;

import android.content.Context;

import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.VentaLocal;
import com.softmyc.tpv.domain.repository.VentaRepository;
import com.softmyc.tpv.domain.repository.VentaResumen;

import java.util.ArrayList;
import java.util.List;

/** Implementación de {@link VentaRepository} sobre la base de datos del negocio activo. */
public final class VentaRepositoryRoom implements VentaRepository {

    private final Context appContext;

    public VentaRepositoryRoom(Context appContext) {
        this.appContext = appContext;
    }

    @Override
    public List<VentaResumen> getDesde(long inicioMs) {
        List<VentaResumen> resultado = new ArrayList<>();
        for (VentaLocal v : AppDatabase.getDatabase(appContext).ventaDao().getDesde(inicioMs)) {
            resultado.add(aModelo(v));
        }
        return resultado;
    }

    @Override
    public List<VentaResumen> getRecientes(int limite) {
        List<VentaResumen> resultado = new ArrayList<>();
        for (VentaLocal v : AppDatabase.getDatabase(appContext).ventaDao().getRecientes(limite)) {
            resultado.add(aModelo(v));
        }
        return resultado;
    }

    @Override
    public VentaResumen getById(int id) {
        VentaLocal v = AppDatabase.getDatabase(appContext).ventaDao().getById(id);
        return v != null ? aModelo(v) : null;
    }

    private static VentaResumen aModelo(VentaLocal v) {
        return new VentaResumen(v.id, v.fecha, v.total, v.metodoPago, v.itemsJson, v.isSynced);
    }
}
