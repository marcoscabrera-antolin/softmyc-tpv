package com.softmyc.tpv.presentation.viewmodel;

import android.content.Context;

/** Implementación de {@link Textos} respaldada por los recursos de la aplicación. */
final class TextosAndroid implements Textos {
    private final Context contexto;

    TextosAndroid(Context contexto) {
        this.contexto = contexto.getApplicationContext();
    }

    @Override
    public String cadena(int id, Object... args) {
        return contexto.getString(id, args);
    }
}
