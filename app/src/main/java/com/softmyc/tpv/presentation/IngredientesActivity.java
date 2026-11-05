package com.softmyc.tpv.presentation;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.NombreNormalizado;
import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.UnidadIngrediente;
import com.softmyc.tpv.domain.usecase.DiagnosticoEscandallo;
import com.softmyc.tpv.presentation.viewmodel.EscandalloViewModel;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Escandallo, parte 1: ingredientes. Alta y edición (nombre, unidad, precio), recuento de stock y avisos de lo
 * que le falta al escandallo. Desde aquí se abren las recetas y se importa un CSV.
 */
public class IngredientesActivity extends BaseActivity {

    static final int PEDIR_CSV = 41;
    private static final int COLOR_OK = 0xFF34D399;
    private static final int COLOR_BAJO = 0xFFFBBF24;
    private static final int COLOR_AGOTADO = 0xFFF87171;
    private static final int COLOR_NEUTRO = 0xFF94A3B8;

    private EscandalloViewModel viewModel;
    private final FilaEscandalloAdapter adaptador = new FilaEscandalloAdapter();
    private TextView txtDiagnostico;
    private TextView txtVacio;
    private EditText edtBuscar;
    private EscandalloViewModel.Estado ultimo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ingredientes);
        setupBackButton();

        RecyclerView lista = findViewById(R.id.recyclerIngredientes);
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adaptador);
        txtDiagnostico = findViewById(R.id.txtDiagnostico);
        txtVacio = findViewById(R.id.txtIngredientesVacio);
        edtBuscar = findViewById(R.id.edtBuscarIngrediente);

        findViewById(R.id.btnAbrirRecetas).setOnClickListener(v -> startActivity(new Intent(this, RecetasActivity.class)));
        findViewById(R.id.btnNuevoIngrediente).setOnClickListener(v -> editar(null));
        findViewById(R.id.btnImportarEscandallo).setOnClickListener(v -> elegirCsv());
        txtDiagnostico.setOnClickListener(v -> mostrarDiagnostico());
        edtBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { pintar(); }
        });

        viewModel = new ViewModelProvider(this).get(EscandalloViewModel.class);
        viewModel.estado().observe(this, e -> { ultimo = e; pintar(); });
        viewModel.mensaje().observe(this, m -> {
            if (m == null) return;
            Toast.makeText(this, m, Toast.LENGTH_LONG).show();
            viewModel.mensajeLeido();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.cargar();
    }

    private void pintar() {
        if (ultimo == null) return;
        String filtro = NombreNormalizado.de(edtBuscar.getText().toString());
        List<FilaEscandalloAdapter.Item> filas = new ArrayList<>();
        for (Ingrediente i : ultimo.ingredientes) {
            if (!filtro.isEmpty() && !i.nombreNormalizado().contains(filtro)) continue;
            filas.add(new FilaEscandalloAdapter.Item(i.nombre, detalle(i), valorStock(i), colorStock(i),
                    () -> editar(i), () -> confirmarBorrado(i)));
        }
        adaptador.mostrar(filas);
        txtVacio.setVisibility(ultimo.ingredientes.isEmpty() ? View.VISIBLE : View.GONE);

        DiagnosticoEscandallo d = ultimo.diagnostico;
        int avisos = d.productosSinReceta.size() + d.ingredientesSinCasar.size() + d.extrasSinConsumo.size()
                + d.ingredientesSinPrecio.size();
        txtDiagnostico.setVisibility(avisos == 0 ? View.GONE : View.VISIBLE);
        txtDiagnostico.setText(getString(R.string.escandallo_avisos_fmt, avisos));
    }

    private String detalle(Ingrediente i) {
        String precio = FormatoEscandallo.precio(i);
        String d = precio != null ? precio : getString(R.string.escandallo_sin_precio);
        if (i.stock != null && i.stock < 0) {
            d += " · " + getString(R.string.escandallo_descuadre_fmt, FormatoEscandallo.cantidad(i.stock, i.unidad));
        }
        return d;
    }

    private String valorStock(Ingrediente i) {
        return i.controlaStock() ? FormatoEscandallo.cantidad(i.stockVisible(), i.unidad) : getString(R.string.escandallo_sin_control);
    }

    private static int colorStock(Ingrediente i) {
        if (!i.controlaStock()) return COLOR_NEUTRO;
        if (i.stockVisible() <= 0) return COLOR_AGOTADO;
        // "Bajo": menos de 1 kg/L o menos de 10 unidades.
        double umbral = i.unidad == UnidadIngrediente.UD ? 10 : 1000;
        return i.stockVisible() < umbral ? COLOR_BAJO : COLOR_OK;
    }

    /** Diálogo de alta (ingrediente null) o edición. */
    private void editar(@Nullable Ingrediente actual) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad / 2, pad, 0);

        EditText nombre = campo(getString(R.string.escandallo_nombre), InputType.TYPE_CLASS_TEXT);
        RadioGroup unidad = new RadioGroup(this);
        unidad.setOrientation(RadioGroup.HORIZONTAL);
        for (UnidadIngrediente u : UnidadIngrediente.values()) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(u == UnidadIngrediente.G ? "Gramos" : u == UnidadIngrediente.ML ? "Mililitros" : "Unidades");
            rb.setTag(u);
            unidad.addView(rb);
        }
        EditText precio = campo(getString(R.string.escandallo_precio_compra), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox controlar = new CheckBox(this);
        controlar.setText(R.string.escandallo_controlar_stock);
        EditText stock = campo(getString(R.string.escandallo_stock_actual), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);

        UnidadIngrediente u0 = actual != null ? actual.unidad : UnidadIngrediente.G;
        for (int k = 0; k < unidad.getChildCount(); k++) {
            RadioButton rb = (RadioButton) unidad.getChildAt(k);
            if (rb.getTag() == u0) rb.setChecked(true);
        }
        if (actual != null) {
            nombre.setText(actual.nombre);
            if (actual.precioCompra != null) precio.setText(FormatoEscandallo.numero(actual.precioCompra));
            controlar.setChecked(actual.controlaStock());
            if (actual.stock != null) stock.setText(FormatoEscandallo.numero(actual.stock));
        }
        stock.setEnabled(controlar.isChecked());
        controlar.setOnCheckedChangeListener((b, marcado) -> stock.setEnabled(marcado));

        form.addView(nombre);
        form.addView(unidad);
        form.addView(precio);
        form.addView(controlar);
        form.addView(stock);

        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setTitle(actual == null ? R.string.escandallo_nuevo_ingrediente : R.string.escandallo_editar_ingrediente)
                .setView(form)
                .setPositiveButton(R.string.guardar, null)
                .setNegativeButton(R.string.cancelar, null)
                .create();
        dialogo.setOnShowListener(d -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = nombre.getText().toString().trim();
            if (n.isEmpty()) { nombre.setError(getString(R.string.escandallo_campo_obligatorio)); return; }
            Double p = FormatoEscandallo.leer(precio.getText().toString());
            if (p != null && p < 0) { precio.setError(getString(R.string.escandallo_valor_no_valido)); return; }
            Double s = controlar.isChecked() ? FormatoEscandallo.leer(stock.getText().toString()) : null;
            if (controlar.isChecked() && s == null) { stock.setError(getString(R.string.escandallo_valor_no_valido)); return; }
            View marcado = unidad.findViewById(unidad.getCheckedRadioButtonId());
            UnidadIngrediente un = marcado != null ? (UnidadIngrediente) marcado.getTag() : UnidadIngrediente.G;
            boolean cambiaStock = actual == null ? s != null : !java.util.Objects.equals(actual.stock, s);
            viewModel.guardarIngrediente(new Ingrediente(actual != null ? actual.id : 0, n, un, null, p), s, cambiaStock);
            dialogo.dismiss();
        }));
        dialogo.show();
    }

    private EditText campo(String pista, int tipo) {
        EditText e = new EditText(this);
        e.setHint(pista);
        e.setInputType(tipo);
        e.setMaxLines(1);
        return e;
    }

    private void confirmarBorrado(Ingrediente i) {
        new AlertDialog.Builder(this)
                .setTitle(i.nombre)
                .setMessage(R.string.escandallo_confirmar_borrado)
                .setPositiveButton(R.string.eliminar, (d, w) -> viewModel.eliminarIngrediente(i.id))
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private void mostrarDiagnostico() {
        if (ultimo == null) return;
        DiagnosticoEscandallo d = ultimo.diagnostico;
        StringBuilder sb = new StringBuilder();
        seccion(sb, getString(R.string.escandallo_diag_sin_receta), d.productosSinReceta);
        seccion(sb, getString(R.string.escandallo_diag_sin_casar), d.ingredientesSinCasar);
        seccion(sb, getString(R.string.escandallo_diag_extras), d.extrasSinConsumo);
        seccion(sb, getString(R.string.escandallo_diag_sin_precio), d.ingredientesSinPrecio);
        new AlertDialog.Builder(this)
                .setTitle(R.string.escandallo_avisos)
                .setMessage(sb.toString().trim())
                .setPositiveButton(R.string.aceptar, null)
                .show();
    }

    private static void seccion(StringBuilder sb, String titulo, List<String> lineas) {
        if (lineas.isEmpty()) return;
        sb.append(titulo).append('\n');
        for (String l : lineas) sb.append("  • ").append(l).append('\n');
        sb.append('\n');
    }

    private void elegirCsv() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.escandallo_importar_csv)), PEDIR_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PEDIR_CSV || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            InputStream entrada = getContentResolver().openInputStream(uri);
            if (entrada != null) viewModel.importar(entrada);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.no_se_pudo_abrir_el_archivo_seleccionado), Toast.LENGTH_LONG).show();
        }
    }
}
