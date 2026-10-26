package com.softmyc.tpv.presentation;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.repo.ProductoRepositoryRoom;
import com.softmyc.tpv.domain.StockEdit;
import com.softmyc.tpv.domain.StockLevel;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;
import com.softmyc.tpv.domain.repository.ProductoRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Control de existencias por producto: semáforo, ajuste +/-, edición numérica y filtro de críticos. */
public class StockActivity extends BaseActivity {

    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();
    private final List<ProductoCatalogo> allProducts = new ArrayList<>();
    private final List<ProductoCatalogo> visibleProducts = new ArrayList<>();

    private StockAdapter adapter;
    private EditText edtSearch;
    private CheckBox chkOnlyCritical;
    private TextView txtEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock);
        setupBackButton();

        edtSearch = findViewById(R.id.edtBuscarStock);
        chkOnlyCritical = findViewById(R.id.chkSoloCriticos);
        txtEmpty = findViewById(R.id.txtStockVacio);

        RecyclerView recycler = findViewById(R.id.recyclerStock);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StockAdapter();
        recycler.setAdapter(adapter);

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });
        chkOnlyCritical.setOnCheckedChangeListener((button, checked) -> applyFilter());

        loadProducts();
    }

    @Override
    protected void onDestroy() {
        diskExecutor.shutdown();
        super.onDestroy();
    }

    private void loadProducts() {
        final android.content.Context appContext = getApplicationContext();
        diskExecutor.execute(() -> {
            try {
                List<ProductoCatalogo> loaded = new ProductoRepositoryRoom(appContext).listarTodos();
                runOnUiThread(() -> {
                    allProducts.clear();
                    allProducts.addAll(loaded);
                    applyFilter();
                });
            } catch (Exception e) {
                android.util.Log.e("StockActivity", "No se pudo cargar el catálogo", e);
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.no_se_pudo_cargar_el_stock), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void applyFilter() {
        String query = edtSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean onlyCritical = chkOnlyCritical.isChecked();
        visibleProducts.clear();
        for (ProductoCatalogo p : allProducts) {
            StockLevel level = StockLevel.of(p.stock);
            if (onlyCritical && level != StockLevel.OUT && level != StockLevel.LOW) continue;
            boolean matches = query.isEmpty()
                    || (p.nombre != null && p.nombre.toLowerCase(Locale.ROOT).contains(query))
                    || String.valueOf(p.codigo).contains(query);
            if (matches) visibleProducts.add(p);
        }
        adapter.notifyDataSetChanged();
        txtEmpty.setVisibility(visibleProducts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Sustituye en las listas (solo desde el hilo principal) el producto con ese código por la copia con otro stock. */
    private void cambiarStockLocal(int codigo, int stock) {
        reemplazarStock(allProducts, codigo, stock);
        reemplazarStock(visibleProducts, codigo, stock);
    }

    private static void reemplazarStock(List<ProductoCatalogo> lista, int codigo, int stock) {
        for (int i = 0; i < lista.size(); i++) {
            ProductoCatalogo p = lista.get(i);
            if (p.codigo == codigo) lista.set(i, p.conStock(stock));
        }
    }

    private void updateStock(ProductoCatalogo product, int newStock) {
        if (newStock == product.stock) return;
        final int previous = product.stock;
        cambiarStockLocal(product.codigo, newStock);
        final ProductoRepository productos = new ProductoRepositoryRoom(getApplicationContext());
        diskExecutor.execute(() -> {
            try {
                productos.actualizarStock(product.codigo, newStock);
            } catch (Exception e) {
                android.util.Log.e("StockActivity", "No se pudo guardar el stock de " + product.codigo, e);
                runOnUiThread(() -> {
                    cambiarStockLocal(product.codigo, previous);
                    applyFilter();
                    Toast.makeText(this, getString(R.string.no_se_pudo_guardar_el_stock), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private int levelColor(StockLevel level) {
        int res;
        switch (level) {
            case OK: res = R.color.stock_ok; break;
            case LOW: res = R.color.stock_low; break;
            case OUT: res = R.color.stock_out; break;
            default: res = R.color.stock_untracked; break;
        }
        return ContextCompat.getColor(this, res);
    }

    private static String levelLabel(StockLevel level) {
        switch (level) {
            case OK: return "Stock suficiente";
            case LOW: return "Stock bajo";
            case OUT: return "Agotado";
            default: return "Sin control de stock";
        }
    }

    private class StockAdapter extends RecyclerView.Adapter<StockAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stock, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(visibleProducts.get(position));
        }

        @Override
        public int getItemCount() {
            return visibleProducts.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            private final View semaforo;
            private final TextView name;
            private final TextView status;
            private final EditText quantity;
            private final Button minus;
            private final Button plus;

            Holder(@NonNull View itemView) {
                super(itemView);
                semaforo = itemView.findViewById(R.id.viewSemaforo);
                name = itemView.findViewById(R.id.txtStockNombre);
                status = itemView.findViewById(R.id.txtStockEstado);
                quantity = itemView.findViewById(R.id.edtStockCantidad);
                minus = itemView.findViewById(R.id.btnStockMenos);
                plus = itemView.findViewById(R.id.btnStockMas);
            }

            void bind(ProductoCatalogo product) {
                StockLevel level = StockLevel.of(product.stock);
                name.setText(String.format(Locale.getDefault(), "#%d  %s", product.codigo, product.nombre));
                status.setText(levelLabel(level));
                semaforo.setBackgroundColor(levelColor(level));
                quantity.setText(product.stock >= 0 ? String.valueOf(product.stock) : "");

                minus.setOnClickListener(v -> commit(product, StockEdit.decrement(product.stock)));
                plus.setOnClickListener(v -> commit(product, StockEdit.increment(product.stock)));
                quantity.setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        commit(product, StockEdit.parse(quantity.getText().toString()));
                        return true;
                    }
                    return false;
                });
                quantity.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) commit(product, StockEdit.parse(quantity.getText().toString()));
                });
            }

            private void commit(ProductoCatalogo product, int newStock) {
                updateStock(product, newStock);
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
            }
        }
    }
}
