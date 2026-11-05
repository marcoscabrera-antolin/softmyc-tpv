package com.softmyc.tpv.composicion;

import android.content.Context;

import com.softmyc.tpv.data.repo.EscandalloRepositoryRoom;
import com.softmyc.tpv.domain.repository.EscandalloRepository;

/**
 * Raíz de composición: el único sitio donde se elige la implementación (Room) de cada puerto del dominio.
 * Las pantallas piden aquí lo que necesitan y solo ven interfaces de dominio, así que no importan {@code data}
 * (regla de ArquitecturaCapasTest). Las factorías solo devuelven tipos de {@code domain}.
 */
public final class Dependencias {

    private static volatile EscandalloRepository escandalloParaTests;

    private Dependencias() { }

    public static EscandalloRepository escandallo(Context context) {
        EscandalloRepository sustituto = escandalloParaTests;
        return sustituto != null ? sustituto : new EscandalloRepositoryRoom(context);
    }

    /** Solo tests: sustituye el repositorio del escandallo (null = el real). */
    public static void setEscandalloParaTests(EscandalloRepository repositorio) {
        escandalloParaTests = repositorio;
    }
}
