package com.softmyc.tpv.data.local.cifrado;

import android.content.Context;

import androidx.sqlite.db.SupportSQLiteOpenHelper;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Prepara el cifrado de una base Room: crea o recupera la clave, migra una base en claro si hace falta y
 * devuelve la fábrica SQLCipher con la que Room abre el fichero.
 */
public final class GestorBaseCifrada {

    /** Proveedor de producción usado por {@code AppDatabase}. */
    public static final ProveedorCifrado PREDETERMINADO = GestorBaseCifrada::preparar;

    private final ClaveBase.Almacen almacen;
    private final MigradorBaseCifrada migrador;
    private final IntFunction<byte[]> fuenteAleatoria;
    private final Function<byte[], SupportSQLiteOpenHelper.Factory> fabricaFactoria;

    public GestorBaseCifrada(ClaveBase.Almacen almacen,
                             OperacionesBase operaciones,
                             IntFunction<byte[]> fuenteAleatoria,
                             Function<byte[], SupportSQLiteOpenHelper.Factory> fabricaFactoria) {
        this.almacen = almacen;
        this.migrador = new MigradorBaseCifrada(operaciones);
        this.fuenteAleatoria = fuenteAleatoria;
        this.fabricaFactoria = fabricaFactoria;
    }

    public static SupportSQLiteOpenHelper.Factory preparar(Context context, File fichero) {
        GestorBaseCifrada gestor = new GestorBaseCifrada(AlmacenClaveSegura.de(context),
                new OperacionesSqlCipher(), ClaveBase.FUENTE_ALEATORIA, SupportOpenHelperFactory::new);
        return gestor.abrir(fichero);
    }

    /** Prepara el fichero de una base y devuelve la fábrica con la que Room debe abrirlo. */
    public SupportSQLiteOpenHelper.Factory abrir(File fichero) {
        EstadoBase.Estado estado = estadoDe(fichero);
        String clave = ClaveBase.leer(almacen);
        switch (PlanApertura.decidir(estado, clave != null)) {
            case CLAVE_AUSENTE:
                throw new BaseCifradaException("La base " + fichero.getName() + " está cifrada pero no hay clave "
                        + "guardada: no se crea una nueva para no perder sus datos");
            case MIGRAR_A_CIFRADA:
                clave = claveOCrear(clave);
                migrador.migrar(fichero, clave);
                break;
            case ABRIR_CIFRADA:
                migrador.cerrarMigracionPendiente(fichero, clave);
                break;
            case CREAR_CIFRADA:
            default:
                clave = claveOCrear(clave);
                migrador.descartarRestos(fichero);
                break;
        }
        return fabricaFactoria.apply(ClaveBase.passphrase(clave));
    }

    private String claveOCrear(String clave) {
        return clave != null ? clave : ClaveBase.crear(almacen, fuenteAleatoria);
    }

    private static EstadoBase.Estado estadoDe(File fichero) {
        try {
            return EstadoBase.estadoDe(fichero);
        } catch (IOException e) {
            throw new BaseCifradaException("No se pudo leer la cabecera de la base " + fichero.getName(), e);
        }
    }
}
