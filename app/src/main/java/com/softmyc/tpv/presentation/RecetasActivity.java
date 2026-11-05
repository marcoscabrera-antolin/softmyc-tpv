package com.softmyc.tpv.presentation;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.NombreNormalizado;
import com.softmyc.tpv.domain.escandallo.ConsumoExtra;
import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.LineaReceta;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;
import com.softmyc.tpv.domain.usecase.BuscadorProductos;
import com.softmyc.tpv.domain.usecase.CalculadoraRentabilidad;
import com.softmyc.tpv.domain.usecase.ConsumoIngredientes;
import com.softmyc.tpv.presentation.viewmodel.EscandalloViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Escandallo, parte 2: qué lleva cada producto (y qué gasta cada extra). Cada producto enseña su coste de
 * ingredientes, gastos añadidos, beneficio y margen a su precio de carta.
 */
public class RecetasActivity extends BaseActivity {

    private static final int COLOR_BIEN = 0xFF34D399;
    private static final int COLOR_JUSTO = 0xFFFBBF24;
    private static final int COLOR_MAL = 0xFFF87171;
    private static final int COLOR_NEUTRO = 0xFF94A3B8;
    /** Margen sobre coste que el negocio usa como objetivo para fijar precios. */
    private static final double MARGEN_OBJETIVO = 0.40;

    private EscandalloViewModel viewModel;
    private final FilaEscandalloAdapter adaptador = new FilaEscandalloAdapter();
    private EditText edtBuscar;
    private boolean modoExtras;
    private EscandalloViewModel.Estado ultimo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recetas);
        setupBackButton();

        RecyclerView lista = findViewById(R.id.recyclerRecetas);
        lista.setLayoutManager(new LinearLayoutManager(this));
        lista.setAdapter(adaptador);
        edtBuscar = findViewById(R.id.edtBuscarReceta);
        edtBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { pintar(); }
        });
        MaterialButtonToggleGroup grupo = findViewById(R.id.grupoModoRecetas);
        grupo.addOnButtonCheckedListener((g, id, marcado) -> {
            if (!marcado) return;
            modoExtras = id == R.id.btnModoExtras;
            pintar();
        });

        viewModel = new ViewModelProvider(this).get(EscandalloViewModel.class);
        viewModel.estado().observe(this, e -> { ultimo = e; pintar(); });
        viewModel.mensaje().observe(this, m -> {
            if (m == null) return;
            Toast.makeText(this, m, Toast.LENGTH_LONG).show();
            viewModel.mensajeLeido();
        });
        viewModel.cargar();
    }

    private void pintar() {
        if (ultimo == null) return;
        List<FilaEscandalloAdapter.Item> filas = new ArrayList<>();
        String texto = edtBuscar.getText().toString();
        if (modoExtras) {
            String filtro = NombreNormalizado.de(texto);
            for (String extra : ultimo.extras) {
                if (!filtro.isEmpty() && !NombreNormalizado.de(extra).contains(filtro)) continue;
                List<ConsumoExtra> consumo = ultimo.recetario.consumoExtra(NombreNormalizado.de(extra));
                filas.add(new FilaEscandalloAdapter.Item(extra, describirExtra(consumo),
                        consumo.isEmpty() ? getString(R.string.escandallo_sin_enlazar) : "",
                        COLOR_NEUTRO, () -> editarExtra(extra), null));
            }
        } else {
            for (ProductoCatalogo p : filtrar(ultimo.productos, texto)) {
                filas.add(filaProducto(p));
            }
        }
        adaptador.mostrar(filas);
    }

    /** Reutiliza el buscador de la pantalla de pedido (nombre, ingrediente de carta o código). */
    private static List<ProductoCatalogo> filtrar(List<ProductoCatalogo> productos, String texto) {
        List<com.softmyc.tpv.domain.Producto> comoProducto = new ArrayList<>();
        for (ProductoCatalogo p : productos) {
            comoProducto.add(new com.softmyc.tpv.domain.Producto(p.codigo, p.nombre, p.precio, p.categoria, p.ingredientes));
        }
        List<ProductoCatalogo> r = new ArrayList<>();
        for (com.softmyc.tpv.domain.Producto encontrado : BuscadorProductos.filtrar(comoProducto, texto)) {
            for (ProductoCatalogo p : productos) if (p.codigo == encontrado.getCodigo()) { r.add(p); break; }
        }
        r.sort((a, b) -> Integer.compare(a.codigo, b.codigo));
        return r;
    }

    private FilaEscandalloAdapter.Item filaProducto(ProductoCatalogo p) {
        Recetario rec = ultimo.recetario;
        String titulo = p.codigo + " · " + p.nombre;
        if (!rec.tieneReceta(p.codigo)) {
            return new FilaEscandalloAdapter.Item(titulo, getString(R.string.escandallo_sin_receta), "",
                    COLOR_NEUTRO, () -> editarProducto(p), null);
        }
        Double coste = ConsumoIngredientes.costeUnitario(p.codigo, rec);
        CalculadoraRentabilidad.Unitario u = CalculadoraRentabilidad.unitario(p.precio, coste, rec.gastosAnadidos(p.codigo));
        if (u == null) {
            return new FilaEscandalloAdapter.Item(titulo, getString(R.string.escandallo_coste_incompleto), "",
                    COLOR_NEUTRO, () -> editarProducto(p), null);
        }
        String detalle = getString(R.string.escandallo_detalle_producto_fmt, FormatoEscandallo.euros(coste),
                FormatoEscandallo.euros(u.costeTotal), FormatoEscandallo.euros(p.precio), FormatoEscandallo.euros(u.beneficio));
        String margen = u.margen != null ? FormatoEscandallo.porcentaje(u.margen) : "";
        int color = u.margen == null ? COLOR_NEUTRO : u.margen >= MARGEN_OBJETIVO ? COLOR_BIEN : u.margen >= 0 ? COLOR_JUSTO : COLOR_MAL;
        return new FilaEscandalloAdapter.Item(titulo, detalle, margen, color, () -> editarProducto(p), null);
    }

    private String describirExtra(List<ConsumoExtra> consumo) {
        StringBuilder sb = new StringBuilder();
        for (ConsumoExtra c : consumo) {
            Ingrediente i = ultimo.recetario.ingrediente(c.ingredienteId);
            if (i == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(i.nombre).append(' ').append(FormatoEscandallo.cantidad(c.cantidad, i.unidad));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ editor

    /** Una fila del editor: ingrediente, cantidad y texto de la carta al que corresponde (para los "sin …"). */
    private final class FilaEditor {
        final View vista;
        final Spinner ingrediente;
        final EditText cantidad;
        final TextView unidad;
        final String nombreEnCarta;

        FilaEditor(LinearLayout contenedor, List<Ingrediente> ingredientes, Long ingredienteId, Double valor,
                   String nombreEnCarta, Runnable alCambiar) {
            this.nombreEnCarta = nombreEnCarta;
            LinearLayout fila = new LinearLayout(RecetasActivity.this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setGravity(android.view.Gravity.CENTER_VERTICAL);
            ingrediente = new Spinner(RecetasActivity.this);
            List<String> nombres = new ArrayList<>();
            for (Ingrediente i : ingredientes) nombres.add(i.nombre);
            ArrayAdapter<String> a = new ArrayAdapter<>(RecetasActivity.this, android.R.layout.simple_spinner_item, nombres);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            ingrediente.setAdapter(a);
            for (int k = 0; ingredienteId != null && k < ingredientes.size(); k++) {
                if (ingredientes.get(k).id == ingredienteId) ingrediente.setSelection(k);
            }
            cantidad = new EditText(RecetasActivity.this);
            cantidad.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            cantidad.setHint(R.string.escandallo_cantidad);
            cantidad.setMinEms(4);
            if (valor != null) cantidad.setText(FormatoEscandallo.numero(valor));
            unidad = new TextView(RecetasActivity.this);
            ImageButton quitar = new ImageButton(RecetasActivity.this);
            quitar.setImageResource(android.R.drawable.ic_menu_delete);
            quitar.setBackgroundColor(0);
            quitar.setContentDescription(getString(R.string.eliminar));
            fila.addView(ingrediente, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            fila.addView(cantidad);
            fila.addView(unidad);
            fila.addView(quitar);
            if (nombreEnCarta != null) {
                LinearLayout bloque = new LinearLayout(RecetasActivity.this);
                bloque.setOrientation(LinearLayout.VERTICAL);
                TextView carta = new TextView(RecetasActivity.this);
                carta.setText(getString(R.string.escandallo_en_carta_fmt, nombreEnCarta));
                carta.setTextSize(12);
                bloque.addView(carta);
                bloque.addView(fila);
                vista = bloque;
            } else {
                vista = fila;
            }
            contenedor.addView(vista);
            quitar.setOnClickListener(v -> { contenedor.removeView(vista); filas.remove(this); alCambiar.run(); });
            ingrediente.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { alCambiar.run(); }
                @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
            });
            cantidad.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) { alCambiar.run(); }
            });
        }

        Ingrediente elegido(List<Ingrediente> ingredientes) {
            int pos = ingrediente.getSelectedItemPosition();
            return pos >= 0 && pos < ingredientes.size() ? ingredientes.get(pos) : null;
        }
    }

    private final List<FilaEditor> filas = new ArrayList<>();

    private void editarProducto(ProductoCatalogo p) {
        abrirEditor(p, null);
    }

    private void editarExtra(String extra) {
        abrirEditor(null, extra);
    }

    /** Editor común: receta de un producto (p) o consumo de un extra. */
    private void abrirEditor(ProductoCatalogo producto, String extra) {
        if (ultimo == null) return;
        List<Ingrediente> ingredientes = ultimo.ingredientes;
        if (ingredientes.isEmpty()) {
            Toast.makeText(this, R.string.escandallo_sin_ingredientes, Toast.LENGTH_LONG).show();
            return;
        }
        Recetario rec = ultimo.recetario;
        filas.clear();
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setPadding(pad, pad / 2, pad, 0);
        LinearLayout contenedor = new LinearLayout(this);
        contenedor.setOrientation(LinearLayout.VERTICAL);
        TextView resumen = new TextView(this);
        resumen.setPadding(0, pad / 2, 0, pad / 2);
        EditText gastos = new EditText(this);
        gastos.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        gastos.setHint(R.string.escandallo_gastos_anadidos);

        Runnable recalcular = () -> resumen.setText(resumenEditor(producto, ingredientes, gastos));

        if (producto != null) {
            List<LineaReceta> receta = rec.receta(producto.codigo);
            if (!receta.isEmpty()) {
                for (LineaReceta l : receta) {
                    filas.add(new FilaEditor(contenedor, ingredientes, l.ingredienteId, l.cantidad, l.nombreEnCarta, recalcular));
                }
            } else if (producto.ingredientes != null) {
                // Sin receta: una fila por ingrediente de la carta, con el ingrediente que case por nombre.
                for (String enCarta : producto.ingredientes) {
                    if (enCarta == null || enCarta.trim().isEmpty()) continue;
                    Long id = null;
                    for (Ingrediente i : ingredientes) if (i.nombreNormalizado().equals(NombreNormalizado.de(enCarta))) id = i.id;
                    filas.add(new FilaEditor(contenedor, ingredientes, id, null, enCarta.trim(), recalcular));
                }
            }
            Double g = rec.gastosAnadidos(producto.codigo);
            if (g != null) gastos.setText(FormatoEscandallo.numero(g));
        } else {
            for (ConsumoExtra c : rec.consumoExtra(NombreNormalizado.de(extra))) {
                filas.add(new FilaEditor(contenedor, ingredientes, c.ingredienteId, c.cantidad, null, recalcular));
            }
        }

        Button anadir = new Button(this);
        anadir.setText(R.string.escandallo_anadir_ingrediente);
        anadir.setOnClickListener(v -> filas.add(new FilaEditor(contenedor, ingredientes, null, null, null, recalcular)));
        gastos.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { recalcular.run(); }
        });

        raiz.addView(contenedor);
        raiz.addView(anadir);
        if (producto != null) {
            raiz.addView(gastos);
            raiz.addView(resumen);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(raiz);
        recalcular.run();

        String titulo = producto != null ? producto.codigo + " · " + producto.nombre : getString(R.string.escandallo_extra_fmt, extra);
        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setView(scroll)
                .setPositiveButton(R.string.guardar, null)
                .setNegativeButton(R.string.cancelar, null)
                .create();
        dialogo.setOnShowListener(d -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<LineaReceta> lineas = new ArrayList<>();
            for (FilaEditor f : filas) {
                Double q = FormatoEscandallo.leer(f.cantidad.getText().toString());
                Ingrediente i = f.elegido(ingredientes);
                if (q == null || q <= 0 || i == null) {
                    f.cantidad.setError(getString(R.string.escandallo_valor_no_valido));
                    return;
                }
                lineas.add(new LineaReceta(producto != null ? producto.codigo : 0, i.id, q, f.nombreEnCarta));
            }
            if (producto != null) {
                Double g = FormatoEscandallo.leer(gastos.getText().toString());
                if (g != null && g < 0) { gastos.setError(getString(R.string.escandallo_valor_no_valido)); return; }
                viewModel.guardarReceta(producto.codigo, lineas, g);
            } else {
                List<ConsumoExtra> consumos = new ArrayList<>();
                for (LineaReceta l : lineas) consumos.add(new ConsumoExtra(extra, l.ingredienteId, l.cantidad));
                viewModel.guardarConsumoExtra(extra, consumos);
            }
            dialogo.dismiss();
        }));
        dialogo.show();
    }

    /** Coste y margen de lo que hay escrito en el editor, recalculado con cada cambio. */
    private String resumenEditor(ProductoCatalogo producto, List<Ingrediente> ingredientes, EditText gastos) {
        if (producto == null) return "";
        double coste = 0;
        for (FilaEditor f : filas) {
            Ingrediente i = f.elegido(ingredientes);
            if (i != null) f.unidad.setText(i.unidad.simbolo());
            Double q = FormatoEscandallo.leer(f.cantidad.getText().toString());
            if (i == null || q == null) continue;
            Double c = i.coste(q);
            if (c == null) return getString(R.string.escandallo_coste_incompleto);
            coste += c;
        }
        CalculadoraRentabilidad.Unitario u = CalculadoraRentabilidad.unitario(producto.precio, coste,
                FormatoEscandallo.leer(gastos.getText().toString()));
        String margen = u.margen != null ? FormatoEscandallo.porcentaje(u.margen) : "—";
        return getString(R.string.escandallo_resumen_editor_fmt, FormatoEscandallo.euros(coste),
                FormatoEscandallo.euros(u.costeTotal), FormatoEscandallo.euros(producto.precio),
                FormatoEscandallo.euros(u.beneficio), margen);
    }
}
