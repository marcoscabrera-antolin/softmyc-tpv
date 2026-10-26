package com.softmyc.tpv.data.local.cifrado;

import android.content.Context;

import androidx.sqlite.db.SupportSQLiteOpenHelper;

import java.io.File;

/**
 * Punto de inyección del cifrado de las bases Room. Devuelve la fábrica de helpers para un fichero, o null para
 * abrirlo en claro (solo en tests JVM, donde las libs nativas de SQLCipher no cargan).
 */
@FunctionalInterface
public interface ProveedorCifrado {

    SupportSQLiteOpenHelper.Factory preparar(Context context, File fichero);
}
