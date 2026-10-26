package com.softmyc.tpv.presentation.util;

import android.content.Context;
import android.content.Intent;
import com.softmyc.tpv.presentation.HistorialVentasActivity;

/**
 * Helper de navegación y utilidades para el módulo de Ventas del TPV.
 */
public class VentasNavegacionHelper {

    /**
     * Inicia la actividad del historial de ventas para mostrar los registros al usuario.
     *
     * @param context Contexto de la actividad llamadora.
     */
    public static void navegarAlHistorial(Context context) {
        Intent intent = new Intent(context, HistorialVentasActivity.class);
        context.startActivity(intent);
    }
}
