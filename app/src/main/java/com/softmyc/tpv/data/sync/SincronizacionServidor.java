package com.softmyc.tpv.data.sync;

import com.softmyc.tpv.R;
import android.content.Context;
import android.util.Log;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.SyncGuard;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.domain.repository.CatalogoTenantRepository;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Orquesta la sincronización con el servidor: primero se envían los cambios de catálogo de la tablet, después se
 * descarga el catálogo (solo si no quedó nada pendiente) y por último se suben las ventas pendientes.
 */
public final class SincronizacionServidor {
    private static final String TAG = "SincronizacionServidor";

    private final Context context;
    private final ExecutorService disco;
    private final CatalogoTenantRepository catalogo;
    private final Runnable alRecargarCatalogo;
    private final Consumer<String> mensajes;

    /**
     * @param alRecargarCatalogo se ejecuta tras aplicar un catálogo descargado, para recargar la pantalla
     * @param mensajes recibe los avisos para el usuario (toast); puede llamarse desde cualquier hilo
     */
    public SincronizacionServidor(Context context, ExecutorService disco, CatalogoTenantRepository catalogo,
                                  Runnable alRecargarCatalogo, Consumer<String> mensajes) {
        this.context = context;
        this.disco = disco;
        this.catalogo = catalogo;
        this.alRecargarCatalogo = alRecargarCatalogo;
        this.mensajes = mensajes;
    }

    public void sincronizarConServidor() {
        String currentTenant = TenantContext.read(SecurePreferencesHelper.getPrinterPrefs(context));
        if (currentTenant == null) {
            Log.w(TAG, "Sincronización omitida: " + TenantContext.NO_TENANT_MESSAGE);
            return;
        }
        // El servidor archiva y sirve los datos del negocio de la sesión: si la base local es de otro, no se sincroniza nada.
        if (!SyncGuard.canUploadFor(Auth.manager(context).current(), currentTenant)) return;
        // A0. Primero se envían los cambios de catálogo hechos en la tablet; si alguno no llega, no se
        // sustituye el catálogo local por el del servidor (se perderían esas ediciones).
        disco.execute(() -> new CatalogOutboxSender(context, currentTenant)
                .enviar((pendientes, descartadas) -> {
                    if (descartadas > 0) {
                        mensajes.accept(context.getResources().getQuantityString(R.plurals.sync_cambios_catalogo_rechazados, descartadas, descartadas));
                    }
                    if (pendientes == 0) descargarCatalogo(currentTenant);
                    else Log.w(TAG, pendientes + " cambio(s) de catálogo sin enviar: no se descarga el catálogo del servidor");
                }));

        // B. Sincronizar ventas locales pendientes (Room -> servidor)
        sincronizarVentasPendientes();
    }

    /** Sube las ventas locales pendientes (Room -> servidor). */
    public void sincronizarVentasPendientes() {
        new VentaUploader(context, disco,
                n -> mensajes.accept(context.getResources().getQuantityString(R.plurals.ventas_rechazadas_no_subidas, n, n)))
                .subirPendientes(null);
    }

    private void descargarCatalogo(String currentTenant) {
        new CatalogoRemotoDescarga(catalogo, disco, alRecargarCatalogo).descargar(context, currentTenant);
    }
}
