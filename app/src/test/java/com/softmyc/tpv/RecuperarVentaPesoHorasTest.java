package com.softmyc.tpv;

import static org.junit.Assert.assertEquals;

import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.usecase.CalcularTotalUseCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** NEG-01 y NEG-02: el peso, las horas y la tara sobreviven al guardar y recuperar una venta. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RecuperarVentaPesoHorasTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private final CalcularTotalUseCase calculadora = new CalcularTotalUseCase();
    private final Map<String, Double> sinExtras = new HashMap<>();

    private static Producto fruta(String unidad) {
        return new Producto(10, "Manzanas", 4.0, "Frutería", Collections.emptyList(), unidad);
    }

    private static PedidoItem pesado(Producto p, double kg, double tara) {
        PedidoItem item = new PedidoItem(p, (int) Math.ceil(kg));
        item.setCantidadMedida(kg);
        item.setUnidadMedida("KG");
        item.setTaraKg(tara);
        return item;
    }

    private List<PedidoItem> idaYVuelta(PedidoItem original, List<Producto> catalogo) {
        String json = SyncManager.serializarItems(Collections.singletonList(original), sinExtras);
        return SyncManager.deserializarItems(json, catalogo);
    }

    @Test
    public void recuperarVentaAlPeso_mantieneElTotalOriginal() {
        PedidoItem original = pesado(fruta("KG"), 0.350, 0.050);
        double totalOriginal = calculadora.calcularTotal(Collections.singletonList(original), sinExtras);

        List<PedidoItem> recuperada = idaYVuelta(original, Collections.singletonList(fruta("KG")));

        assertEquals(totalOriginal, calculadora.calcularTotal(recuperada, sinExtras), 0.001);
        assertEquals(0.350, recuperada.get(0).getCantidadMedida(), 0.0001);
        assertEquals(0.050, recuperada.get(0).getTaraKg(), 0.0001);
        assertEquals("KG", recuperada.get(0).getUnidadMedida());
    }

    @Test
    public void recuperarVentaAlPeso_deUnProductoQueSoloEsDePesoPorCategoria() {
        // El catálogo guarda unidad UD; la unidad real (KG) viaja en la propia venta.
        PedidoItem original = pesado(fruta("UD"), 2.5, 0.2);
        double totalOriginal = calculadora.calcularTotal(Collections.singletonList(original), sinExtras);

        List<PedidoItem> recuperada = idaYVuelta(original, Collections.singletonList(fruta("UD")));

        assertEquals(totalOriginal, calculadora.calcularTotal(recuperada, sinExtras), 0.001);
    }

    @Test
    public void recuperarVentaDeProductoDesaparecidoDelCatalogo_conservaPesoYUnidad() {
        PedidoItem original = pesado(fruta("KG"), 1.25, 0.0);
        double totalOriginal = calculadora.calcularTotal(Collections.singletonList(original), sinExtras);

        List<PedidoItem> recuperada = idaYVuelta(original, new ArrayList<>());

        assertEquals(totalOriginal, calculadora.calcularTotal(recuperada, sinExtras), 0.001);
        assertEquals(1.25, recuperada.get(0).getCantidadMedida(), 0.0001);
    }

    @Test
    public void ventaAntiguaSinCamposDeMedida_seRecuperaComoUnidades() {
        String antiguo = "[{\"nombre\":\"Burger\",\"cantidad\":2,\"precio_unitario\":6.0,\"categoria\":\"Hamburguesas\"}]";
        Producto burger = new Producto(1, "Burger", 6.0, "Hamburguesas", Collections.emptyList());

        List<PedidoItem> recuperada = SyncManager.deserializarItems(antiguo, Collections.singletonList(burger));

        assertEquals(2, recuperada.get(0).getCantidad());
        assertEquals("UD", recuperada.get(0).getUnidadMedida());
        assertEquals(12.0, calculadora.calcularTotal(recuperada, sinExtras), 0.001);
    }

    @Test
    public void recuperarVentaPorHoras_mantieneElTotalOriginal() {
        Producto manoDeObra = new Producto(20, "Mano de obra", 30.0, "Taller", Collections.emptyList(), "HORA");
        PedidoItem original = new PedidoItem(manoDeObra, 2);
        original.setCantidadMedida(1.5);
        original.setUnidadMedida("HORA");
        double totalOriginal = calculadora.calcularTotal(Collections.singletonList(original), sinExtras);

        List<PedidoItem> recuperada = idaYVuelta(original, Collections.singletonList(manoDeObra));

        assertEquals(totalOriginal, calculadora.calcularTotal(recuperada, sinExtras), 0.001);
        assertEquals(1.5, recuperada.get(0).getCantidadMedida(), 0.0001);
    }

    @Test
    public void ventaConUnidadPeroSinTara_recuperaConTaraCero() {
        String json = "[{\"nombre\":\"Manzanas\",\"cantidad\":2,\"cantidad_medida\":1.5,\"unidad\":\"KG\",\"precio_unitario\":4.0,\"categoria\":\"Frutería\"}]";

        List<PedidoItem> recuperada = SyncManager.deserializarItems(json, Collections.singletonList(fruta("KG")));

        assertEquals(0.0, recuperada.get(0).getTaraKg(), 0.0);
        assertEquals(6.0, calculadora.calcularTotal(recuperada, sinExtras), 0.001);
    }

    @Test
    public void unidadEfectiva_deProductoDeFruteriaConUnidadUdEsKg() {
        assertEquals("KG", fruta("UD").getUnidadEfectiva());
    }

    @Test
    public void unidadEfectiva_respetaLaUnidadDelProductoYElResto() {
        assertEquals("KG", fruta("KG").getUnidadEfectiva());
        assertEquals("HORA", new Producto(1, "Mano de obra", 30.0, "Taller", Collections.emptyList()).getUnidadEfectiva());
        assertEquals("UD", new Producto(2, "Burger", 6.0, "Hamburguesas", Collections.emptyList()).getUnidadEfectiva());
    }
}
