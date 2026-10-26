package com.softmyc.tpv.data.local.cifrado;

import android.database.Cursor;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;

/**
 * Implementación real de {@link OperacionesBase} sobre SQLCipher. Solo se ejecuta en el dispositivo.
 */
public final class OperacionesSqlCipher implements OperacionesBase {

    /** Contraseña vacía: SQLCipher abre el fichero sin cifrar. */
    private static final String SIN_CLAVE = "";
    private static final String BIBLIOTECA_NATIVA = "sqlcipher";
    private static final String TABLAS_SQL =
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name";
    private static final String ALIAS_EXPORTACION = "cifrada";

    public OperacionesSqlCipher() {
        System.loadLibrary(BIBLIOTECA_NATIVA);
    }

    @Override
    public Instantanea leerEnClaro(File fichero) {
        return leer(fichero, SIN_CLAVE);
    }

    @Override
    public Instantanea leerCifrada(File fichero, String claveHex) {
        return leer(fichero, ClaveBase.textoSqlCipher(claveHex));
    }

    @Override
    public void exportarACifrada(File origen, File destino, String claveHex, int version) {
        SQLiteDatabase db = abrir(origen, SIN_CLAVE);
        try {
            db.execSQL("ATTACH DATABASE ? AS " + ALIAS_EXPORTACION + " KEY ?",
                    new Object[]{destino.getAbsolutePath(), ClaveBase.textoSqlCipher(claveHex)});
            primeraCelda(db, "SELECT sqlcipher_export('" + ALIAS_EXPORTACION + "')");
            db.execSQL("PRAGMA " + ALIAS_EXPORTACION + ".user_version = " + version);
            db.execSQL("DETACH DATABASE " + ALIAS_EXPORTACION);
        } finally {
            db.close();
        }
    }

    private Instantanea leer(File fichero, String clave) {
        SQLiteDatabase db = abrir(fichero, clave);
        try {
            primeraCelda(db, "PRAGMA wal_checkpoint(TRUNCATE)");
            int version = Integer.parseInt(primeraCelda(db, "PRAGMA user_version"));
            boolean integra = "ok".equals(primeraCelda(db, "PRAGMA integrity_check"));
            return new Instantanea(version, contarFilas(db), integra);
        } finally {
            db.close();
        }
    }

    private static SQLiteDatabase abrir(File fichero, String clave) {
        return SQLiteDatabase.openDatabase(fichero.getAbsolutePath(), clave, null,
                SQLiteDatabase.OPEN_READWRITE, (SQLiteDatabaseHook) null);
    }

    private static Map<String, Long> contarFilas(SQLiteDatabase db) {
        Map<String, Long> filas = new TreeMap<>();
        try (Cursor tablas = db.rawQuery(TABLAS_SQL, null)) {
            while (tablas.moveToNext()) {
                String tabla = tablas.getString(0);
                String conteo = primeraCelda(db, "SELECT COUNT(*) FROM " + entreComillas(tabla));
                filas.put(tabla, Long.parseLong(conteo));
            }
        }
        return filas;
    }

    private static String primeraCelda(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static String entreComillas(String identificador) {
        return "\"" + identificador.replace("\"", "\"\"") + "\"";
    }
}
