package com.softmyc.tpv.domain.escandallo;

import java.util.Collections;
import java.util.List;

/** Resultado de importar un escandallo: cuánto se cargó y qué filas se rechazaron (con el motivo). */
public final class InformeImportacion {
    public final int ingredientes;
    public final int productosConReceta;
    public final int extras;
    public final int gastos;
    public final List<String> rechazadas;

    public InformeImportacion(int ingredientes, int productosConReceta, int extras, int gastos, List<String> rechazadas) {
        this.ingredientes = ingredientes;
        this.productosConReceta = productosConReceta;
        this.extras = extras;
        this.gastos = gastos;
        this.rechazadas = Collections.unmodifiableList(rechazadas);
    }
}
