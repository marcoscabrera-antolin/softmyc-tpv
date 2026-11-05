package com.softmyc.tpv.domain.usecase;

import static com.softmyc.tpv.domain.usecase.EscandalloDePrueba.JAMON;
import static com.softmyc.tpv.domain.usecase.EscandalloDePrueba.PAN;
import static com.softmyc.tpv.domain.usecase.EscandalloDePrueba.POLLO;
import static com.softmyc.tpv.domain.usecase.EscandalloDePrueba.QUESO;
import static com.softmyc.tpv.domain.usecase.EscandalloDePrueba.SALSA;
import static com.softmyc.tpv.domain.usecase.EscandalloDePrueba.linea;
import static com.softmyc.tpv.domain.usecase.EscandalloDePrueba.nube;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.LineaConsumo;
import com.softmyc.tpv.domain.escandallo.LineaReceta;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.escandallo.UnidadIngrediente;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsumoIngredientesTest {

    private final Recetario r = nube(1000.0);

    @Test
    public void cada_unidad_gasta_la_receta_completa() {
        Map<Long, Double> gasto = ConsumoIngredientes.calcular(Collections.singletonList(linea(1, 2, null, null)), r);

        assertEquals(666.667, gasto.get(POLLO), 1e-9);
        assertEquals(142.5, gasto.get(JAMON), 1e-9);
        assertEquals(2.0, gasto.get(PAN), 1e-9);
        assertEquals(4, gasto.size());
    }

    @Test
    public void un_quitado_no_se_gasta_casando_por_texto_de_carta_o_por_ingrediente_sin_acentos() {
        Map<Long, Double> sinSalsa = ConsumoIngredientes.calcular(
                Collections.singletonList(linea(1, 1, Collections.singletonList("SALSA NUBE"), null)), r);
        Map<Long, Double> sinJamon = ConsumoIngredientes.calcular(
                Collections.singletonList(linea(1, 1, Collections.singletonList("jamon"), null)), r);

        assertFalse(sinSalsa.containsKey(SALSA));
        assertFalse(sinJamon.containsKey(JAMON));
        assertTrue(sinJamon.containsKey(POLLO));
    }

    @Test
    public void un_quitado_que_no_casa_no_cambia_nada() {
        Map<Long, Double> base = ConsumoIngredientes.calcular(Collections.singletonList(linea(1, 1, null, null)), r);
        Map<Long, Double> conQuitado = ConsumoIngredientes.calcular(
                Collections.singletonList(linea(1, 1, Collections.singletonList("Cebolla"), null)), r);

        assertEquals(base, conQuitado);
    }

    @Test
    public void cada_extra_gasta_su_racion_por_unidad_y_repetido_cuenta_dos_veces() {
        Map<Long, Double> uno = ConsumoIngredientes.calcular(
                Collections.singletonList(linea(1, 3, null, Collections.singletonList("queso"))), r);
        Map<Long, Double> doble = ConsumoIngredientes.calcular(
                Collections.singletonList(linea(1, 1, null, Arrays.asList("Queso", "Queso"))), r);

        assertEquals(246.0, uno.get(QUESO), 1e-9);
        assertEquals(164.0, doble.get(QUESO), 1e-9);
    }

    @Test
    public void extra_sin_enlazar_no_gasta_nada() {
        Map<Long, Double> gasto = ConsumoIngredientes.calcular(
                Collections.singletonList(linea(1, 1, null, Collections.singletonList("Aguacate"))), r);

        assertFalse(gasto.containsKey(QUESO));
    }

    @Test
    public void lineas_al_peso_sin_codigo_o_sin_cantidad_no_gastan_ingredientes() {
        List<LineaConsumo> lineas = Arrays.asList(
                new LineaConsumo(1, 2, "KG", null, null),
                new LineaConsumo(1, 2, "HORA", null, null),
                new LineaConsumo(0, 2, "UD", null, null),
                new LineaConsumo(1, 0, "UD", null, null),
                new LineaConsumo(7, 1, "UD", null, null));

        assertTrue(ConsumoIngredientes.calcular(lineas, r).isEmpty());
        assertTrue(ConsumoIngredientes.calcular(null, r).isEmpty());
    }

    @Test
    public void varias_lineas_del_mismo_producto_se_suman() {
        Map<Long, Double> gasto = ConsumoIngredientes.calcular(
                Arrays.asList(linea(1, 1, null, null), linea(1, 1, Collections.singletonList("Pollo"), null)), r);

        assertEquals(333.333, gasto.get(POLLO), 1e-9);
        assertEquals(2.0, gasto.get(PAN), 1e-9);
    }

    @Test
    public void diferencia_da_solo_lo_que_cambia_y_omite_ceros() {
        Map<Long, Double> antes = new HashMap<>();
        antes.put(POLLO, 333.333);
        antes.put(PAN, 1.0);
        antes.put(QUESO, 82.0);
        Map<Long, Double> despues = new HashMap<>();
        despues.put(POLLO, 666.666);
        despues.put(PAN, 1.0);
        despues.put(JAMON, 71.25);

        Map<Long, Double> d = ConsumoIngredientes.diferencia(despues, antes);

        assertEquals(333.333, d.get(POLLO), 1e-9);
        assertEquals(71.25, d.get(JAMON), 1e-9);
        assertEquals(-82.0, d.get(QUESO), 1e-9);
        assertFalse(d.containsKey(PAN));
    }

    @Test
    public void coste_del_bocadillo_coincide_con_el_documento_de_costes() {
        assertEquals(3.50, ConsumoIngredientes.costeUnitario(1, r), 0.005);
        assertEquals(7.00, ConsumoIngredientes.coste(linea(1, 2, null, null), r), 0.005);
    }

    @Test
    public void el_coste_resta_los_quitados_y_suma_los_extras() {
        double sinJamon = ConsumoIngredientes.coste(linea(1, 1, Collections.singletonList("Jamón"), null), r);
        double conQueso = ConsumoIngredientes.coste(linea(1, 1, null, Collections.singletonList("Queso")), r);

        assertEquals(3.50 - 0.57, sinJamon, 0.005);
        assertEquals(3.50 + 0.41, conQueso, 0.005);
    }

    @Test
    public void coste_desconocido_sin_receta_al_peso_o_con_ingrediente_sin_precio() {
        assertNull(ConsumoIngredientes.costeUnitario(99, r));
        assertNull(ConsumoIngredientes.coste(new LineaConsumo(1, 1, "KG", null, null), r));
        assertNull(ConsumoIngredientes.coste(null, r));
        Recetario sinPrecio = new Recetario(
                Collections.singletonList(new Ingrediente(1, "Roquefort", UnidadIngrediente.G, null, null)),
                Collections.singletonList(new LineaReceta(9, 1, 30, "Roquefort")), Collections.emptyList(), new HashMap<>());
        assertNull(ConsumoIngredientes.costeUnitario(9, sinPrecio));
    }

    @Test
    public void gastos_anadidos_por_unidades_o_null_si_no_estan_definidos() {
        assertEquals(3.38, ConsumoIngredientes.gastosAnadidos(linea(1, 2, null, null), r), 1e-9);
        assertNull(ConsumoIngredientes.gastosAnadidos(linea(99, 1, null, null), r));
        assertNull(ConsumoIngredientes.gastosAnadidos(new LineaConsumo(1, 1, "KG", null, null), r));
    }

    @Test
    public void unidades_de_ingrediente_calculan_el_coste_por_kilo_o_por_pieza() {
        assertEquals(2.33, UnidadIngrediente.G.coste(333.333, 6.99), 0.001);
        assertEquals(0.70, UnidadIngrediente.UD.coste(2, 0.35), 1e-9);
        assertEquals(UnidadIngrediente.ML, UnidadIngrediente.desde("ml"));
        assertEquals(UnidadIngrediente.ML, UnidadIngrediente.desde("litros"));
        assertEquals(UnidadIngrediente.UD, UnidadIngrediente.desde("ud"));
        assertEquals(UnidadIngrediente.UD, UnidadIngrediente.desde("Ración"));
        assertEquals(UnidadIngrediente.G, UnidadIngrediente.desde("gramos"));
        assertEquals(UnidadIngrediente.G, UnidadIngrediente.desde(null));
    }

    @Test
    public void el_recetario_vacio_no_tiene_nada() {
        assertFalse(Recetario.VACIO.tieneReceta(1));
        assertTrue(Recetario.VACIO.receta(1).isEmpty());
        assertTrue(Recetario.VACIO.consumoExtra("queso").isEmpty());
        assertNull(Recetario.VACIO.gastosAnadidos(1));
    }

    @Test
    public void ingrediente_muestra_el_stock_negativo_como_cero_y_sabe_si_se_controla() {
        Ingrediente descuadrado = new Ingrediente(1, "Pan", UnidadIngrediente.UD, -3.0, 0.35);
        assertEquals(0.0, descuadrado.stockVisible(), 1e-9);
        assertTrue(descuadrado.controlaStock());
        assertFalse(descuadrado.conStock(null).controlaStock());
        assertEquals(0.0, descuadrado.conStock(null).stockVisible(), 1e-9);
        assertNull(new Ingrediente(2, "X", null, null, null).coste(10));
        assertEquals(UnidadIngrediente.G, new Ingrediente(2, "X", null, null, null).unidad);
    }
}
