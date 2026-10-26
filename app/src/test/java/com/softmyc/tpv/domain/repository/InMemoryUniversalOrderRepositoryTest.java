package com.softmyc.tpv.domain.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.model.PaymentMethod;
import com.softmyc.tpv.domain.model.TaxCalculationMode;
import com.softmyc.tpv.domain.model.UniversalOrder;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

/** Repositorio de pedidos en memoria: alta, reemplazo por id, orden de recientes, límites y filtro por sector. */
public class InMemoryUniversalOrderRepositoryTest {

    private InMemoryUniversalOrderRepository repositorio;

    @Before
    public void setUp() {
        repositorio = new InMemoryUniversalOrderRepository();
    }

    @Test
    public void repositorio_nuevo_esta_vacio_y_no_encuentra_nada() {
        assertEquals(0, repositorio.count());
        assertNull(repositorio.findById("ORD-1"));
        assertTrue(repositorio.findRecent(10).isEmpty());
    }

    @Test
    public void save_guarda_el_pedido_y_findById_lo_devuelve_por_su_id() {
        UniversalOrder pedido = pedido("ORD-1", "RESTAURANT");

        repositorio.save(pedido);

        assertSame(pedido, repositorio.findById("ORD-1"));
        assertEquals(1, repositorio.count());
    }

    @Test
    public void save_con_nulo_no_hace_nada() {
        repositorio.save(null);

        assertEquals(0, repositorio.count());
    }

    @Test
    public void save_con_el_mismo_id_reemplaza_el_pedido_anterior() {
        UniversalOrder original = pedido("ORD-1", "RESTAURANT");
        UniversalOrder reemplazo = pedido("ORD-1", "AUTO_REPAIR");

        repositorio.save(original);
        repositorio.save(reemplazo);

        assertEquals(1, repositorio.count());
        assertSame(reemplazo, repositorio.findById("ORD-1"));
    }

    @Test
    public void findRecent_devuelve_primero_los_mas_recientes() {
        repositorio.save(pedido("A", "RESTAURANT"));
        repositorio.save(pedido("B", "RESTAURANT"));
        repositorio.save(pedido("C", "RESTAURANT"));

        List<UniversalOrder> recientes = repositorio.findRecent(0);

        assertEquals("C", recientes.get(0).getId());
        assertEquals("B", recientes.get(1).getId());
        assertEquals("A", recientes.get(2).getId());
    }

    @Test
    public void findRecent_respeta_el_limite_y_con_cero_devuelve_todos() {
        repositorio.save(pedido("A", "RESTAURANT"));
        repositorio.save(pedido("B", "RESTAURANT"));
        repositorio.save(pedido("C", "RESTAURANT"));

        assertEquals(2, repositorio.findRecent(2).size());
        assertEquals(3, repositorio.findRecent(0).size());
        assertEquals(3, repositorio.findRecent(-5).size());
        assertEquals(3, repositorio.findRecent(10).size());
    }

    @Test
    public void findBySector_filtra_sin_distinguir_mayusculas() {
        repositorio.save(pedido("A", "RESTAURANT"));
        repositorio.save(pedido("B", "AUTO_REPAIR"));
        repositorio.save(pedido("C", "restaurant"));

        List<UniversalOrder> restaurante = repositorio.findBySector("Restaurant", 0);

        assertEquals(2, restaurante.size());
        assertEquals("C", restaurante.get(0).getId());
        assertEquals("A", restaurante.get(1).getId());
    }

    @Test
    public void findBySector_respeta_el_limite_y_el_sector_nulo_devuelve_vacio() {
        repositorio.save(pedido("A", "RESTAURANT"));
        repositorio.save(pedido("B", "RESTAURANT"));

        assertEquals(1, repositorio.findBySector("RESTAURANT", 1).size());
        assertTrue(repositorio.findBySector(null, 0).isEmpty());
        assertTrue(repositorio.findBySector("FERRETERIA", 0).isEmpty());
    }

    @Test
    public void clear_vacia_el_repositorio() {
        repositorio.save(pedido("A", "RESTAURANT"));

        repositorio.clear();

        assertEquals(0, repositorio.count());
        assertNull(repositorio.findById("A"));
    }

    private static UniversalOrder pedido(String id, String sector) {
        return new UniversalOrder(id, System.currentTimeMillis(), sector, "Pedido de prueba",
                Collections.emptyList(), PaymentMethod.CARD, TaxCalculationMode.EXCLUSIVE);
    }
}
