package com.softmyc.tpv.domain.usecase;

import com.softmyc.tpv.domain.escandallo.ConsumoExtra;
import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.LineaConsumo;
import com.softmyc.tpv.domain.escandallo.LineaReceta;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.escandallo.UnidadIngrediente;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Escandallo pequeño con los datos reales de La Nube: el bocadillo Nube (1) = pollo 333,33 g + jamón 71,25 g +
 * salsa ("Salsa Nube") + pan = 3,50 € de ingredientes, y 1,69 € de gastos añadidos. El extra "Queso" gasta 82 g.
 */
final class EscandalloDePrueba {
    static final long POLLO = 1, JAMON = 2, SALSA = 3, PAN = 4, QUESO = 5;

    static Recetario nube(Double stockPollo) {
        List<Ingrediente> ing = Arrays.asList(
                new Ingrediente(POLLO, "Pollo", UnidadIngrediente.G, stockPollo, 6.99),
                new Ingrediente(JAMON, "Jamón", UnidadIngrediente.G, 1000.0, 8.0),
                new Ingrediente(SALSA, "Salsa", UnidadIngrediente.G, null, 1.66),
                new Ingrediente(PAN, "Pan", UnidadIngrediente.UD, 20.0, 0.35),
                new Ingrediente(QUESO, "Queso", UnidadIngrediente.G, 500.0, 5.0));
        List<LineaReceta> receta = Arrays.asList(
                new LineaReceta(1, POLLO, 2.33 / 6.99 * 1000, "Pollo"),
                new LineaReceta(1, JAMON, 71.25, "Jamón"),
                new LineaReceta(1, SALSA, 0.25 / 1.66 * 1000, "Salsa Nube"),
                new LineaReceta(1, PAN, 1, null));
        Map<Integer, Double> gastos = new HashMap<>();
        gastos.put(1, 1.69);
        return new Recetario(ing, receta, Collections.singletonList(new ConsumoExtra("Queso", QUESO, 82)), gastos);
    }

    static LineaConsumo linea(int codigo, int cantidad, List<String> quitados, List<String> extras) {
        return new LineaConsumo(codigo, cantidad, "UD", quitados, extras);
    }

    private EscandalloDePrueba() { }
}
