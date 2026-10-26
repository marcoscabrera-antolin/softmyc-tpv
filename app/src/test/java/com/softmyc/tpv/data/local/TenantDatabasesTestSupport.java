package com.softmyc.tpv.data.local;

import android.content.Context;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Limpieza entre tests de las bases por negocio. {@link AppDatabase} guarda una instancia por negocio en un mapa
 * estático que sobrevive entre tests de la misma JVM: sin esta limpieza un test recibe una base ya cerrada o con datos
 * de otro test. Cierra las instancias, vacía el mapa y borra los ficheros de esos negocios.
 */
public final class TenantDatabasesTestSupport {

    private static final String CAMPO_INSTANCIAS = "TENANT_INSTANCES";

    private TenantDatabasesTestSupport() { }

    public static void cerrarInstanciasYBorrarFicheros(Context context) {
        Map<String, AppDatabase> instancias = instanciasPorNegocio();
        List<String> negocios = new ArrayList<>(instancias.keySet());
        for (AppDatabase db : instancias.values()) {
            if (db.isOpen()) {
                db.close();
            }
        }
        instancias.clear();
        for (String negocio : negocios) {
            context.deleteDatabase(TenantContext.databaseFileName(negocio));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, AppDatabase> instanciasPorNegocio() {
        try {
            Field campo = AppDatabase.class.getDeclaredField(CAMPO_INSTANCIAS);
            campo.setAccessible(true);
            return (Map<String, AppDatabase>) campo.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("AppDatabase ya no tiene " + CAMPO_INSTANCIAS + ": actualiza el helper de tests", e);
        }
    }
}
