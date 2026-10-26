package com.softmyc.tpv.data.sync;

import android.content.Context;

import com.softmyc.tpv.data.auth.BackendUrl;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.local.VentaDao;
import com.softmyc.tpv.data.local.VentaLocal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ARQ-10: las marcas de "sincronizada" se refieren al servidor que las recibió. Al cambiar de servidor se vuelven
 * a poner pendientes todas las ventas de todos los negocios locales; la subida es idempotente por uuid.
 */
public final class SyncReset {

    private static final String PREFIJO_BASE = "softmyc_";
    private static final String EXTENSION_BASE = ".db";
    private static final String ARCHIVO_LANUBE = PREFIJO_BASE + "database" + EXTENSION_BASE;

    private SyncReset() { }

    /** ¿La URL nueva apunta a otro servidor que la raíz actual? Una URL vacía conserva el servidor actual. */
    public static boolean cambiaDeServidor(String raizAnterior, String urlNueva) {
        if (urlNueva == null || urlNueva.trim().isEmpty()) return false;
        return !raizAnterior.equalsIgnoreCase(BackendUrl.rootOf(urlNueva));
    }

    /** Negocio al que pertenece un fichero de base de datos, o null si no es una base de negocio. */
    public static String tenantDesdeArchivo(String nombreArchivo) {
        if (ARCHIVO_LANUBE.equals(nombreArchivo)) return TenantContext.LA_NUBE;
        if (!nombreArchivo.startsWith(PREFIJO_BASE) || !nombreArchivo.endsWith(EXTENSION_BASE)) return null;
        String id = nombreArchivo.substring(PREFIJO_BASE.length(), nombreArchivo.length() - EXTENSION_BASE.length());
        return TenantContext.canonicalId(id);
    }

    /** Pone como pendientes las ventas marcadas como sincronizadas. Devuelve cuántas cambió. */
    public static int marcarTodasPendientes(VentaDao dao) {
        int cambiadas = 0;
        for (VentaLocal venta : dao.getAll()) {
            if (!venta.isSynced) continue;
            venta.isSynced = false;
            dao.update(venta);
            cambiadas++;
        }
        return cambiadas;
    }

    /**
     * Aplica el reinicio en todas las bases de negocio del dispositivo. Debe llamarse fuera del hilo principal.
     * Devuelve el número total de ventas que vuelven a estar pendientes.
     */
    public static int marcarTodasPendientesEnTodosLosNegocios(Context context) {
        int total = 0;
        for (String negocio : negociosLocales(context)) {
            total += marcarTodasPendientes(AppDatabase.getDatabaseForTenant(context, negocio).ventaDao());
        }
        return total;
    }

    private static List<String> negociosLocales(Context context) {
        List<String> negocios = new ArrayList<>();
        File carpeta = context.getDatabasePath(ARCHIVO_LANUBE).getParentFile();
        String[] archivos = carpeta != null ? carpeta.list() : null;
        if (archivos == null) return negocios;
        for (String archivo : archivos) {
            String negocio = tenantDesdeArchivo(archivo);
            if (negocio != null && !negocios.contains(negocio)) negocios.add(negocio);
        }
        return negocios;
    }
}
