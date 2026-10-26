package com.softmyc.tpv.presentation.viewmodel;

import androidx.annotation.StringRes;

/** Textos localizados para la lógica de presentación; permite sustituirlos por un fake en tests. */
public interface Textos {
    String cadena(@StringRes int id, Object... args);
}
