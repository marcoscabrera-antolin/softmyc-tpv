package com.softmyc.tpv.data.repo;

import android.content.Context;
import android.util.Log;

import com.softmyc.tpv.data.CsvParser;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.CatalogSync;
import com.softmyc.tpv.data.local.ExtraLocal;
import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.local.SalsaLocal;
import com.softmyc.tpv.data.local.TenantCatalogSeeder;
import com.softmyc.tpv.data.sync.CatalogOutbox;
import com.softmyc.tpv.domain.repository.CatalogoTenantRepository;
import com.softmyc.tpv.domain.repository.ExtraCatalogo;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Implementación Room del catálogo del negocio: única capa que toca AppDatabase para el catálogo. */
public final class CatalogoTenantRepositoryRoom implements CatalogoTenantRepository {

    private static final String TAG = "CatalogoTenantRepo";
    private static final String ARCHIVO_SALSAS = "salsas.csv";
    private static final String CATEGORIA_SALSAS = "Salsas";
    private static final String UNIDAD_POR_DEFECTO = "UD";
    private static final int STOCK_SIN_CONTROL = -1;

    private final Context appContext;

    public CatalogoTenantRepositoryRoom(Context appContext) {
        this.appContext = appContext.getApplicationContext();
    }

    @Override
    public void sembrar(String tenantId) {
        AppDatabase db = AppDatabase.getDatabaseForTenant(appContext, tenantId);
        TenantCatalogSeeder.seedTenantSync(appContext, db, tenantId);
    }

    @Override
    public List<ExtraCatalogo> listarExtras() {
        List<ExtraCatalogo> extras = new ArrayList<>();
        for (ExtraLocal extra : AppDatabase.getDatabase(appContext).extraDao().getAll()) {
            extras.add(new ExtraCatalogo(extra.nombre, extra.precio));
        }
        return extras;
    }

    @Override
    public List<String> listarSalsas() {
        AppDatabase db = AppDatabase.getDatabase(appContext);
        List<SalsaLocal> salsas = db.salsaDao().getAll();
        if (salsas.isEmpty()) {
            salsas = sembrarSalsasDesdeCsv(db);
        }
        List<String> nombres = new ArrayList<>();
        for (SalsaLocal salsa : salsas) {
            if (salsa.nombre != null && !salsa.nombre.trim().isEmpty()) {
                nombres.add(salsa.nombre.trim());
            }
        }
        return nombres;
    }

    @Override
    public boolean aplicarDescargaServidor(String tenantId, List<ProductoCatalogo> productos,
                                           List<ExtraCatalogo> extras, List<String> salsas) {
        CatalogOutbox outbox = new CatalogOutbox(SecurePreferencesHelper.getPrinterPrefs(appContext));
        // Una edición hecha mientras se descargaba debe ganar al catálogo descargado.
        if (!outbox.isEmpty(tenantId)) {
            Log.w(TAG, "Hay cambios de catálogo nuevos sin enviar: se descarta esta descarga");
            return false;
        }
        return CatalogSync.syncFromServer(
                AppDatabase.getDatabase(appContext),
                aProductosLocales(productos),
                aExtrasLocales(extras),
                aSalsasLocales(salsas),
                CatalogSync.pendientesDe(outbox.pending(tenantId)));
    }

    private List<SalsaLocal> sembrarSalsasDesdeCsv(AppDatabase db) {
        try (InputStream is = appContext.getAssets().open(ARCHIVO_SALSAS)) {
            CsvParser.ParseResult resultado = CsvParser.parse(is, CATEGORIA_SALSAS);
            if (!resultado.salsas.isEmpty()) {
                db.salsaDao().insertAll(resultado.salsas);
                return db.salsaDao().getAll();
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "No se pudo sembrar " + ARCHIVO_SALSAS + ": se mantiene la lista vacía", e);
        }
        return new ArrayList<>();
    }

    private static List<ProductoLocal> aProductosLocales(List<ProductoCatalogo> productos) {
        List<ProductoLocal> locales = new ArrayList<>(productos.size());
        for (ProductoCatalogo p : productos) {
            locales.add(new ProductoLocal(p.codigo, p.nombre, p.precio, p.categoria, p.ingredientes));
        }
        return locales;
    }

    private static List<ExtraLocal> aExtrasLocales(List<ExtraCatalogo> extras) {
        List<ExtraLocal> locales = new ArrayList<>(extras.size());
        for (ExtraCatalogo e : extras) {
            locales.add(new ExtraLocal(e.nombre, e.precio));
        }
        return locales;
    }

    private static List<SalsaLocal> aSalsasLocales(List<String> salsas) {
        List<SalsaLocal> locales = new ArrayList<>(salsas.size());
        for (String nombre : salsas) {
            locales.add(new SalsaLocal(nombre));
        }
        return locales;
    }
}
