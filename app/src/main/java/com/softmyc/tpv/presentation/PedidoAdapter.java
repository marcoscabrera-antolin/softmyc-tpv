package com.softmyc.tpv.presentation;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.PedidoItem;

import java.util.List;

/**
 * Adapter para la lista del pedido activo en PedidoActivity.
 * Muestra nombre del producto, modificaciones (sin/extras/salsas) y subtotal.
 *
 * Extraído de la inner class PedidoSimpleAdapter de PedidoActivity
 * para mejorar la cohesión y permitir reutilización.
 */
public class PedidoAdapter extends ArrayAdapter<CharSequence> {

    private final List<String> totales;

    public PedidoAdapter(Context context, List<CharSequence> detalles, List<String> totales) {
        super(context, R.layout.item_pedido_simple, detalles);
        this.totales = totales;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View itemView = LayoutInflater.from(getContext())
                .inflate(R.layout.item_pedido_simple, parent, false);
        TextView textoPedido = itemView.findViewById(R.id.textoPedido);
        TextView textoTotal  = itemView.findViewById(R.id.textoTotal);
        textoPedido.setText(getItem(position));
        textoTotal.setText(totales.get(position));
        return itemView;
    }

    /**
     * Construye la lista de CharSequence y totales a partir de una lista de PedidoItem.
     * Lógica de formateo HTML extraída de PedidoActivity.actualizarLista().
     *
     * @param lista         items del pedido.
     * @param calcularExtra función para calcular el precio de los extras de un item.
     * @return array [detalles, totales] listo para el adapter.
     */
    public static Data buildFromPedido(List<PedidoItem> lista, ExtraCalculator calcularExtra) {
        List<CharSequence> detalles = new java.util.ArrayList<>();
        List<String> totalesStr = new java.util.ArrayList<>();

        for (PedidoItem item : lista) {
            StringBuilder d = new StringBuilder();
            if (!"UD".equalsIgnoreCase(item.getUnidadMedida()) && item.getCantidadMedida() > 0) {
                d.append(String.format(java.util.Locale.getDefault(), "<b>%.3f %s</b> x <b>%s</b><br>",
                        item.getCantidadMedida(), item.getUnidadMedida().toLowerCase(), item.getNombre()));
            } else {
                d.append(item.getCantidad()).append(" x <b>").append(item.getNombre()).append("</b><br>");
            }

            if (item.getTaraKg() > 0.0) {
                d.append("&nbsp;&nbsp;<b>⚖️ Tara:</b> -")
                        .append(String.format(java.util.Locale.getDefault(), "%.3f kg<br>", item.getTaraKg()));
            }

            if (!item.getIngredientesQuitados().isEmpty()) {
                d.append("<b>&nbsp;&nbsp;Sin:</b><br>");
                for (String ing : item.getIngredientesQuitados()) {
                    d.append("&nbsp;&nbsp;&nbsp;&nbsp;↳ ").append(ing).append("<br>");
                }
            }
            if (!item.getIngredientesExtra().isEmpty()) {
                d.append("<b>&nbsp;&nbsp;Extras / Suplementos:</b><br>");
                for (String ing : item.getIngredientesExtra()) {
                    d.append("&nbsp;&nbsp;&nbsp;&nbsp;↳ ").append(ing).append("<br>");
                }
            }
            if (!item.getSalsas().isEmpty()) {
                d.append("<b>&nbsp;&nbsp;Salsas:</b><br>");
                for (String s : item.getSalsas()) {
                    d.append("&nbsp;&nbsp;&nbsp;&nbsp;↳ ").append(s).append("<br>");
                }
            }

            double subtotal;
            if (item.getProducto() != null && item.getProducto().isWeightBased()) {
                double net = Math.max(0.0, item.getCantidadMedida() - item.getTaraKg());
                subtotal = (item.getProducto().getPrecio() * net) + (calcularExtra != null ? calcularExtra.calcular(item) : 0.0);
            } else if (item.getProducto() != null && item.getProducto().isTimeBased()) {
                double qty = item.getCantidadMedida() > 0 ? item.getCantidadMedida() : item.getCantidad();
                subtotal = (item.getProducto().getPrecio() * qty) + (calcularExtra != null ? calcularExtra.calcular(item) : 0.0);
            } else {
                subtotal = (item.getProducto().getPrecio() + (calcularExtra != null ? calcularExtra.calcular(item) : 0.0))
                        * item.getCantidad();
            }

            detalles.add(Html.fromHtml(d.toString()));
            totalesStr.add(String.format(java.util.Locale.getDefault(), "%.2f€", subtotal));
        }
        return new Data(detalles, totalesStr);
    }

    /** Callback para calcular el precio extra de un item (evita dependencia directa del ViewModel). */
    public interface ExtraCalculator {
        double calcular(PedidoItem item);
    }

    /** Datos pre-calculados listos para construir el adapter. */
    public static class Data {
        public final List<CharSequence> detalles;
        public final List<String> totales;
        public Data(List<CharSequence> detalles, List<String> totales) {
            this.detalles = detalles;
            this.totales  = totales;
        }
    }
}
