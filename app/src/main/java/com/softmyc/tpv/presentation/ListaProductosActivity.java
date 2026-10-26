package com.softmyc.tpv.presentation;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.*;
import com.softmyc.tpv.data.repo.ProductoRepositoryRoom;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;
import com.softmyc.tpv.domain.repository.ProductoRepository;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ListaProductosActivity extends BaseActivity {

    private ListView listView;
    private TextView txtTitulo;
    private List<Producto> listaProductos = new ArrayList<>();
    private ProductoAdapter adapter;

    // Botones de categoría
    private Button btnBocadillos, btnHamburguesas, btnOtros, btnSalsas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_productos);

        setupBackButton();
        inicializarVistas();

        // Configurar botones para cambiar de categoría
        btnBocadillos.setOnClickListener(v -> cargarCategoria("bocadillo"));
        btnHamburguesas.setOnClickListener(v -> cargarCategoria("hamburguesa"));
        btnOtros.setOnClickListener(v -> cargarCategoria("otros"));
        btnSalsas.setOnClickListener(v -> cargarCategoria("salsas"));

        // Cargar por defecto lo que venga del Intent, o Hamburguesas si no hay nada
        String catInicial = getIntent().getStringExtra("CATEGORIA");
        if (catInicial == null) catInicial = "hamburguesa";

        cargarCategoria(catInicial);
    }

    private void inicializarVistas() {
        listView = findViewById(R.id.listaProductos);
        txtTitulo = findViewById(R.id.txtTituloCategoria);

        btnBocadillos = findViewById(R.id.btnCatBocadillos);
        btnHamburguesas = findViewById(R.id.btnCatHamburguesas);
        btnOtros = findViewById(R.id.btnCatOtros);
        btnSalsas = findViewById(R.id.btnCatSalsas);
    }

    private void cargarCategoria(String categoria) {
        String catNormTarget = com.softmyc.tpv.util.CategoryUtils.capitalizeCategory(categoria);

        actualizarEstiloBotones(categoria.toLowerCase().trim());
        txtTitulo.setText(getString(R.string.carta));

        AppExecutors.disco().execute(() -> {
            ProductoRepository productos = new ProductoRepositoryRoom(getApplicationContext());
            List<ProductoCatalogo> prodsLocal = productos.listarTodos();
            List<Producto> lista = new ArrayList<>();
            for (ProductoCatalogo pl : prodsLocal) {
                if (pl.categoria != null) {
                    String catProdNorm = com.softmyc.tpv.util.CategoryUtils.capitalizeCategory(pl.categoria);
                    if (catProdNorm.equalsIgnoreCase(catNormTarget)) {
                        lista.add(new Producto(pl.codigo, pl.nombre, pl.precio, catProdNorm, pl.ingredientes));
                    }
                }
            }
            if (catNormTarget.equalsIgnoreCase("Salsas")) {
                int salsaIdx = 1;
                for (String nombreSalsa : productos.listarNombresSalsas()) {
                    lista.add(new Producto(salsaIdx++, nombreSalsa, 0.0, "Salsas", new ArrayList<>()));
                }
            }
            Collections.sort(lista, Comparator.comparingInt(Producto::getCodigo));

            AppExecutors.enUiSiViva(this, () -> {
                listaProductos = lista;
                if (adapter == null) {
                    adapter = new ProductoAdapter(this, listaProductos);
                    listView.setAdapter(adapter);
                } else {
                    adapter.clear();
                    adapter.addAll(listaProductos);
                    adapter.notifyDataSetChanged();
                }
            });
        });
    }

    private void actualizarEstiloBotones(String categoriaActiva) {
        resetBoton(btnBocadillos);
        resetBoton(btnHamburguesas);
        resetBoton(btnOtros);
        resetBoton(btnSalsas);

        switch (categoriaActiva) {
            case "bocadillo":
                resaltarBoton(btnBocadillos);
                break;
            case "hamburguesa":
                resaltarBoton(btnHamburguesas);
                break;
            case "otros":
                resaltarBoton(btnOtros);
                break;
            case "salsas":
                resaltarBoton(btnSalsas);
                break;
        }
    }

    private void resetBoton(Button btn) {
        if (btn != null) btn.setAlpha(0.5f);
    }

    private void resaltarBoton(Button btn) {
        if (btn != null) btn.setAlpha(1.0f);
    }
}