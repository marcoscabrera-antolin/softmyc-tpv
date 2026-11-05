package com.softmyc.tpv.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.Producto;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BuscadorProductosTest {

    private final List<Producto> carta = Arrays.asList(
            new Producto(1, "Bocadillo de Pollo", 5.0, "Bocadillos", Arrays.asList("Pan", "Pollo", "Lechuga")),
            new Producto(2, "Serranito", 5.5, "Bocadillos", Arrays.asList("Pan", "Lomo", "Jamón", "Pimiento")),
            new Producto(3, "Hamburguesa Crispy", 7.0, "Hamburguesas", Arrays.asList("Pollo crujiente", "Queso")),
            new Producto(12, "Café con leche", 1.5, "Bebidas", null),
            new Producto(21, "Ñoquis", 6.0, "Otros", new ArrayList<>()));

    @Test
    public void texto_vacio_o_nulo_devuelve_todo() {
        assertEquals(5, BuscadorProductos.filtrar(carta, "").size());
        assertEquals(5, BuscadorProductos.filtrar(carta, "   ").size());
        assertEquals(5, BuscadorProductos.filtrar(carta, null).size());
    }

    @Test
    public void busca_por_nombre_sin_distinguir_mayusculas() {
        assertEquals(Arrays.asList(2), codigos(BuscadorProductos.filtrar(carta, "SERRAN")));
    }

    @Test
    public void busca_por_ingrediente() {
        // "pollo" está en el nombre del 1 y en los ingredientes del 1 y del 3.
        assertEquals(Arrays.asList(1, 3), codigos(BuscadorProductos.filtrar(carta, "pollo")));
        assertEquals(Arrays.asList(2), codigos(BuscadorProductos.filtrar(carta, "pimiento")));
    }

    @Test
    public void ignora_acentos_en_ambos_sentidos() {
        assertEquals(Arrays.asList(2), codigos(BuscadorProductos.filtrar(carta, "jamon")));
        assertEquals(Arrays.asList(12), codigos(BuscadorProductos.filtrar(carta, "cafe")));
        assertEquals(Arrays.asList(12), codigos(BuscadorProductos.filtrar(carta, "CAFÉ")));
        assertEquals(Arrays.asList(21), codigos(BuscadorProductos.filtrar(carta, "noquis")));
    }

    @Test
    public void varias_palabras_deben_aparecer_todas() {
        assertEquals(Arrays.asList(3), codigos(BuscadorProductos.filtrar(carta, "pollo queso")));
        assertTrue(BuscadorProductos.filtrar(carta, "pollo jamon").isEmpty());
    }

    @Test
    public void un_numero_busca_por_codigo_exacto() {
        assertEquals(Arrays.asList(12), codigos(BuscadorProductos.filtrar(carta, "12")));
        assertEquals(Arrays.asList(1), codigos(BuscadorProductos.filtrar(carta, " 1 ")));
        assertTrue(BuscadorProductos.filtrar(carta, "99").isEmpty());
        assertTrue(BuscadorProductos.filtrar(carta, "99999999999999").isEmpty());
    }

    @Test
    public void lista_nula_devuelve_lista_vacia_y_productos_sin_nombre_no_fallan() {
        assertTrue(BuscadorProductos.filtrar(null, "pollo").isEmpty());
        List<Producto> raros = Arrays.asList(new Producto(5, null, 1.0, null, null));
        assertTrue(BuscadorProductos.filtrar(raros, "pollo").isEmpty());
        assertEquals(1, BuscadorProductos.filtrar(raros, "5").size());
    }

    private static List<Integer> codigos(List<Producto> productos) {
        List<Integer> codigos = new ArrayList<>();
        for (Producto p : productos) codigos.add(p.getCodigo());
        return codigos;
    }
}
