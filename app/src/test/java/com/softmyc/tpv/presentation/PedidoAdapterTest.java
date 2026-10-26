package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Formateo del resumen del pedido ({@link PedidoAdapter#buildFromPedido}): unidades, peso con tara, tiempo,
 * personalizaciones y totales por línea. Fija el locale en es-ES para que los importes sean deterministas.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PedidoAdapterTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private Locale localeOriginal;
    private Context context;

    @Before
    public void fijarLocale() {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(new Locale("es", "ES"));
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void pedido_vacio_produce_listas_vacias() {
        PedidoAdapter.Data datos = PedidoAdapter.buildFromPedido(Collections.emptyList(), null);

        assertTrue(datos.detalles.isEmpty());
        assertTrue(datos.totales.isEmpty());
    }

    @Test
    public void linea_por_unidad_muestra_cantidad_y_total_con_el_precio_por_cantidad() {
        PedidoItem cafe = item("Café", 1.5, "UD", "Bebidas", 2);

        PedidoAdapter.Data datos = PedidoAdapter.buildFromPedido(Collections.singletonList(cafe), null);

        assertTrue(datos.detalles.get(0).toString().contains("2 x Café"));
        assertEquals(Collections.singletonList("3,00€"), datos.totales);
    }

    @Test
    public void extras_por_unidad_se_suman_al_precio_antes_de_multiplicar() {
        PedidoItem bocadillo = item("Bocadillo", 3.0, "UD", "Bocadillos", 2);

        PedidoAdapter.Data datos = PedidoAdapter.buildFromPedido(Collections.singletonList(bocadillo), it -> 0.5);

        assertEquals("7,00€", datos.totales.get(0));
    }

    @Test
    public void extras_del_calculador_nulo_cuentan_como_cero() {
        PedidoItem bocadillo = item("Bocadillo", 3.0, "UD", "Bocadillos", 1);

        PedidoAdapter.Data datos = PedidoAdapter.buildFromPedido(Collections.singletonList(bocadillo), null);

        assertEquals("3,00€", datos.totales.get(0));
    }

    @Test
    public void linea_por_peso_muestra_cantidad_medida_y_descuenta_la_tara() {
        PedidoItem uvas = item("Uvas", 2.0, "KG", "Frutas", 1);
        uvas.setCantidadMedida(1.5);
        uvas.setTaraKg(0.25);

        PedidoAdapter.Data datos = PedidoAdapter.buildFromPedido(Collections.singletonList(uvas), null);

        String texto = datos.detalles.get(0).toString();
        assertTrue(texto.contains("1,500 kg x Uvas") || texto.contains("1.500 kg x Uvas"));
        assertTrue(texto.contains("Tara"));
        assertTrue(texto.contains("0,250 kg") || texto.contains("0.250 kg"));
        // Neto 1,25 kg a 2,00 €/kg
        assertEquals("2,50€", datos.totales.get(0));
    }

    @Test
    public void tara_mayor_que_el_peso_no_produce_importe_negativo() {
        PedidoItem uvas = item("Uvas", 2.0, "KG", "Frutas", 1);
        uvas.setCantidadMedida(0.2);
        uvas.setTaraKg(0.5);

        PedidoAdapter.Data datos = PedidoAdapter.buildFromPedido(Collections.singletonList(uvas), null);

        assertEquals("0,00€", datos.totales.get(0));
    }

    @Test
    public void linea_por_tiempo_factura_horas_medidas_o_la_cantidad_si_no_hay_medida() {
        PedidoItem horas = item("Mano de obra", 40.0, "HORA", "Servicios", 1);
        horas.setCantidadMedida(1.5);
        PedidoItem sinMedida = item("Visita", 30.0, "HORA", "Servicios", 2);

        PedidoAdapter.Data datos = PedidoAdapter.buildFromPedido(Arrays.asList(horas, sinMedida), null);

        assertEquals("60,00€", datos.totales.get(0));
        assertEquals("60,00€", datos.totales.get(1));
    }

    @Test
    public void personalizaciones_se_listan_bajo_su_cabecera() {
        PedidoItem hamburguesa = item("Hamburguesa", 6.0, "UD", "Hamburguesas", 1);
        hamburguesa.getIngredientesQuitados().add("cebolla");
        hamburguesa.getIngredientesExtra().add("queso");
        hamburguesa.getSalsas().add("alioli");

        PedidoAdapter.Data datos = PedidoAdapter.buildFromPedido(Collections.singletonList(hamburguesa), null);

        String texto = datos.detalles.get(0).toString();
        assertTrue(texto.contains("Sin:"));
        assertTrue(texto.contains("cebolla"));
        assertTrue(texto.contains("Extras / Suplementos:"));
        assertTrue(texto.contains("queso"));
        assertTrue(texto.contains("Salsas:"));
        assertTrue(texto.contains("alioli"));
    }

    @Test
    public void linea_sin_personalizaciones_no_muestra_cabeceras_extra() {
        PedidoItem agua = item("Agua", 1.0, "UD", "Bebidas", 1);

        String texto = PedidoAdapter.buildFromPedido(Collections.singletonList(agua), null).detalles.get(0).toString();

        assertFalse(texto.contains("Sin:"));
        assertFalse(texto.contains("Salsas:"));
        assertFalse(texto.contains("Tara"));
    }

    @Test
    public void una_linea_por_cada_item_y_en_el_mismo_orden() {
        List<PedidoItem> pedido = Arrays.asList(
                item("Café", 1.5, "UD", "Bebidas", 1),
                item("Tarta", 4.0, "UD", "Postres", 2));

        PedidoAdapter.Data datos = PedidoAdapter.buildFromPedido(pedido, null);

        assertEquals(2, datos.detalles.size());
        assertEquals(Arrays.asList("1,50€", "8,00€"), datos.totales);
    }

    @Test
    public void getView_pinta_el_detalle_y_el_total_de_la_posicion() {
        PedidoAdapter.Data datos = PedidoAdapter.buildFromPedido(
                Arrays.asList(item("Café", 1.5, "UD", "Bebidas", 1), item("Tarta", 4.0, "UD", "Postres", 2)), null);
        PedidoAdapter adaptador = new PedidoAdapter(context, datos.detalles, datos.totales);

        View fila = adaptador.getView(1, null, new FrameLayout(context));

        assertNotNull(fila);
        assertEquals("8,00€", ((TextView) fila.findViewById(R.id.textoTotal)).getText().toString());
        assertTrue(((TextView) fila.findViewById(R.id.textoPedido)).getText().toString().contains("Tarta"));
    }

    private static PedidoItem item(String nombre, double precio, String unidad, String categoria, int cantidad) {
        Producto producto = new Producto(1, nombre, precio, categoria, new ArrayList<>(), unidad);
        return new PedidoItem(producto, cantidad);
    }
}
