package com.softmyc.tpv.presentation.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.domain.escandallo.ConsumoExtra;
import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.LineaReceta;
import com.softmyc.tpv.domain.escandallo.UnidadIngrediente;
import com.softmyc.tpv.presentation.RepositorioEscandalloFalso;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EscandalloViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantaneo = new InstantTaskExecutorRule();

    private RepositorioEscandalloFalso repo;
    private EscandalloViewModel vm;

    @Before
    public void setUp() {
        repo = RepositorioEscandalloFalso.conNube();
        vm = new EscandalloViewModel(ApplicationProvider.<Application>getApplicationContext(), repo, Runnable::run);
    }

    @Test
    public void cargar_publica_ingredientes_productos_y_diagnostico() {
        vm.cargar();

        EscandalloViewModel.Estado e = vm.estado().getValue();
        assertEquals(3, e.ingredientes.size());
        assertEquals(2, e.productos.size());
        assertEquals(Collections.singletonList("9 Tigger"), e.diagnostico.productosSinReceta);
        assertEquals(Collections.singletonList("Bacon"), e.diagnostico.extrasSinConsumo);
        assertNull(vm.mensaje().getValue());
    }

    @Test
    public void crear_un_ingrediente_con_stock_inicial() {
        vm.guardarIngrediente(new Ingrediente(0, "Lechuga", UnidadIngrediente.G, null, 2.5), 3000.0, true);

        assertEquals(Double.valueOf(3000.0), repo.ingredientes.get(100L).stock);
        assertEquals("Ingrediente guardado", vm.mensaje().getValue());
        assertEquals(4, vm.estado().getValue().ingredientes.size());
        vm.mensajeLeido();
        assertNull(vm.mensaje().getValue());
    }

    @Test
    public void editar_sin_cambiar_stock_lo_conserva_y_un_nombre_repetido_se_rechaza() {
        vm.guardarIngrediente(new Ingrediente(1, "Pollo asado", UnidadIngrediente.G, null, 7.5), null, false);
        assertEquals(Double.valueOf(1000.0), repo.ingredientes.get(1L).stock);
        assertEquals("Pollo asado", repo.ingredientes.get(1L).nombre);

        vm.guardarIngrediente(new Ingrediente(0, "PAN", UnidadIngrediente.UD, null, 0.3), null, false);
        assertEquals("Ya existe un ingrediente llamado Pan", vm.mensaje().getValue());
    }

    @Test
    public void recuento_eliminar_receta_extra_y_gastos() {
        vm.fijarStock(1, 250.0);
        assertEquals(Double.valueOf(250.0), repo.ingredientes.get(1L).stock);

        vm.guardarReceta(9, Collections.singletonList(new LineaReceta(9, 3, 30, "Roquefort")), 1.69);
        assertEquals(Double.valueOf(1.69), repo.gastos.get(9));
        assertTrue(vm.estado().getValue().recetario.tieneReceta(9));

        vm.guardarConsumoExtra("Bacon", Collections.singletonList(new ConsumoExtra("Bacon", 1, 20)));
        assertTrue(vm.estado().getValue().diagnostico.extrasSinConsumo.isEmpty());

        vm.eliminarIngrediente(3);
        assertEquals("Ingrediente eliminado", vm.mensaje().getValue());
        assertEquals(2, vm.estado().getValue().ingredientes.size());
    }

    @Test
    public void importar_informa_de_lo_cargado_y_de_lo_rechazado() {
        vm.importar(new ByteArrayInputStream(new byte[0]));

        assertTrue(vm.mensaje().getValue().startsWith("Importado: 2 ingredientes, 1 recetas, 1 extras"));
        assertTrue(vm.mensaje().getValue().contains("1 filas rechazadas"));
    }

    @Test
    public void errores_del_repositorio_llegan_como_mensaje() {
        repo.fallo = new IllegalStateException("base cerrada");
        vm.cargar();
        assertEquals("No se pudo guardar: base cerrada", vm.mensaje().getValue());

        vm.importar(new ByteArrayInputStream(new byte[0]) {
            @Override public void close() throws IOException { throw new IOException("disco"); }
        });
        assertTrue(vm.mensaje().getValue().startsWith("No se pudo importar"));
    }

    @Test
    public void el_constructor_por_defecto_usa_su_propio_hilo() {
        EscandalloViewModel real = new EscandalloViewModel(ApplicationProvider.<Application>getApplicationContext());
        real.onCleared();
    }
}
