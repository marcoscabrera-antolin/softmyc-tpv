package com.softmyc.tpv.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.model.TaxRate;

import org.junit.Test;

/**
 * ARQ-14: las heurísticas que deducen estrategia e IVA del nombre de la categoría están centralizadas
 * en CategoriaHeuristicas. Estos tests fijan su comportamiento actual, incluidos sus casos discutibles.
 */
public class CategoriaHeuristicasTest {

    @Test
    public void categoriasDePesoSeReconocenPorPalabraClave() {
        assertTrue(CategoriaHeuristicas.esPeso("Frutas de temporada"));
        assertTrue(CategoriaHeuristicas.esPeso("Venta a GRANEL"));
        assertTrue(CategoriaHeuristicas.esPeso("Verduras"));
        assertTrue(CategoriaHeuristicas.esPeso("Producto al peso"));
        assertFalse(CategoriaHeuristicas.esPeso("Bebidas"));
    }

    @Test
    public void categoriasDeHorasSeReconocenPorPalabraClave() {
        assertTrue(CategoriaHeuristicas.esHoras("Taller mecánico"));
        assertTrue(CategoriaHeuristicas.esHoras("Mano de obra"));
        assertTrue(CategoriaHeuristicas.esHoras("Reparación express"));
        assertFalse(CategoriaHeuristicas.esHoras("Hamburguesas"));
    }

    /** Caso discutible documentado: "Servicio de mesa" cae en horas por la palabra "servicio". */
    @Test
    public void servicioDeMesaSeClasificaComoHorasPorPalabraClave() {
        assertTrue(CategoriaHeuristicas.esHoras("Servicio de mesa"));
    }

    @Test
    public void categoriasDeVolumenSeReconocenPorPalabraClave() {
        assertTrue(CategoriaHeuristicas.esVolumen("Mayorista"));
        assertTrue(CategoriaHeuristicas.esVolumen("Distribución"));
        assertTrue(CategoriaHeuristicas.esVolumen("Compra por volumen"));
        assertFalse(CategoriaHeuristicas.esVolumen("Bebidas"));
    }

    @Test
    public void categoriaNulaONoReconocidaNoActivaNingunaHeuristica() {
        assertFalse(CategoriaHeuristicas.esPeso(null));
        assertFalse(CategoriaHeuristicas.esHoras(null));
        assertFalse(CategoriaHeuristicas.esVolumen(null));
        assertFalse(CategoriaHeuristicas.esPeso("GENERAL"));
    }

    @Test
    public void unidadesDePesoYHorasSeReconocenPorCodigo() {
        assertTrue(CategoriaHeuristicas.esUnidadPeso("KG"));
        assertTrue(CategoriaHeuristicas.esUnidadPeso("g"));
        assertFalse(CategoriaHeuristicas.esUnidadPeso("UD"));
        assertTrue(CategoriaHeuristicas.esUnidadHoras("HORA"));
        assertTrue(CategoriaHeuristicas.esUnidadHoras("h"));
        assertTrue(CategoriaHeuristicas.esUnidadHoras("MIN"));
        assertFalse(CategoriaHeuristicas.esUnidadHoras("KG"));
    }

    @Test
    public void ivaSugeridoPorSectorEsElDocumentado() {
        assertEquals(TaxRate.SUPER_REDUCED_4, CategoriaHeuristicas.IVA_PESO);
        assertEquals(TaxRate.GENERAL_21, CategoriaHeuristicas.IVA_HORAS);
        assertEquals(TaxRate.GENERAL_21, CategoriaHeuristicas.IVA_VOLUMEN);
        assertEquals(TaxRate.REDUCED_10, CategoriaHeuristicas.IVA_DEFECTO);
    }
}
