package com.softmyc.tpv.presentation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.softmyc.tpv.R;

import java.util.ArrayList;
import java.util.List;

/** Lista sencilla de filas (título, detalle, valor a la derecha) para las pantallas del escandallo. */
class FilaEscandalloAdapter extends RecyclerView.Adapter<FilaEscandalloAdapter.Fila> {

    static final class Item {
        final String titulo;
        final String detalle;
        final String valor;
        final int colorValor;
        final Runnable alPulsar;
        final Runnable alMantener;

        Item(String titulo, String detalle, String valor, int colorValor, Runnable alPulsar, Runnable alMantener) {
            this.titulo = titulo;
            this.detalle = detalle;
            this.valor = valor;
            this.colorValor = colorValor;
            this.alPulsar = alPulsar;
            this.alMantener = alMantener;
        }
    }

    private final List<Item> items = new ArrayList<>();

    void mostrar(List<Item> nuevos) {
        items.clear();
        items.addAll(nuevos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Fila onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Fila(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_escandallo, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Fila h, int position) {
        Item item = items.get(position);
        h.titulo.setText(item.titulo);
        h.detalle.setText(item.detalle);
        h.detalle.setVisibility(item.detalle == null || item.detalle.isEmpty() ? View.GONE : View.VISIBLE);
        h.valor.setText(item.valor);
        h.valor.setTextColor(item.colorValor);
        h.itemView.setOnClickListener(v -> { if (item.alPulsar != null) item.alPulsar.run(); });
        h.itemView.setOnLongClickListener(v -> {
            if (item.alMantener == null) return false;
            item.alMantener.run();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Fila extends RecyclerView.ViewHolder {
        final TextView titulo;
        final TextView detalle;
        final TextView valor;

        Fila(View v) {
            super(v);
            titulo = v.findViewById(R.id.txtTitulo);
            detalle = v.findViewById(R.id.txtDetalle);
            valor = v.findViewById(R.id.txtValor);
        }
    }
}
