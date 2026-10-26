package com.softmyc.tpv.presentation;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.softmyc.tpv.domain.port.AjustesImpresora;
import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.printer.PrinterHelper;
import com.softmyc.tpv.data.repo.ProductoRepositoryRoom;
import com.softmyc.tpv.data.repo.VentaRepositoryRoom;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;
import com.softmyc.tpv.domain.repository.VentaResumen;
import com.softmyc.tpv.domain.usecase.ArqueoCocinaCalculator;
import com.softmyc.tpv.domain.usecase.ArqueoCocinaCalculator.Resultado;
import com.softmyc.tpv.util.CategoryUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Arqueo de cocina: ingresos de las categorías marcadas como cocina, por periodo, con ticket impreso. */
public class ArqueoCocinaActivity extends BaseActivity {

    private static final String PREF_CATEGORIAS_COCINA = "categorias_cocina";

    private enum Periodo { HOY, MES, ANIO }

    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();

    private TextView txtTotal;
    private TextView txtDesglose;
    private TextView txtCategorias;
    private TextView txtVacio;
    private ListView listaProductos;

    private Periodo periodo = Periodo.HOY;
    private Resultado ultimoResultado;
    private List<String> categoriasDisponibles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arqueo_cocina);
        setupBackButton();

        txtTotal = findViewById(R.id.txtTotalCocina);
        txtDesglose = findViewById(R.id.txtDesglose);
        txtCategorias = findViewById(R.id.txtCategorias);
        txtVacio = findViewById(R.id.txtArqueoVacio);
        listaProductos = findViewById(R.id.listaProductosCocina);

        findViewById(R.id.btnPeriodoHoy).setOnClickListener(v -> cambiarPeriodo(Periodo.HOY));
        findViewById(R.id.btnPeriodoMes).setOnClickListener(v -> cambiarPeriodo(Periodo.MES));
        findViewById(R.id.btnPeriodoAnio).setOnClickListener(v -> cambiarPeriodo(Periodo.ANIO));
        findViewById(R.id.btnCategoriasCocina).setOnClickListener(v -> elegirCategorias());
        findViewById(R.id.btnImprimirArqueo).setOnClickListener(v -> imprimirArqueo());

        recalcular();
    }

    @Override
    protected void onDestroy() {
        diskExecutor.shutdown();
        super.onDestroy();
    }

    private void cambiarPeriodo(Periodo nuevo) {
        periodo = nuevo;
        recalcular();
    }

    private Set<String> categoriasCocina() {
        SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(this);
        Set<String> guardadas = prefs.getStringSet(PREF_CATEGORIAS_COCINA, null);
        return guardadas != null ? new HashSet<>(guardadas) : new HashSet<>(ArqueoCocinaCalculator.CATEGORIAS_POR_DEFECTO);
    }

    private long inicioPeriodo() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (periodo == Periodo.MES) cal.set(Calendar.DAY_OF_MONTH, 1);
        if (periodo == Periodo.ANIO) cal.set(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }

    private String nombrePeriodo() {
        switch (periodo) {
            case MES: return "ESTE MES";
            case ANIO: return "ESTE ANIO";
            default: return "HOY";
        }
    }

    private void recalcular() {
        final android.content.Context appContext = getApplicationContext();
        final long desde = inicioPeriodo();
        final Set<String> categorias = categoriasCocina();
        diskExecutor.execute(() -> {
            try {
                List<ArqueoCocinaCalculator.Venta> ventas = new ArrayList<>();
                for (VentaResumen v : new VentaRepositoryRoom(appContext).getDesde(desde)) {
                    ventas.add(new ArqueoCocinaCalculator.Venta(v.metodoPago, v.itemsJson));
                }
                Map<String, String> categoriaPorNombre = new HashMap<>();
                TreeSet<String> disponibles = new TreeSet<>();
                for (ProductoCatalogo p : new ProductoRepositoryRoom(appContext).listarTodos()) {
                    String cat = CategoryUtils.capitalizeCategory(p.categoria);
                    if (p.nombre != null) categoriaPorNombre.put(p.nombre.toLowerCase(Locale.ROOT), cat);
                    disponibles.add(cat);
                }
                Resultado resultado = ArqueoCocinaCalculator.calcular(ventas, categorias, categoriaPorNombre);
                runOnUiThread(() -> mostrar(resultado, categorias, new ArrayList<>(disponibles)));
            } catch (Exception e) {
                android.util.Log.e("ArqueoCocina", "No se pudo calcular el arqueo", e);
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.no_se_pudo_calcular_el_arqueo), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void mostrar(Resultado r, Set<String> categorias, List<String> disponibles) {
        ultimoResultado = r;
        categoriasDisponibles = disponibles;
        txtTotal.setText(getString(R.string.arqueo_cocina_total_fmt, nombrePeriodo().toLowerCase(Locale.ROOT), r.totalCocina));
        txtDesglose.setText(getString(R.string.arqueo_cocina_desglose_fmt, r.efectivo, r.tarjeta, r.tickets));
        txtCategorias.setText(getString(R.string.arqueo_categorias_fmt,
                categorias.isEmpty() ? getString(R.string.arqueo_ninguna_categoria) : String.join(", ", new TreeSet<>(categorias))));

        List<String> filas = new ArrayList<>();
        for (ArqueoCocinaCalculator.LineaProducto p : r.productos) {
            filas.add(String.format(Locale.getDefault(), "%dx  %s   —   %.2f €", p.unidades, p.nombre, p.importe));
        }
        listaProductos.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, filas));
        txtVacio.setVisibility(filas.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void elegirCategorias() {
        if (categoriasDisponibles.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_hay_categorias_en_el_catalogo), Toast.LENGTH_SHORT).show();
            return;
        }
        final Set<String> actuales = categoriasCocina();
        final String[] nombres = categoriasDisponibles.toArray(new String[0]);
        final boolean[] marcadas = new boolean[nombres.length];
        for (int i = 0; i < nombres.length; i++) marcadas[i] = actuales.contains(nombres[i]);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.categorias_de_cocina))
                .setMultiChoiceItems(nombres, marcadas, (dialog, which, checked) -> marcadas[which] = checked)
                .setPositiveButton(getString(R.string.guardar), (dialog, which) -> {
                    Set<String> nuevas = new HashSet<>();
                    for (int i = 0; i < nombres.length; i++) if (marcadas[i]) nuevas.add(nombres[i]);
                    SecurePreferencesHelper.getPrinterPrefs(this).edit().putStringSet(PREF_CATEGORIAS_COCINA, nuevas).apply();
                    recalcular();
                })
                .setNegativeButton(getString(R.string.cancelar), null)
                .show();
    }

    private void imprimirArqueo() {
        if (ultimoResultado == null || ultimoResultado.productos.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_hay_ventas_de_cocina_que_imprimir), Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(this);
        String ip = prefs.getString("impresora_ip", "192.168.0.20");
        int puerto = prefs.getInt("impresora_puerto", AjustesImpresora.PUERTO_POR_DEFECTO);
        int fontStyle = prefs.getInt("tamanio_letra", 0);
        String establecimiento = prefs.getString("nombre_restaurante", "SoftMYC");
        int cols = (fontStyle == 2) ? 24 : 48;

        String fecha = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        String ticket = ArqueoCocinaCalculator.formatearTicket(ultimoResultado, establecimiento, nombrePeriodo(), fecha, cols);

        new PrinterHelper(ip, puerto, fontStyle).print(this, ticket, (ok, error) -> runOnUiThread(() ->
                Toast.makeText(this, ok ? getString(R.string.arqueo_enviado_a_la_impresora)
                        : error != null ? getString(R.string.arqueo_no_impreso_error_fmt, error) : getString(R.string.arqueo_no_impreso),
                        Toast.LENGTH_LONG).show()));
        Toast.makeText(this, getString(R.string.arqueo_imprimiendo_fmt, ip), Toast.LENGTH_SHORT).show();
    }
}
