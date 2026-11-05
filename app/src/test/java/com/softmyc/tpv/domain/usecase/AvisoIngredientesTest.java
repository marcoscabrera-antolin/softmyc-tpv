package com.softmyc.tpv.domain.usecase;

import static com.softmyc.tpv.domain.usecase.EscandalloDePrueba.linea;
import static com.softmyc.tpv.domain.usecase.EscandalloDePrueba.nube;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.escandallo.Recetario;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AvisoIngredientesTest {

    @Test
    public void avisa_solo_de_los_ingredientes_controlados_que_no_llegan() {
        Recetario r = nube(500.0);   // pollo para 1 bocadillo, no para 2; salsa sin control
        Map<Long, Double> necesario = ConsumoIngredientes.calcular(Collections.singletonList(linea(1, 2, null, null)), r);

        List<AvisoIngredientes.Falta> faltas = AvisoIngredientes.evaluar(necesario, r.ingredientes());

        assertEquals(1, faltas.size());
        assertEquals("Pollo", faltas.get(0).nombre);
        assertEquals(500.0, faltas.get(0).disponible, 1e-9);
        assertEquals(666.667, faltas.get(0).necesario, 1e-9);
        assertEquals("g", faltas.get(0).unidad);
        assertTrue(faltas.get(0).mensaje().startsWith("Pollo: quedan 500 g, se necesitan "));
    }

    @Test
    public void stock_negativo_cuenta_como_cero() {
        Recetario r = nube(-50.0);
        Map<Long, Double> necesario = ConsumoIngredientes.calcular(Collections.singletonList(linea(1, 1, null, null)), r);

        assertEquals(0.0, AvisoIngredientes.evaluar(necesario, r.ingredientes()).get(0).disponible, 1e-9);
    }

    @Test
    public void con_stock_suficiente_o_sin_datos_no_avisa() {
        Recetario r = nube(5000.0);
        Map<Long, Double> necesario = ConsumoIngredientes.calcular(Collections.singletonList(linea(1, 1, null, null)), r);

        assertTrue(AvisoIngredientes.evaluar(necesario, r.ingredientes()).isEmpty());
        assertTrue(AvisoIngredientes.evaluar(null, r.ingredientes()).isEmpty());
        assertEquals("", AvisoIngredientes.resumen(Collections.emptyList(), 2));
        assertEquals("", AvisoIngredientes.resumen(null, 2));
    }

    @Test
    public void el_resumen_muestra_como_mucho_n_y_cuantos_mas() {
        Recetario r = nube(0.0);
        Map<Long, Double> necesario = ConsumoIngredientes.calcular(
                Collections.singletonList(linea(1, 100, null, Arrays.asList("Queso"))), r);
        List<AvisoIngredientes.Falta> faltas = AvisoIngredientes.evaluar(necesario, r.ingredientes());

        String texto = AvisoIngredientes.resumen(faltas, 2);

        assertEquals(4, faltas.size());   // pollo, jamón, pan y queso
        assertEquals(3, texto.split("\n").length);
        assertTrue(texto.endsWith("y 2 más"));
    }

    @Test
    public void formatea_cantidades_enteras_sin_decimales() {
        assertEquals("120", AvisoIngredientes.cantidad(120.0));
        assertEquals(String.format(java.util.Locale.getDefault(), "%.1f", 12.5), AvisoIngredientes.cantidad(12.5));
    }
}
