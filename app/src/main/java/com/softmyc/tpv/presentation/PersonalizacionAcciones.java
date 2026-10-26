package com.softmyc.tpv.presentation;

import android.content.Context;

/** Lo que los modos de personalización necesitan del diálogo que los aloja. */
interface PersonalizacionAcciones {
    /** Contexto actual del diálogo, o null si ya no está adjunto. */
    Context contexto();

    /** Recalcula la cantidad y el precio mostrados. */
    void actualizarPrecio();
}
