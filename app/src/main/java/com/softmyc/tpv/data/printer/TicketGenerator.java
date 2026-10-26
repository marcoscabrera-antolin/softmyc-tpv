package com.softmyc.tpv.data.printer;
import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.*;
import com.softmyc.tpv.data.*;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TicketGenerator {

    private static final String SANGRIA_PRODUCTO = "    ";
    private static final String SANGRIA_MODIFICADOR = "    ";
    private static final String SANGRIA_MODIFICADOR_CONTINUACION = "      ";

    /**
     * Contenedor de resultados generado tras crear un ticket.
     *
     * Incluye:
     *  - El texto completo del ticket listo para imprimir.
     *  - El total calculado del pedido.
     */
    public static class TicketResult {
        public final String texto;
        public final double total;

        /**
         *  Construye un resultado de ticket con el texto generado y el total.
         *
         * @param texto Texto final del ticket.
         * @param total Importe total del pedido.
         */
        public TicketResult(String texto, double total) {
            this.texto = texto;
            this.total = total;
        }
    }


    /**
     * Genera el texto completo del ticket de un pedido, incluyendo cabecera,
     * líneas de productos, subtotales, total final y mensajes de cierre.
     *
     * Funcionalidad:
     *  - Obtiene y actualiza el número de pedido almacenado en SharedPreferences.
     *  - Construye la cabecera con nombre de tienda, número de pedido y fecha.
     *  - Recorre cada elemento del pedido calculando:
     *      * Precio base
     *      * Precio de ingredientes extra
     *      * Subtotal por cantidad
     *  - Añade secciones de ingredientes quitados, extras y salsas.
     *  - Alinea subtotales y total final para impresión en impresoras térmicas.
     *  - Devuelve un objeto TicketResult con el texto generado y el total.
     *
     * @param context            Contexto necesario para acceder a preferencias.
     * @param pedido             Lista de productos incluidos en el pedido.
     * @param nombreTienda       Nombre de la tienda que aparecerá en el ticket.
     * @param preciosIngredientes Mapa con los precios de los ingredientes extra.
     *
     * @return Objeto TicketResult con el texto del ticket y el total calculado.
     */
    public static TicketResult generarTicket(
            Context context,
            List<PedidoItem> pedido,
            String nombreTienda,
            Map<String, Double> preciosIngredientes,
            String metodoPago) {
        // C7: Contador de pedido en SharedPreferences cifrado
        SharedPreferences prefs = getSecurePrefs(context);
        int numeroPedido = prefs.getInt("numero_pedido", 1);
        TicketResult resultado = generarTicket(context, pedido, nombreTienda, preciosIngredientes, metodoPago, numeroPedido);
        prefs.edit().putInt("numero_pedido", numeroPedido + 1).apply();
        return resultado;
    }

    /**
     * Igual que la anterior pero con el número de pedido dado (el id de la venta guardada) y sin tocar
     * el contador: el número impreso es el que sirve para recuperar la venta.
     */
    public static TicketResult generarTicket(
            Context context,
            List<PedidoItem> pedido,
            String nombreTienda,
            Map<String, Double> preciosIngredientes,
            String metodoPago,
            int numeroPedido) {

        StringBuilder sb = new StringBuilder();
        double total = 0;
        // Un único cálculo de importes: el mismo motor (BigDecimal) que la pantalla de pedido
        com.softmyc.tpv.domain.usecase.CalcularTotalUseCase calculadora =
                new com.softmyc.tpv.domain.usecase.CalcularTotalUseCase();

        // Cargar ajustes del establecimiento y tamaño de letra
        SharedPreferences settingsPrefs = getSettingsPrefs(context);
        String nombreConfig = settingsPrefs.getString("nombre_restaurante", nombreTienda);
        if (nombreConfig == null || nombreConfig.trim().isEmpty()) {
            nombreConfig = nombreTienda;
        }
        String direccionFiscal = settingsPrefs.getString("direccion_fiscal", "");
        String informacionFiscal = settingsPrefs.getString("informacion_fiscal", "");
        String monedaSymbol = settingsPrefs.getString("simbolo_moneda", "EUR");
        if (monedaSymbol == null || monedaSymbol.trim().isEmpty() || monedaSymbol.trim().equals("€")) {
            monedaSymbol = "EUR";
        }
        int tamanioLetra = settingsPrefs.getInt("tamanio_letra", 0);

        // Si la letra es grande/muy grande (doble ancho), el ancho se reduce a la mitad (24 cols)
        // para garantizar que no haya saltos de línea molestos
        int cols = (tamanioLetra == 2) ? 24 : 48;
        String divider = repeat("-", cols);

        // --- CABECERA ---
        sb.append(divider).append("\n");
        appendCentrado(sb, nombreConfig.toUpperCase(), cols);

        if (direccionFiscal != null && !direccionFiscal.trim().isEmpty()) {
            appendCentrado(sb, direccionFiscal, cols);
        }
        if (informacionFiscal != null && !informacionFiscal.trim().isEmpty()) {
            appendCentrado(sb, informacionFiscal, cols);
        }

        sb.append(center("PEDIDO N " + numeroPedido, cols)).append("\n");

        String fecha = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date());
        sb.append(center(fecha, cols)).append("\n");
        sb.append(divider).append("\n\n");

        // --- LINEAS DE PRODUCTO ---
        for (PedidoItem item : pedido) {

            double subtotal = calculadora.calcularTotal(java.util.Collections.singletonList(item), preciosIngredientes);
            total += subtotal;

            // Producto principal: cantidad discreta o medida continua (kg, horas)
            StringBuilder cabecera = new StringBuilder();
            if ("UD".equalsIgnoreCase(item.getUnidadMedida())) {
                cabecera.append(item.getCantidad()).append(" x ");
            } else {
                cabecera.append(String.format(Locale.getDefault(), "%.3f %s x ", item.getCantidadMedida(),
                        item.getUnidadMedida().toLowerCase(Locale.ROOT)));
                if (item.getTaraKg() > 0.0) {
                    cabecera.append(String.format(Locale.getDefault(), "(tara %.3f) ", item.getTaraKg()));
                }
            }
            cabecera.append(item.getNombre());
            appendAjustado(sb, cabecera.toString(), cols, "", SANGRIA_PRODUCTO);

            // Sin ingredientes
            if (!item.getIngredientesQuitados().isEmpty()) {
                sb.append("  Sin:\n");
                for (String ing : item.getIngredientesQuitados()) {
                    appendAjustado(sb, "- " + ing, cols, SANGRIA_MODIFICADOR, SANGRIA_MODIFICADOR_CONTINUACION);
                }
            }

            // Con extras
            if (!item.getIngredientesExtra().isEmpty()) {
                sb.append("  Extra:\n");
                for (String ing : item.getIngredientesExtra()) {
                    appendAjustado(sb, "+ " + ing, cols, SANGRIA_MODIFICADOR, SANGRIA_MODIFICADOR_CONTINUACION);
                }
            }

            // Salsas
            if (!item.getSalsas().isEmpty()) {
                sb.append("  Salsas:\n");
                for (String salsa : item.getSalsas()) {
                    appendAjustado(sb, "* " + salsa, cols, SANGRIA_MODIFICADOR, SANGRIA_MODIFICADOR_CONTINUACION);
                }
            }

            // Subtotal alineado
            sb.append(right(String.format(Locale.getDefault(), "Subtotal: %.2f %s", subtotal, monedaSymbol), cols)).append("\n\n");
        }

        // --- TOTAL --- (suma de las líneas ya redondeadas, igual que el motor de precios)
        total = calculadora.calcularTotal(pedido, preciosIngredientes);
        sb.append(divider).append("\n");
        sb.append(right(String.format(Locale.getDefault(), "TOTAL: %.2f %s", total, monedaSymbol), cols)).append("\n");
        sb.append(divider).append("\n\n");

        sb.append(center("GRACIAS POR SU COMPRA", cols)).append("\n");
        // C6: Usar el nombre del establecimiento configurado, no uno hardcodeado
        sb.append(center(nombreConfig.toUpperCase(), cols)).append("\n");

        // --- FIN ---
        sb.append("\n\n"); // espacios que la impresora necesita
        // El corte lo hará PrinterHelper con ESC/POS

        return new TicketResult(sb.toString(), total);
    }

    /** C7: Abre (o crea) el SharedPreferences cifrado para el contador de pedidos. */
    public static SharedPreferences getSecurePrefs(Context context) {
        return com.softmyc.tpv.data.SecurePreferencesHelper.getCounterPrefs(context);
    }

    /** C7: Abre (o crea) el SharedPreferences cifrado para la configuración de ticket. */
    public static SharedPreferences getSettingsPrefs(Context context) {
        return com.softmyc.tpv.data.SecurePreferencesHelper.getPrinterPrefs(context);
    }

    // ========================
    //  METODOS AUXILIARES
    // ========================

    /** Añade el texto centrado, partido en líneas si no cabe en el papel. */
    private static void appendCentrado(StringBuilder sb, String texto, int width) {
        for (String linea : AjusteLineasTicket.envolver(texto, width, "", "")) {
            sb.append(center(linea, width)).append("\n");
        }
    }

    /** Añade el texto ajustado al ancho del papel; las líneas de continuación llevan sangría. */
    private static void appendAjustado(StringBuilder sb, String texto, int width,
                                       String sangriaPrimera, String sangriaContinuacion) {
        for (String linea : AjusteLineasTicket.envolver(texto, width, sangriaPrimera, sangriaContinuacion)) {
            sb.append(linea).append("\n");
        }
    }

    private static String center(String text, int width) {
        int pad = (width - text.length()) / 2;
        if (pad <= 0) return text;
        return repeat(" ", pad) + text;
    }

    private static String right(String text, int width) {
        int pad = width - text.length();
        if (pad <= 0) return text;
        return repeat(" ", pad) + text;
    }

    /**
     * Genera una cadena repetida un número determinado de veces.
     * Se utiliza para crear espacios de relleno en la alineación del ticket.
     *
     * @param s     Cadena a repetir.
     * @param times Número de repeticiones.
     * @return Cadena resultante con el contenido repetido.
     */
    private static String repeat(String s, int times) {
        if (times <= 0) return "";
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Genera el texto completo del ticket de un pedido online para la impresora de cocina.
     */
    @SuppressWarnings("unchecked")
    public static TicketResult generarTicketOnline(
            Context context,
            Map<String, Object> cliente,
            List<Map<String, Object>> items,
            double total,
            int numeroPedido) {

        StringBuilder sb = new StringBuilder();

        // Cargar ajustes del establecimiento y tamaño de letra
        SharedPreferences settingsPrefs = getSettingsPrefs(context);
        String nombreConfig = settingsPrefs.getString("nombre_restaurante", "MI RESTAURANTE");
        if (nombreConfig == null || nombreConfig.trim().isEmpty()) {
            nombreConfig = "MI RESTAURANTE";
        }
        int tamanioLetra = settingsPrefs.getInt("tamanio_letra", 0);
        int cols = (tamanioLetra == 2) ? 24 : 48;
        String divider = repeat("-", cols);

        // --- CABECERA ---
        sb.append(divider).append("\n");
        sb.append(center("PEDIDO ONLINE N " + numeroPedido, cols)).append("\n");

        String fecha = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date());
        sb.append(center(fecha, cols)).append("\n");
        sb.append(divider).append("\n\n");

        // --- DATOS DEL CLIENTE ---
        if (cliente != null) {
            String nombre = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetString(cliente, "nombre", "Cliente");
            String telefono = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetString(cliente, "telefono", "-");
            String mesa = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetString(cliente, "mesa", "Recogida");

            sb.append("CLIENTE: ").append(nombre).append("\n");
            sb.append("TEL: ").append(telefono).append("\n");
            sb.append("TIPO/MESA: ").append(mesa).append("\n");
            sb.append(divider).append("\n\n");
        }

        // --- LINEAS DE PRODUCTO ---
        if (items != null) {
            for (Map<String, Object> item : items) {
                String nombre = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetString(item, "nombre", null);
                if (nombre == null) nombre = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetString(item, "name", "Producto");
                
                int cantVal;
                if (item.containsKey("cantidad")) {
                    cantVal = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetInt(item, "cantidad", 1);
                } else {
                    cantVal = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetInt(item, "quantity", 1);
                }
                
                double precioVal;
                if (item.containsKey("precio_unitario")) {
                    precioVal = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetDouble(item, "precio_unitario", 0.0);
                } else if (item.containsKey("totalPrice")) {
                    precioVal = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetDouble(item, "totalPrice", 0.0);
                } else {
                    precioVal = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetDouble(item, "basePrice", 0.0);
                }

                double subtotal = cantVal * precioVal;

                appendAjustado(sb, cantVal + " x " + nombre, cols, "", SANGRIA_PRODUCTO);

                // Sin ingredientes
                List<String> quitados = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetStringList(item, "quitados");
                if (!quitados.isEmpty()) {
                    sb.append("  Sin:\n");
                    for (String q : quitados) {
                        appendAjustado(sb, "- " + q, cols, SANGRIA_MODIFICADOR, SANGRIA_MODIFICADOR_CONTINUACION);
                    }
                }

                // Con extras
                List<String> extras = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetStringList(item, "extras");
                if (!extras.isEmpty()) {
                    sb.append("  Extra:\n");
                    for (String ext : extras) {
                        appendAjustado(sb, "+ " + ext, cols, SANGRIA_MODIFICADOR, SANGRIA_MODIFICADOR_CONTINUACION);
                    }
                }

                // Salsas
                List<String> salsas = com.softmyc.tpv.util.TypeSafeJsonHelper.safeGetStringList(item, "salsas");
                if (!salsas.isEmpty()) {
                    sb.append("  Salsas:\n");
                    for (String s : salsas) {
                        appendAjustado(sb, "* " + s, cols, SANGRIA_MODIFICADOR, SANGRIA_MODIFICADOR_CONTINUACION);
                    }
                }

                sb.append(right(String.format("Subtotal: %.2f EUR", subtotal), cols)).append("\n\n");
            }
        }

        // --- TOTAL ---
        sb.append(divider).append("\n");
        sb.append(right(String.format("TOTAL: %.2f EUR", total), cols)).append("\n");
        sb.append(divider).append("\n\n");

        sb.append(center("PAGADO ONLINE - PREPARAR EN COCINA", cols)).append("\n");
        sb.append(center(nombreConfig.toUpperCase(), cols)).append("\n");

        // --- FIN ---
        sb.append("\n\n");

        return new TicketResult(sb.toString(), total);
    }

}
