package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.softmyc.tpv.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tarjeta de pedido web/cocina: identificador, origen, retraso, cliente, total, líneas con personalizaciones,
 * botones según estado y el caso de documento corrupto (no debe tumbar la lista).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PedidosWebAdapterTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private Context context;
    private List<String> acciones;

    @Before
    public void setUp() {
        // Material: los botones de la tarjeta son MaterialButton.
        context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_MyApplication);
        acciones = new ArrayList<>();
    }

    @Test
    public void id_del_pedido_usa_los_ocho_ultimos_caracteres_en_mayusculas_y_origen_web() {
        Map<String, Object> doc = pedido("cs_test_abcdefghij", "pendiente");

        View tarjeta = pintar(doc);

        assertEquals("PEDIDO #CDEFGHIJ • WEB", texto(tarjeta, R.id.tvOrderId));
    }

    @Test
    public void pedidos_creados_en_el_tpv_se_marcan_como_tpv_por_su_prefijo() {
        Map<String, Object> doc = pedido("TPV-42", "pendiente");

        assertEquals("PEDIDO #TPV-42 • TPV", texto(pintar(doc), R.id.tvOrderId));
    }

    @Test
    public void sin_id_de_sesion_se_usa_el_id_de_documento() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("_id", "65f0c0ffee123456");
        doc.put("estado", "pendiente");

        assertEquals("PEDIDO #EE123456 • WEB", texto(pintar(doc), R.id.tvOrderId));
    }

    @Test
    public void origen_explicito_tpv_prevalece_sobre_el_prefijo_de_la_sesion() {
        Map<String, Object> doc = pedido("cs_test_xyz", "pendiente");
        doc.put("origen", "TPV");

        assertEquals("PEDIDO #TEST_XYZ • TPV", texto(pintar(doc), R.id.tvOrderId));
    }

    @Test
    public void estado_retrasado_muestra_el_badge_y_otro_estado_lo_oculta() {
        View retrasado = pintar(pedido("cs_1", "Retrasado por cocina"));
        View pendiente = pintar(pedido("cs_2", "pendiente"));

        assertEquals(View.VISIBLE, retrasado.findViewById(R.id.tvOrderStatus).getVisibility());
        assertEquals("RETRASADO", texto(retrasado, R.id.tvOrderStatus));
        assertEquals(View.GONE, pendiente.findViewById(R.id.tvOrderStatus).getVisibility());
    }

    @Test
    public void cliente_con_telefono_y_mesa_se_muestra_completo() {
        Map<String, Object> doc = pedido("cs_1", "pendiente");
        doc.put("cliente", mapa("nombre", "Ana", "telefono", "600111222", "mesa", "Terraza 3"));

        View tarjeta = pintar(doc);

        assertEquals("Cliente: Ana (600111222)", texto(tarjeta, R.id.tvCustomerInfo));
        assertEquals("Terraza 3", texto(tarjeta, R.id.tvOrderTime));
    }

    @Test
    public void cliente_sin_mesa_ni_telefono_es_recogida_inmediata() {
        Map<String, Object> doc = pedido("cs_1", "pendiente");
        doc.put("cliente", mapa("nombre", "Luis"));

        View tarjeta = pintar(doc);

        assertEquals("Cliente: Luis", texto(tarjeta, R.id.tvCustomerInfo));
        assertEquals("Recogida inmediata", texto(tarjeta, R.id.tvOrderTime));
    }

    @Test
    public void cliente_como_texto_plano_se_usa_como_nombre_y_sin_cliente_queda_el_generico() {
        Map<String, Object> conTexto = pedido("cs_1", "pendiente");
        conTexto.put("cliente", "María");
        Map<String, Object> sinCliente = pedido("cs_2", "pendiente");

        assertEquals("Cliente: María", texto(pintar(conTexto), R.id.tvCustomerInfo));
        assertEquals("Cliente: Cliente Web", texto(pintar(sinCliente), R.id.tvCustomerInfo));
    }

    @Test
    public void total_se_muestra_con_dos_decimales_y_sin_total_queda_a_cero() {
        Map<String, Object> conTotal = pedido("cs_1", "pendiente");
        conTotal.put("total", 12.5);
        Map<String, Object> sinTotal = pedido("cs_2", "pendiente");

        assertEquals("12.50€", texto(pintar(conTotal), R.id.tvOrderTotal));
        assertEquals("0.00€", texto(pintar(sinTotal), R.id.tvOrderTotal));
    }

    @Test
    public void lineas_muestran_cantidad_y_personalizaciones() {
        Map<String, Object> doc = pedido("cs_1", "pendiente");
        Map<String, Object> linea = mapa("nombre", "Hamburguesa", "cantidad", 2);
        linea.put("extras", Arrays.asList("queso", "bacon"));
        linea.put("salsas", Collections.singletonList("alioli"));
        linea.put("quitados", Collections.singletonList("cebolla"));
        doc.put("items", Collections.singletonList(linea));

        String lineas = texto(pintar(doc), R.id.tvOrderItems);

        assertEquals("• 2x Hamburguesa (+queso, bacon) [Salsas: alioli] (SIN: cebolla)", lineas);
    }

    @Test
    public void si_no_hay_items_se_usa_el_carrito_con_alias_en_ingles() {
        Map<String, Object> doc = pedido("cs_1", "pendiente");
        doc.put("cart", Collections.singletonList(mapa("name", "Tarta", "quantity", 3)));

        assertEquals("• 3x Tarta", texto(pintar(doc), R.id.tvOrderItems));
    }

    @Test
    public void sin_lineas_muestra_la_linea_generica_del_pedido() {
        Map<String, Object> doc = pedido("cs_1", "pendiente");
        doc.put("items", new ArrayList<>());

        assertEquals("• 1x Pedido Bocatería La Nube", texto(pintar(doc), R.id.tvOrderItems));
    }

    @Test
    public void linea_con_cantidad_no_numerica_usa_uno_por_defecto() {
        Map<String, Object> doc = pedido("cs_1", "pendiente");
        doc.put("items", Collections.singletonList(mapa("nombre", "Agua", "cantidad", "muchas")));

        assertEquals("• 1x Agua", texto(pintar(doc), R.id.tvOrderItems));
    }

    @Test
    public void pedido_pendiente_ofrece_listo_retraso_y_ticket_con_sus_acciones() {
        Map<String, Object> doc = pedido("cs_1", "pendiente");
        View tarjeta = pintar(doc);

        assertEquals("¡LISTO!", texto(tarjeta, R.id.btnMarkReady));
        assertEquals(View.VISIBLE, tarjeta.findViewById(R.id.btnMarkDelay).getVisibility());

        tarjeta.findViewById(R.id.btnMarkReady).performClick();
        tarjeta.findViewById(R.id.btnMarkDelay).performClick();
        tarjeta.findViewById(R.id.btnPrintTicket).performClick();

        assertEquals(Arrays.asList(
                "estado:¡Listo para recoger!",
                "estado:Retrasado (+15 min)",
                "ticket"), acciones);
    }

    @Test
    public void pedido_listo_pasa_a_entregado_y_oculta_el_retraso() {
        Map<String, Object> doc = pedido("cs_1", "Listo");
        View tarjeta = pintar(doc);

        assertEquals("ENTREGADO", texto(tarjeta, R.id.btnMarkReady));
        assertEquals(View.GONE, tarjeta.findViewById(R.id.btnMarkDelay).getVisibility());

        tarjeta.findViewById(R.id.btnMarkReady).performClick();

        assertEquals(Collections.singletonList("estado:Entregado"), acciones);
    }

    @Test
    public void estado_ausente_se_trata_como_pendiente() {
        Map<String, Object> doc = pedido("cs_1", "pendiente");
        doc.remove("estado");

        assertEquals("¡LISTO!", texto(pintar(doc), R.id.btnMarkReady));
    }

    @Test
    public void documento_corrupto_muestra_aviso_y_oculta_acciones_sin_lanzar() {
        Map<String, Object> doc = pedido("cs_1", "pendiente");
        doc.put("stripeSessionId", 12345);

        View tarjeta = pintar(doc);

        assertEquals("PEDIDO CORRUPTO", texto(tarjeta, R.id.tvOrderId));
        assertEquals(View.GONE, tarjeta.findViewById(R.id.btnMarkReady).getVisibility());
        assertEquals(View.GONE, tarjeta.findViewById(R.id.btnMarkDelay).getVisibility());
        assertEquals("0.00€", texto(tarjeta, R.id.tvOrderTotal));
    }

    @Test
    public void la_lista_cuenta_una_tarjeta_por_documento() {
        List<Map<String, Object>> docs = Arrays.asList(pedido("cs_1", "pendiente"), pedido("cs_2", "Listo"));
        PedidosWebAdapter adaptador = new PedidosWebAdapter(docs, listener());

        assertEquals(2, adaptador.getItemCount());
    }

    private View pintar(Map<String, Object> doc) {
        PedidosWebAdapter adaptador = new PedidosWebAdapter(Collections.singletonList(doc), listener());
        PedidosWebAdapter.OrderViewHolder holder =
                adaptador.onCreateViewHolder(new FrameLayout(context), 0);
        adaptador.onBindViewHolder(holder, 0);
        assertNotNull(holder.itemView);
        return holder.itemView;
    }

    private PedidosWebAdapter.OrderActionListener listener() {
        return new PedidosWebAdapter.OrderActionListener() {
            @Override
            public void onMarkStatus(Map<String, Object> doc, String newStatus) {
                acciones.add("estado:" + newStatus);
            }

            @Override
            public void onPrintTicket(Map<String, Object> doc) {
                acciones.add("ticket");
            }
        };
    }

    private static Map<String, Object> pedido(String sesion, String estado) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("stripeSessionId", sesion);
        doc.put("estado", estado);
        return doc;
    }

    private static Map<String, Object> mapa(Object... pares) {
        Map<String, Object> mapa = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((String) pares[i], pares[i + 1]);
        }
        return mapa;
    }

    private static String texto(View raiz, int id) {
        View vista = raiz.findViewById(id);
        assertNotNull("vista " + id, vista);
        if (vista instanceof MaterialButton) {
            return ((MaterialButton) vista).getText().toString();
        }
        assertTrue("la vista " + id + " no es un TextView", vista instanceof TextView);
        return ((TextView) vista).getText().toString();
    }
}
