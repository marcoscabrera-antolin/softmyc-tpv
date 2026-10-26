package com.softmyc.tpv.data.printer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ticket de cocina para pedidos online ({@link TicketGenerator#generarTicketOnline}). Fija el locale en es-ES
 * para que los importes sean deterministas.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TicketGeneratorOnlineTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private final Context context = ApplicationProvider.getApplicationContext();
    private Locale localeOriginal;

    @Before
    public void fijarLocaleYPrefs() {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(new Locale("es", "ES"));
        limpiarPrefs();
    }

    @After
    public void restaurarLocaleYPrefs() {
        Locale.setDefault(localeOriginal);
        limpiarPrefs();
    }

    @Test
    public void cabecera_incluye_numero_de_pedido_online_y_cierre_para_cocina() {
        TicketGenerator.TicketResult resultado = generar(null, Collections.emptyList(), 0.0, 17);

        assertTrue(resultado.texto.contains("PEDIDO ONLINE N 17"));
        assertTrue(resultado.texto.contains("PAGADO ONLINE - PREPARAR EN COCINA"));
        assertTrue(resultado.texto.contains("MI RESTAURANTE"));
    }

    @Test
    public void sin_cliente_no_imprime_bloque_de_datos_de_cliente() {
        TicketGenerator.TicketResult resultado = generar(null, Collections.emptyList(), 0.0, 1);

        assertFalse(resultado.texto.contains("CLIENTE:"));
        assertFalse(resultado.texto.contains("TIPO/MESA:"));
    }

    @Test
    public void cliente_sin_campos_usa_valores_por_defecto() {
        TicketGenerator.TicketResult resultado = generar(new HashMap<>(), Collections.emptyList(), 0.0, 1);

        assertTrue(resultado.texto.contains("CLIENTE: Cliente"));
        assertTrue(resultado.texto.contains("TEL: -"));
        assertTrue(resultado.texto.contains("TIPO/MESA: Recogida"));
    }

    @Test
    public void cliente_con_datos_los_imprime_tal_cual() {
        Map<String, Object> cliente = new HashMap<>();
        cliente.put("nombre", "Ana Ruiz");
        cliente.put("telefono", "600111222");
        cliente.put("mesa", "Mesa 4");

        TicketGenerator.TicketResult resultado = generar(cliente, Collections.emptyList(), 0.0, 1);

        assertTrue(resultado.texto.contains("CLIENTE: Ana Ruiz"));
        assertTrue(resultado.texto.contains("TEL: 600111222"));
        assertTrue(resultado.texto.contains("TIPO/MESA: Mesa 4"));
    }

    @Test
    public void linea_usa_cantidad_y_precio_unitario_cuando_estan_presentes() {
        Map<String, Object> linea = linea("Pizza", "cantidad", 2, "precio_unitario", 5.5);

        String texto = generar(null, Collections.singletonList(linea), 11.0, 1).texto;

        assertTrue(texto.contains("2 x Pizza"));
        assertTrue(texto.contains("Subtotal: 11,00 EUR"));
    }

    @Test
    public void linea_usa_quantity_y_totalPrice_como_alternativa_en_ingles() {
        Map<String, Object> linea = linea("Burger", "quantity", 3, "totalPrice", 4.0);

        String texto = generar(null, Collections.singletonList(linea), 12.0, 1).texto;

        assertTrue(texto.contains("3 x Burger"));
        assertTrue(texto.contains("Subtotal: 12,00 EUR"));
    }

    @Test
    public void linea_sin_precio_ni_cantidad_usa_basePrice_y_cantidad_uno() {
        Map<String, Object> linea = new HashMap<>();
        linea.put("nombre", "Agua");
        linea.put("basePrice", 1.25);

        String texto = generar(null, Collections.singletonList(linea), 1.25, 1).texto;

        assertTrue(texto.contains("1 x Agua"));
        assertTrue(texto.contains("Subtotal: 1,25 EUR"));
    }

    @Test
    public void linea_sin_nombre_usa_name_y_si_tampoco_existe_Producto() {
        Map<String, Object> enIngles = new HashMap<>();
        enIngles.put("name", "Tarta");
        Map<String, Object> sinNombre = new HashMap<>();

        String texto = generar(null, Arrays.asList(enIngles, sinNombre), 0.0, 1).texto;

        assertTrue(texto.contains("1 x Tarta"));
        assertTrue(texto.contains("1 x Producto"));
    }

    @Test
    public void linea_con_precio_texto_no_numerico_cuenta_como_cero() {
        Map<String, Object> linea = linea("Sopa", "cantidad", 2, "precio_unitario", "caro");

        String texto = generar(null, Collections.singletonList(linea), 0.0, 1).texto;

        assertTrue(texto.contains("Subtotal: 0,00 EUR"));
    }

    @Test
    public void quitados_extras_y_salsas_se_imprimen_con_sus_prefijos() {
        Map<String, Object> linea = linea("Hamburguesa", "cantidad", 1, "precio_unitario", 6.0);
        linea.put("quitados", Arrays.asList("cebolla", "tomate"));
        linea.put("extras", Collections.singletonList("queso"));
        linea.put("salsas", Collections.singletonList("alioli"));

        String texto = generar(null, Collections.singletonList(linea), 6.0, 1).texto;

        assertTrue(texto.contains("  Sin:\n    - cebolla\n    - tomate\n"));
        assertTrue(texto.contains("  Extra:\n    + queso\n"));
        assertTrue(texto.contains("  Salsas:\n    * alioli\n"));
    }

    @Test
    public void listas_de_personalizacion_vacias_no_imprimen_cabeceras() {
        Map<String, Object> linea = linea("Ensalada", "cantidad", 1, "precio_unitario", 3.0);
        linea.put("quitados", new ArrayList<String>());

        String texto = generar(null, Collections.singletonList(linea), 3.0, 1).texto;

        assertFalse(texto.contains("Sin:"));
        assertFalse(texto.contains("Extra:"));
        assertFalse(texto.contains("Salsas:"));
    }

    @Test
    public void items_nulos_no_lanzan_y_solo_imprime_el_total() {
        TicketGenerator.TicketResult resultado = generar(null, null, 9.5, 1);

        assertTrue(resultado.texto.contains("TOTAL: 9,50 EUR"));
        assertEquals(9.5, resultado.total, 1e-9);
    }

    @Test
    public void total_del_resultado_coincide_con_el_recibido_aunque_no_cuadre_con_las_lineas() {
        Map<String, Object> linea = linea("Café", "cantidad", 1, "precio_unitario", 1.0);

        TicketGenerator.TicketResult resultado = generar(null, Collections.singletonList(linea), 7.77, 1);

        assertEquals(7.77, resultado.total, 1e-9);
        assertTrue(resultado.texto.contains("TOTAL: 7,77 EUR"));
    }

    @Test
    public void nombre_de_restaurante_vacio_cae_al_valor_por_defecto() {
        configurar("nombre_restaurante", "   ");

        String texto = generar(null, Collections.emptyList(), 0.0, 1).texto;

        assertTrue(texto.contains("MI RESTAURANTE"));
    }

    @Test
    public void nombre_de_restaurante_configurado_se_imprime_en_mayusculas_al_pie() {
        configurar("nombre_restaurante", "Bar Sol");

        String texto = generar(null, Collections.emptyList(), 0.0, 1).texto;

        assertTrue(texto.trim().endsWith("BAR SOL"));
    }

    @Test
    public void letra_grande_reduce_el_ancho_a_24_columnas() {
        configurar("tamanio_letra", 2);

        String primeraLinea = generar(null, Collections.emptyList(), 0.0, 1).texto.split("\n")[0];

        assertEquals(24, primeraLinea.length());
    }

    @Test
    public void letra_normal_usa_48_columnas() {
        String primeraLinea = generar(null, Collections.emptyList(), 0.0, 1).texto.split("\n")[0];

        assertEquals(48, primeraLinea.length());
    }

    private TicketGenerator.TicketResult generar(Map<String, Object> cliente,
                                                 List<Map<String, Object>> items,
                                                 double total,
                                                 int numeroPedido) {
        return TicketGenerator.generarTicketOnline(context, cliente, items, total, numeroPedido);
    }

    private static Map<String, Object> linea(String nombre, Object... pares) {
        Map<String, Object> linea = new HashMap<>();
        linea.put("nombre", nombre);
        for (int i = 0; i < pares.length; i += 2) {
            linea.put((String) pares[i], pares[i + 1]);
        }
        return linea;
    }

    private void configurar(String clave, int valor) {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putInt(clave, valor).commit();
    }

    private void configurar(String clave, String valor) {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putString(clave, valor).commit();
    }

    private void limpiarPrefs() {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().clear().commit();
        SecurePreferencesHelper.getCounterPrefs(context).edit().clear().commit();
    }
}
