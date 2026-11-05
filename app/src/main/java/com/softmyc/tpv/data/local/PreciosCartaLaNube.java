package com.softmyc.tpv.data.local;

import android.content.Context;
import android.util.Log;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.sync.CatalogOutbox;

/**
 * Precios de carta de La Nube según el documento de costes (noviembre de 2026): coste total + 40 %, redondeado
 * a 0,25 €, o el precio que ya tenía si superaba ese margen.
 *
 * Las instalaciones nuevas ya los traen en la carta (assets). En las existentes se aplican una vez: solo a los
 * productos que siguen con el precio anterior, para no pisar un precio editado a mano. Cada producto con precio
 * nuevo se encola para el servidor (igual que una edición en Gestión de precios); así la descarga del catálogo
 * no vuelve a traer el precio antiguo.
 */
final class PreciosCartaLaNube {

    private static final String TAG = "PreciosCartaLaNube";

    /** {código, precio anterior, precio nuevo}. */
    static final double[][] PRECIOS = {
            {1, 4.00, 7.25},
            {2, 4.50, 7.75},
            {3, 4.00, 6.25},
            {4, 4.70, 6.50},
            {5, 4.50, 6.75},
            {6, 5.50, 8.00},
            {7, 4.50, 7.00},
            {8, 4.50, 5.50},
            {10, 4.70, 7.25},
            {11, 4.50, 5.50},
            {12, 4.00, 5.00},
            {13, 4.00, 6.00},
            {14, 2.80, 3.50},
            {15, 4.50, 6.00},
            {16, 4.00, 7.00},
            {17, 2.80, 3.50},
            {18, 6.00, 7.50},
            {21, 4.50, 8.25},
            {22, 4.00, 4.50},
            {23, 4.50, 5.00},
            {25, 6.00, 8.25},
            {26, 4.00, 5.75},
            {27, 4.50, 6.25},
            {28, 5.50, 7.00},
            {29, 6.00, 7.50},
            {30, 5.50, 7.75},
            {31, 5.50, 7.50},
            {32, 4.50, 6.25},
            {33, 5.00, 6.75},
            {34, 4.50, 7.00},
            {35, 5.00, 8.00},
            {36, 4.50, 7.50},
            {38, 7.00, 9.00},
            {50, 1.70, 1.70},
            {51, 3.00, 3.00},
            {52, 3.00, 3.00},
            {53, 2.00, 2.50},
            {54, 3.80, 4.50},
            {55, 4.80, 5.50},
            {56, 7.00, 8.00},
            {57, 4.50, 6.00},
            {60, 2.50, 2.75},
    };

    private PreciosCartaLaNube() { }

    /** @return cuántos productos han quedado con el precio nuevo y se han encolado para el servidor. */
    static int aplicar(Context context, AppDatabase db, String tenant) {
        int actualizados = 0;
        CatalogOutbox cola = context != null ? new CatalogOutbox(SecurePreferencesHelper.getPrinterPrefs(context)) : null;
        for (double[] p : PRECIOS) {
            int codigo = (int) p[0];
            boolean cambia = db.productoDao().actualizarPrecioSiEs(codigo, p[1], p[2]) > 0;
            ProductoLocal actual = db.productoDao().getByCodigo(codigo);
            boolean yaNuevo = actual != null && Math.abs(actual.precio - p[2]) < 0.001;
            if (cambia || yaNuevo) {
                actualizados++;
                if (cola != null) cola.add(tenant, CatalogOutbox.UPSERT, codigo);
            }
        }
        Log.i(TAG, actualizados + " precios de carta al día");
        return actualizados;
    }
}
