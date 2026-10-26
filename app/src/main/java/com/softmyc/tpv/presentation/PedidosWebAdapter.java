package com.softmyc.tpv.presentation;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.softmyc.tpv.R;
import com.softmyc.tpv.util.TypeSafeJsonHelper;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PedidosWebAdapter extends RecyclerView.Adapter<PedidosWebAdapter.OrderViewHolder> {

    private static final String TAG = "PedidosWebAdapter";

    public interface OrderActionListener {
        void onMarkStatus(Map<String, Object> doc, String newStatus);
        void onPrintTicket(Map<String, Object> doc);
    }

    private final List<Map<String, Object>> docs;
    private final OrderActionListener listener;

    public PedidosWebAdapter(List<Map<String, Object>> docs, OrderActionListener listener) {
        this.docs = docs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pedido_web, parent, false);
        return new OrderViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        try {
            Map<String, Object> doc = docs.get(position);

            String rawSessionId = (String) doc.get("stripeSessionId");
            String orderId = rawSessionId;
            if (orderId == null || orderId.isEmpty()) {
                orderId = (String) doc.get("_id");
            }
            if (orderId != null && orderId.length() > 8) {
                orderId = orderId.substring(orderId.length() - 8).toUpperCase();
            }

            String origen = TypeSafeJsonHelper.safeGetString(doc, "origen", "");
            if (origen.isEmpty()) {
                if (rawSessionId != null && rawSessionId.startsWith("TPV-")) {
                    origen = "tpv";
                } else {
                    origen = "web";
                }
            }
            String tagOrigen = origen.equalsIgnoreCase("tpv") ? " • TPV" : " • WEB";

            holder.tvOrderId.setText(holder.tvOrderId.getContext().getString(R.string.pedidos_web_id_fmt, orderId != null ? orderId : "", tagOrigen));

            String status = (String) doc.get("estado");
            if (status == null) status = "pendiente";

            // Mostrar u ocultar el badge de retraso si aplica
            if (status.toLowerCase().contains("retrasado")) {
                holder.tvOrderStatus.setVisibility(View.VISIBLE);
                holder.tvOrderStatus.setText(holder.itemView.getContext().getString(R.string.retrasado));
            } else {
                holder.tvOrderStatus.setVisibility(View.GONE);
            }

            Map<String, Object> cliente = TypeSafeJsonHelper.safeGetMap(doc, "cliente");
            String nombre;
            String telefono = "";
            String mesa = "";
            if (cliente != null) {
                nombre = TypeSafeJsonHelper.safeGetString(cliente, "nombre", "Cliente Web");
                telefono = TypeSafeJsonHelper.safeGetString(cliente, "telefono", "");
                mesa = TypeSafeJsonHelper.safeGetString(cliente, "mesa", "");
            } else {
                nombre = TypeSafeJsonHelper.safeGetString(doc, "cliente", "Cliente Web");
            }
            
            String customerText = holder.tvCustomerInfo.getContext().getString(R.string.pedidos_web_cliente_fmt, nombre);
            if (!telefono.isEmpty()) {
                customerText = holder.tvCustomerInfo.getContext().getString(R.string.pedidos_web_cliente_telefono_fmt, nombre, telefono);
            }
            holder.tvCustomerInfo.setText(customerText);

            // Hora de recogida en el badge superior derecho
            if (mesa.isEmpty()) {
                holder.tvOrderTime.setText(holder.itemView.getContext().getString(R.string.recogida_inmediata));
            } else {
                holder.tvOrderTime.setText(mesa);
            }

            double totalVal = TypeSafeJsonHelper.safeGetDouble(doc, "total", 0.0);
            holder.tvOrderTotal.setText(String.format(java.util.Locale.US, "%.2f€", totalVal));

            // Format items list
            List<Map<String, Object>> itemsList = TypeSafeJsonHelper.safeGetMapList(doc, "items");
            if (itemsList.isEmpty()) {
                itemsList = TypeSafeJsonHelper.safeGetMapList(doc, "cart");
            }
            StringBuilder itemsText = new StringBuilder();
            if (!itemsList.isEmpty()) {
                for (Map<String, Object> item : itemsList) {
                    String name = TypeSafeJsonHelper.safeGetString(item, "nombre", null);
                    if (name == null) name = TypeSafeJsonHelper.safeGetString(item, "name", "Producto");
                    int qty;
                    if (item.containsKey("cantidad")) {
                        qty = TypeSafeJsonHelper.safeGetInt(item, "cantidad", 1);
                    } else {
                        qty = TypeSafeJsonHelper.safeGetInt(item, "quantity", 1);
                    }
                    
                    itemsText.append("• ").append(qty).append("x ").append(name);
                    
                    List<String> extras = TypeSafeJsonHelper.safeGetStringList(item, "extras");
                    if (!extras.isEmpty()) {
                        itemsText.append(" (+").append(android.text.TextUtils.join(", ", extras)).append(")");
                    }
                    List<String> salsas = TypeSafeJsonHelper.safeGetStringList(item, "salsas");
                    if (!salsas.isEmpty()) {
                        itemsText.append(" [Salsas: ").append(android.text.TextUtils.join(", ", salsas)).append("]");
                    }
                    List<String> quitados = TypeSafeJsonHelper.safeGetStringList(item, "quitados");
                    if (!quitados.isEmpty()) {
                        itemsText.append(" (SIN: ").append(android.text.TextUtils.join(", ", quitados)).append(")");
                    }
                    itemsText.append("\n");
                }
            } else {
                itemsText.append("• 1x Pedido Bocatería La Nube");
            }
            holder.tvOrderItems.setText(itemsText.toString().trim());

            // Configurar botones dinámicamente según el estado del pedido
            String statusLower = status.toLowerCase();
            if (statusLower.contains("listo")) {
                // Estado: Listo -> Botón principal es "ENTREGADO" (color azul) y ocultamos "+15 MIN"
                holder.btnMarkReady.setText(holder.itemView.getContext().getString(R.string.entregado));
                holder.btnMarkReady.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        holder.itemView.getContext().getResources().getColor(R.color.primary_brand)
                ));
                holder.btnMarkReady.setOnClickListener(v -> listener.onMarkStatus(doc, "Entregado"));
                holder.btnMarkDelay.setVisibility(View.GONE);
            } else {
                // Estado: Pendiente -> Botón principal es "¡LISTO!" (color verde) y mostramos "+15 MIN"
                holder.btnMarkReady.setText(holder.itemView.getContext().getString(R.string.listo));
                holder.btnMarkReady.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#10b981")
                ));
                holder.btnMarkReady.setOnClickListener(v -> listener.onMarkStatus(doc, "¡Listo para recoger!"));
                holder.btnMarkDelay.setVisibility(View.VISIBLE);
                holder.btnMarkDelay.setOnClickListener(v -> listener.onMarkStatus(doc, "Retrasado (+15 min)"));
            }
            
            holder.btnPrintTicket.setOnClickListener(v -> listener.onPrintTicket(doc));
        } catch (Exception e) {
            Log.e(TAG, "Error pintando el pedido en posicion " + position, e);
            holder.tvOrderId.setText(holder.itemView.getContext().getString(R.string.pedido_corrupto));
            holder.tvCustomerInfo.setText(holder.tvCustomerInfo.getContext().getString(R.string.pedidos_web_error_datos_fmt, e.getMessage()));
            holder.tvOrderItems.setText(holder.itemView.getContext().getString(R.string.no_se_pudo_renderizar_este_pedido_de_for));
            holder.tvOrderTotal.setText("0.00€");
            holder.tvOrderTime.setText("N/A");
            holder.tvOrderStatus.setVisibility(View.GONE);
            holder.btnMarkReady.setVisibility(View.GONE);
            holder.btnMarkDelay.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return docs.size();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvOrderStatus, tvCustomerInfo, tvOrderTime, tvOrderItems, tvOrderTotal;
        MaterialButton btnMarkReady, btnMarkDelay, btnPrintTicket;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvOrderStatus = itemView.findViewById(R.id.tvOrderStatus);
            tvCustomerInfo = itemView.findViewById(R.id.tvCustomerInfo);
            tvOrderTime = itemView.findViewById(R.id.tvOrderTime);
            tvOrderItems = itemView.findViewById(R.id.tvOrderItems);
            tvOrderTotal = itemView.findViewById(R.id.tvOrderTotal);
            btnMarkReady = itemView.findViewById(R.id.btnMarkReady);
            btnMarkDelay = itemView.findViewById(R.id.btnMarkDelay);
            btnPrintTicket = itemView.findViewById(R.id.btnPrintTicket);
        }
    }
}
