package com.softmyc.tpv.presentation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.Producto;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter para la lista de productos en ListaProductosActivity.
 * Muestra código, nombre, ingredientes y precio de cada producto.
 *
 * Extraído de la inner class ProductoAdapter de ListaProductosActivity
 * para mejorar la cohesión y permitir reutilización futura.
 *
 * Usa el patrón ViewHolder para optimizar el rendimiento del scroll.
 */
public class ProductoAdapter extends ArrayAdapter<Producto> {

    public ProductoAdapter(Context context, List<Producto> productos) {
        super(context, 0, new ArrayList<>(productos));
    }

    static class ViewHolder {
        TextView txtNombre;
        TextView txtIng;
        TextView txtPrecio;
        TextView txtInicial;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_producto_card, parent, false);
            holder = new ViewHolder();
            holder.txtNombre  = convertView.findViewById(R.id.txtNombre);
            holder.txtIng     = convertView.findViewById(R.id.txtIngredientes);
            holder.txtPrecio  = convertView.findViewById(R.id.txtPrecio);
            holder.txtInicial = convertView.findViewById(R.id.txtInicial);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Producto p = getItem(position);
        if (p == null) return convertView;

        if (holder.txtNombre  != null) holder.txtNombre.setText(p.getNombre());
        if (holder.txtPrecio  != null) holder.txtPrecio.setText(String.format("%.2f€", p.getPrecio()));
        if (holder.txtInicial != null) holder.txtInicial.setText(String.valueOf(p.getCodigo()));

        if (holder.txtIng != null) {
            List<String> ings = p.getIngredientes();
            if (ings != null && !ings.isEmpty()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    holder.txtIng.setText(String.join(", ", ings));
                } else {
                    holder.txtIng.setText(ings.toString().replace("[", "").replace("]", ""));
                }
            } else {
                holder.txtIng.setText("");
            }
        }
        return convertView;
    }
}
