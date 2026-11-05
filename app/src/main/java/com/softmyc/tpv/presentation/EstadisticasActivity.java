package com.softmyc.tpv.presentation;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.CostesVenta;
import com.softmyc.tpv.data.local.RecetarioRoom;
import com.softmyc.tpv.data.repo.VentaRepositoryRoom;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.repository.VentaResumen;
import com.softmyc.tpv.domain.usecase.CalculadoraRentabilidad;

import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.softmyc.tpv.presentation.custom.SalesChartView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Estadísticas: tendencia de ventas del mes, ventas y beneficio (bruto y neto) del periodo elegido y ranking
 * de productos. El beneficio y el margen solo los ven administrador y encargado.
 */
public class EstadisticasActivity extends BaseActivity {

    enum Periodo { HOY, SEMANA, MES }

    private ListView listaRanking;
    private Periodo periodo = Periodo.HOY;
    private boolean verBeneficio;

    /** Ventas desde el inicio del periodo más largo, cargadas una vez; cambiar de periodo solo vuelve a calcular. */
    private List<VentaResumen> ventasCargadas;
    private Recetario recetario;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_estadisticas);

        setupBackButton();
        listaRanking = findViewById(R.id.listaRanking);

        String rol = SecurePreferencesHelper.getPrinterPrefs(this).getString("user_role", "tpv_operator");
        verBeneficio = "admin".equalsIgnoreCase(rol) || "encargado".equalsIgnoreCase(rol);
        int visibilidad = verBeneficio ? View.VISIBLE : View.GONE;
        findViewById(R.id.cajaBruto).setVisibility(visibilidad);
        findViewById(R.id.cajaNeto).setVisibility(visibilidad);
        findViewById(R.id.cajaMargen).setVisibility(visibilidad);

        MaterialButtonToggleGroup grupo = findViewById(R.id.grupoPeriodo);
        grupo.addOnButtonCheckedListener((g, id, marcado) -> {
            if (!marcado) return;
            periodo = id == R.id.btnPeriodoSemana ? Periodo.SEMANA : id == R.id.btnPeriodoMes ? Periodo.MES : Periodo.HOY;
            mostrarPeriodo();
        });

        cargar();
    }

    /** Inicio (00:00) del periodo que contiene {@code ahora}. La semana empieza en lunes. */
    static long inicioDe(Periodo p, Calendar ahora) {
        Calendar c = (Calendar) ahora.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (p == Periodo.MES) {
            c.set(Calendar.DAY_OF_MONTH, 1);
        } else if (p == Periodo.SEMANA) {
            int desdeLunes = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            c.add(Calendar.DAY_OF_MONTH, -desdeLunes);
        }
        return c.getTimeInMillis();
    }

    private void cargar() {
        Calendar ahora = Calendar.getInstance();
        long inicioMes = inicioDe(Periodo.MES, ahora);
        long desde = Math.min(inicioMes, inicioDe(Periodo.SEMANA, ahora));

        // Gráfica: un punto por día del mes en curso.
        SimpleDateFormat formatoDia = new SimpleDateFormat("dd MMM", new Locale("es", "ES"));
        Map<String, Double> ventasPorDia = new LinkedHashMap<>();
        int diasMes = ahora.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = 1; i <= diasMes; i++) {
            Calendar dia = (Calendar) ahora.clone();
            dia.set(Calendar.DAY_OF_MONTH, i);
            ventasPorDia.put(formatoDia.format(dia.getTime()), 0.0);
        }

        AppExecutors.disco().execute(() -> {
            // El filtro por fecha lo hace SQLite con el índice, en lugar de cargar todo el histórico.
            List<VentaResumen> ventas = new VentaRepositoryRoom(getApplicationContext()).getDesde(desde);
            Recetario rec = RecetarioRoom.cargar(AppDatabase.getDatabase(getApplicationContext()));
            for (VentaResumen v : ventas) {
                if (v.fechaMs < inicioMes) continue;
                String clave = formatoDia.format(new Date(v.fechaMs));
                if (ventasPorDia.containsKey(clave)) ventasPorDia.put(clave, ventasPorDia.get(clave) + v.total);
            }
            AppExecutors.enUiSiViva(this, () -> {
                ventasCargadas = ventas;
                recetario = rec;
                List<Double> valores = new ArrayList<>(ventasPorDia.values());
                List<String> etiquetas = new ArrayList<>(ventasPorDia.keySet());
                SalesChartView grafica = findViewById(R.id.salesChartView);
                if (grafica != null) grafica.setData(valores, etiquetas);
                mostrarPeriodo();
            });
        });
    }

    private void mostrarPeriodo() {
        if (ventasCargadas == null) return;
        CalculadoraRentabilidad.Resumen r = resumenDe(ventasCargadas, recetario, inicioDe(periodo, Calendar.getInstance()));

        ((TextView) findViewById(R.id.txtVentasPeriodo)).setText(FormatoEscandallo.euros(r.ventas));
        ((TextView) findViewById(R.id.txtBeneficioBruto)).setText(FormatoEscandallo.euros(r.beneficioBruto));
        ((TextView) findViewById(R.id.txtBeneficioNeto)).setText(FormatoEscandallo.euros(r.beneficioNeto));
        ((TextView) findViewById(R.id.txtMargen)).setText(r.margen != null ? FormatoEscandallo.porcentaje(r.margen) : "—");

        TextView aviso = findViewById(R.id.txtAvisoSinCoste);
        boolean hayHueco = verBeneficio && r.unidadesSinCoste > 0;
        aviso.setVisibility(hayHueco ? View.VISIBLE : View.GONE);
        if (hayHueco) {
            aviso.setText(getString(R.string.rentabilidad_sin_coste_fmt, FormatoEscandallo.numero(r.unidadesSinCoste),
                    FormatoEscandallo.euros(r.ventas - r.ventasConCoste)));
        }
        List<CalculadoraRentabilidad.PorProducto> ranking = new ArrayList<>(r.ranking);
        // Sin permiso para ver el beneficio, el ranking es por unidades (como antes): su orden no lo delata.
        if (!verBeneficio) ranking.sort((x, y) -> Double.compare(y.unidades, x.unidades));
        listaRanking.setAdapter(new RankingAdapter(this, ranking));
    }

    /** Resumen de las ventas desde {@code desdeMs}. Las ventas con detalle ilegible suman a ventas pero no al beneficio. */
    static CalculadoraRentabilidad.Resumen resumenDe(List<VentaResumen> ventas, Recetario recetario, long desdeMs) {
        double total = 0;
        List<CalculadoraRentabilidad.Linea> lineas = new ArrayList<>();
        for (VentaResumen v : ventas) {
            if (v.fechaMs < desdeMs) continue;
            total += v.total;
            List<CalculadoraRentabilidad.Linea> deVenta = CostesVenta.lineasRentabilidad(v.itemsJson, recetario);
            if (deVenta != null) lineas.addAll(deVenta);
        }
        return CalculadoraRentabilidad.calcular(total, lineas);
    }

    // --- ADAPTADOR DEL RANKING ---
    private class RankingAdapter extends android.widget.ArrayAdapter<CalculadoraRentabilidad.PorProducto> {

        RankingAdapter(android.content.Context context, List<CalculadoraRentabilidad.PorProducto> list) {
            super(context, 0, list);
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            if (convertView == null) {
                convertView = android.view.LayoutInflater.from(getContext()).inflate(R.layout.item_ranking, parent, false);
            }
            CalculadoraRentabilidad.PorProducto item = getItem(position);

            TextView txtPosicion = convertView.findViewById(R.id.txtPosicion);
            TextView txtNombre = convertView.findViewById(R.id.txtNombreProducto);
            TextView txtVentas = convertView.findViewById(R.id.txtVentas);

            if (position == 0) txtPosicion.setText("🥇");
            else if (position == 1) txtPosicion.setText("🥈");
            else if (position == 2) txtPosicion.setText("🥉");
            else txtPosicion.setText(String.valueOf(position + 1));

            if (item != null) {
                txtNombre.setText(item.producto);
                String unidades = getString(R.string.estadisticas_unidades_fmt, FormatoEscandallo.numero(item.unidades));
                txtVentas.setText(verBeneficio && item.beneficioNeto != null
                        ? getString(R.string.rentabilidad_ranking_fmt, unidades, FormatoEscandallo.euros(item.beneficioNeto))
                        : unidades);
            }
            return convertView;
        }
    }
}
