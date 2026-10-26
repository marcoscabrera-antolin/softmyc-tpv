package com.softmyc.tpv.data.printer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.domain.CalculoPedidoException;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Casos límite del ticket: pedido vacío, cantidades, nombres largos, caracteres fuera de ISO-8859-1, anchos de papel,
 * importes con muchos decimales, extras/quitados y contador. Fija el locale en es-ES para que las cifras sean
 * deterministas (el ticket usa el locale por defecto para los decimales).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TicketGeneratorCasosLimiteTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String NOMBRE_TIENDA = "Tienda Test";
    private static final String MONEDA_EUR = "EUR";

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
    public void pedido_vacio_imprime_cabecera_y_total_cero() {
        TicketGenerator.TicketResult resultado = TicketGenerator.generarTicket(
                context, new ArrayList<>(), NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 7);

        assertEquals(0.0, resultado.total, 0.0001);
        assertTrue(resultado.texto.contains("PEDIDO N 7"));
        assertTrue(resultado.texto.contains("TOTAL: 0,00 EUR"));
        assertFalse(resultado.texto.contains("Subtotal:"));
    }

    @Test
    public void linea_con_cantidad_cero_muestra_subtotal_cero_y_no_suma() {
        List<PedidoItem> pedido = Collections.singletonList(ud("Café", 2.50, 0));

        TicketGenerator.TicketResult resultado = TicketGenerator.generarTicket(
                context, pedido, NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 1);

        assertEquals(0.0, resultado.total, 0.0001);
        assertTrue(resultado.texto.contains("Subtotal: 0,00 EUR"));
    }

    @Test
    public void nombre_largo_se_imprime_completo_sin_truncar() {
        String nombreLargo = "Bocadillo de jamón serrano con tomate natural y aceite de oliva virgen extra";
        List<PedidoItem> pedido = Collections.singletonList(ud(nombreLargo, 4.00, 1));

        TicketGenerator.TicketResult resultado = TicketGenerator.generarTicket(
                context, pedido, NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 1);

        // El nombre puede partirse en varias líneas, pero no debe perderse ni truncarse
        assertTrue(resultado.texto.replaceAll("\\s+", " ").contains("1 x " + nombreLargo));
    }

    /**
     * Hallazgo (TEST-10): el ticket no ajusta las líneas al ancho del papel. Un nombre de más de 48 columnas
     * produce una línea más ancha que el papel y el subtotal alineado a la derecha se descuadra al saltar la
     * impresora de línea. Se deja documentado; no se corrige en esta tarea.
     */
    @Test
    public void nombre_largo_no_supera_las_columnas_del_papel() {
        String nombreLargo = "Bocadillo de jamón serrano con tomate natural y aceite de oliva virgen extra";
        List<PedidoItem> pedido = Collections.singletonList(ud(nombreLargo, 4.00, 1));

        TicketGenerator.TicketResult resultado = TicketGenerator.generarTicket(
                context, pedido, NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 1);

        for (String linea : resultado.texto.split("\n")) {
            assertTrue("línea de " + linea.length() + " columnas: " + linea, linea.length() <= 48);
        }
    }

    @Test
    public void caracteres_fuera_de_iso_8859_1_llegan_al_texto_del_ticket_sin_transformar() {
        // La conversión a ISO-8859-1 (y el '?' resultante) ocurre al enviar a la impresora, no al generar el texto.
        List<PedidoItem> pedido = Collections.singletonList(ud("Café ☕ Ñandú", 1.50, 1));

        TicketGenerator.TicketResult resultado = TicketGenerator.generarTicket(
                context, pedido, NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 1);

        assertTrue(resultado.texto.contains("1 x Café ☕ Ñandú"));
    }

    @Test
    public void letra_grande_usa_24_columnas_en_separadores_y_totales() {
        configurar("tamanio_letra", 2);
        List<PedidoItem> pedido = Collections.singletonList(ud("Café", 2.50, 2));

        TicketGenerator.TicketResult resultado = TicketGenerator.generarTicket(
                context, pedido, NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 1);

        String[] lineas = resultado.texto.split("\n");
        assertEquals("-".repeat(24), lineas[0]);
        assertEquals(24, lineaQueEmpiezaPor(lineas, "TOTAL:").length());
    }

    @Test
    public void letra_normal_usa_48_columnas_en_separadores_y_totales() {
        List<PedidoItem> pedido = Collections.singletonList(ud("Café", 2.50, 2));

        TicketGenerator.TicketResult resultado = TicketGenerator.generarTicket(
                context, pedido, NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 1);

        String[] lineas = resultado.texto.split("\n");
        assertEquals("-".repeat(48), lineas[0]);
        assertEquals(48, lineaQueEmpiezaPor(lineas, "TOTAL:").length());
    }

    @Test
    public void decimales_del_total_se_redondean_a_centimos_como_el_motor() {
        // Comportamiento actual: el precio unitario se redondea a céntimos antes de multiplicar.
        // 3 x 0,333 -> 3 x 0,33 = 0,99 (la cifra exacta 0,999 no aparece en el ticket).
        TicketGenerator.TicketResult tres = TicketGenerator.generarTicket(
                context, Collections.singletonList(ud("Pieza", 0.333, 3)), NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 1);
        // 1,005 se redondea hacia arriba (HALF_UP) como en BigDecimal, no a 1,00 por el binario del double
        TicketGenerator.TicketResult medio = TicketGenerator.generarTicket(
                context, Collections.singletonList(ud("Lote", 1.005, 1)), NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 1);

        assertEquals(0.99, tres.total, 0.0001);
        assertTrue(tres.texto.contains("TOTAL: 0,99 EUR"));
        assertEquals(1.01, medio.total, 0.0001);
        assertTrue(medio.texto.contains("TOTAL: 1,01 EUR"));
    }

    @Test
    public void cantidades_grandes_no_pierden_precision() {
        List<PedidoItem> pedido = Collections.singletonList(ud("Tornillo", 9.99, 100000));

        TicketGenerator.TicketResult resultado = TicketGenerator.generarTicket(
                context, pedido, NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 1);

        assertEquals(999000.00, resultado.total, 0.001);
        assertTrue(resultado.texto.contains("TOTAL: 999000,00 EUR"));
    }

    @Test
    public void extras_quitados_y_salsas_se_imprimen_y_el_extra_suma_al_total() {
        PedidoItem hamburguesa = ud("Hamburguesa", 6.00, 2);
        hamburguesa.getIngredientesQuitados().add("Queso");
        hamburguesa.getIngredientesExtra().add("Bacon");
        hamburguesa.getSalsas().add("Ketchup");
        Map<String, Double> precios = Collections.singletonMap("Bacon", 1.20);

        TicketGenerator.TicketResult resultado = TicketGenerator.generarTicket(
                context, Collections.singletonList(hamburguesa), NOMBRE_TIENDA, precios, "Tarjeta", 1);

        assertTrue(resultado.texto.contains("  Sin:\n    - Queso\n"));
        assertTrue(resultado.texto.contains("  Extra:\n    + Bacon\n"));
        assertTrue(resultado.texto.contains("  Salsas:\n    * Ketchup\n"));
        // 2 x (6,00 + 1,20) = 14,40
        assertEquals(14.40, resultado.total, 0.001);
        assertTrue(resultado.texto.contains("Subtotal: 14,40 EUR"));
    }

    @Test
    public void numero_de_pedido_del_contador_avanza_solo_en_la_version_sin_numero() {
        SecurePreferencesHelper.getCounterPrefs(context).edit().putInt("numero_pedido", 5).commit();
        List<PedidoItem> pedido = Collections.singletonList(ud("Café", 2.50, 1));

        TicketGenerator.TicketResult conContador = TicketGenerator.generarTicket(
                context, pedido, NOMBRE_TIENDA, new HashMap<>(), "Efectivo");
        int tras = SecurePreferencesHelper.getCounterPrefs(context).getInt("numero_pedido", -1);
        TicketGenerator.TicketResult conNumero = TicketGenerator.generarTicket(
                context, pedido, NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 42);

        assertTrue(conContador.texto.contains("PEDIDO N 5"));
        assertEquals(6, tras);
        assertTrue(conNumero.texto.contains("PEDIDO N 42"));
        assertEquals("el número explícito no toca el contador", 6,
                SecurePreferencesHelper.getCounterPrefs(context).getInt("numero_pedido", -1));
    }

    @Test
    public void simbolo_de_moneda_configurado_se_usa_y_el_euro_se_normaliza_a_EUR() {
        List<PedidoItem> pedido = Collections.singletonList(ud("Café", 2.50, 1));
        configurar("simbolo_moneda", "USD");
        TicketGenerator.TicketResult usd = TicketGenerator.generarTicket(
                context, pedido, NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 1);
        configurar("simbolo_moneda", "€");
        TicketGenerator.TicketResult euro = TicketGenerator.generarTicket(
                context, pedido, NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 1);

        assertTrue(usd.texto.contains("TOTAL: 2,50 USD"));
        assertTrue(euro.texto.contains("TOTAL: 2,50 " + MONEDA_EUR));
    }

    /** Un peso neto no positivo es un error de dominio tipado: el ticket no llega a generarse ni a imprimirse. */
    @Test(expected = CalculoPedidoException.class)
    public void tara_mayor_que_bruto_lanza_error_tipado_y_no_genera_ticket() {
        Producto manzana = new Producto(1, "Manzana", 3.00, "Frutas", Collections.emptyList(), "KG");
        PedidoItem pesado = new PedidoItem(manzana, 1);
        pesado.setCantidadMedida(0.100);
        pesado.setTaraKg(0.500);

        TicketGenerator.generarTicket(
                context, Collections.singletonList(pesado), NOMBRE_TIENDA, new HashMap<>(), "Efectivo", 1);
    }

    @Test
    public void nombre_configurado_vacio_usa_el_de_la_venta_en_mayusculas() {
        configurar("nombre_restaurante", "   ");
        List<PedidoItem> pedido = Collections.singletonList(ud("Café", 2.50, 1));

        TicketGenerator.TicketResult resultado = TicketGenerator.generarTicket(
                context, pedido, "Tienda Sur", new HashMap<>(), "Efectivo", 1);

        assertTrue(resultado.texto.contains("TIENDA SUR"));
        assertFalse(resultado.texto.contains("Tienda Sur"));
    }

    private static PedidoItem ud(String nombre, double precio, int cantidad) {
        Producto producto = new Producto(1, nombre, precio, "Test", Collections.emptyList(), "UD");
        return new PedidoItem(producto, cantidad);
    }

    private void configurar(String clave, int valor) {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putInt(clave, valor).commit();
    }

    private void configurar(String clave, String valor) {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putString(clave, valor).commit();
    }

    private static String lineaQueEmpiezaPor(String[] lineas, String prefijo) {
        for (String linea : lineas) {
            if (linea.trim().startsWith(prefijo)) return linea;
        }
        throw new AssertionError("no hay línea que empiece por " + prefijo);
    }

    private void limpiarPrefs() {
        SecurePreferencesHelper.getPrinterPrefs(context).edit().clear().commit();
        SecurePreferencesHelper.getCounterPrefs(context).edit().clear().commit();
    }
}
