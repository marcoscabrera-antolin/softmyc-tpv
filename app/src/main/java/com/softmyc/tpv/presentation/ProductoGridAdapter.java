package com.softmyc.tpv.presentation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.Producto;

public class ProductoGridAdapter extends ListAdapter<Producto, ProductoGridAdapter.ViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(Producto producto);
    }

    private final OnProductClickListener listener;

    public ProductoGridAdapter(OnProductClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Producto> DIFF_CALLBACK = new DiffUtil.ItemCallback<Producto>() {
        @Override
        public boolean areItemsTheSame(@NonNull Producto oldItem, @NonNull Producto newItem) {
            return oldItem.getCodigo() == newItem.getCodigo();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Producto oldItem, @NonNull Producto newItem) {
            return oldItem.getNombre().equals(newItem.getNombre()) &&
                   oldItem.getPrecio() == newItem.getPrecio() &&
                   (oldItem.getCategoria() == null || oldItem.getCategoria().equals(newItem.getCategoria()));
        }
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_producto_grid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtId;
        private final TextView txtName;
        private final TextView txtPrice;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtId = itemView.findViewById(R.id.txtProductId);
            txtName = itemView.findViewById(R.id.txtProductName);
            txtPrice = itemView.findViewById(R.id.txtProductPrice);
        }

        public void bind(Producto producto, OnProductClickListener listener) {
            txtId.setText(String.format(java.util.Locale.getDefault(), "#%02d", producto.getCodigo()));
            txtName.setText(producto.getNombre());
            if (!"UD".equalsIgnoreCase(producto.getUnidadMedida())) {
                txtPrice.setText(String.format(java.util.Locale.getDefault(), "%.2f€ / %s",
                        producto.getPrecio(), producto.getUnidadMedida().toLowerCase()));
            } else {
                txtPrice.setText(String.format(java.util.Locale.getDefault(), "%.2f€", producto.getPrecio()));
            }

            itemView.setOnClickListener(v -> {
                try {
                    if (listener != null && producto != null) {
                        listener.onProductClick(producto);
                    }
                } catch (Throwable t) {
                    android.util.Log.e("ProductoGridAdapter", "Error al pulsar producto", t);
                }
            });
        }
    }
}
