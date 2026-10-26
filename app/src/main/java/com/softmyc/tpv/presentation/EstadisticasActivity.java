package com.softmyc.tpv.presentation;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.*;
import com.softmyc.tpv.data.*;
import com.softmyc.tpv.data.repo.VentaRepositoryRoom;
import com.softmyc.tpv.domain.repository.VentaResumen;
import com.softmyc.tpv.data.sync.SyncManager;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.softmyc.tpv.presentation.custom.SalesChartView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EstadisticasActivity extends BaseActivity {

    private ListView listaRanking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_estadisticas);

        setupBackButton();
        listaRanking = findViewById(R.id.listaRanking);

        // CALCULAR EL RANKING Y LA TENDENCIA
        calcularRanking();
    }

    private void calcularRanking() {
        // Filtramos desde el día 1 del mes actual a las 00:00:00
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long inicioMesMs = cal.getTimeInMillis();

        // Obtener el número de días en el mes actual
        Calendar calFinal = Calendar.getInstance();
        int maxDays = calFinal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Inicializar mapa de ventas ordenadas por día para todo el mes actual
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd MMM", new java.util.Locale("es", "ES"));
        Map<String, Double> ventasPorDia = new java.util.LinkedHashMap<>();
        for (int i = 1; i <= maxDays; i++) {
            Calendar loopCal = Calendar.getInstance();
            loopCal.set(Calendar.DAY_OF_MONTH, i);
            String diaKey = dayFormat.format(loopCal.getTime());
            ventasPorDia.put(diaKey, 0.0);
        }

        AppExecutors.disco().execute(() -> {
            // El filtro por mes lo hace SQLite con el índice de fecha, en lugar de cargar todo el histórico.
            List<VentaResumen> ventasRoom = new VentaRepositoryRoom(getApplicationContext()).getDesde(inicioMesMs);
            Map<String, Long> contadorProductos = new HashMap<>();

            for (VentaResumen venta : ventasRoom) {
                // 1. Total diario
                String diaKey = dayFormat.format(new Date(venta.fechaMs));
                if (ventasPorDia.containsKey(diaKey)) {
                    ventasPorDia.put(diaKey, ventasPorDia.get(diaKey) + venta.total);
                }

                // 2. Ranking productos
                List<PedidoItem> items;
                try {
                    items = SyncManager.deserializarItems(venta.itemsJson, null);
                } catch (DeserializacionVentaException e) {
                    // Sin detalle legible la venta no suma al ranking de productos; sus importes sí cuentan.
                    android.util.Log.w("EstadisticasActivity", "Venta " + venta.id + " sin detalle legible", e);
                    items = null;
                }
                if (items != null) {
                    for (PedidoItem item : items) {
                        String nombre = item.getNombre();
                        long cant = item.getCantidad();
                        contadorProductos.put(nombre, contadorProductos.getOrDefault(nombre, 0L) + cant);
                    }
                }
            }

            List<Map.Entry<String, Long>> listaOrdenada = new ArrayList<>(contadorProductos.entrySet());
            Collections.sort(listaOrdenada, (o1, o2) -> o2.getValue().compareTo(o1.getValue()));

            AppExecutors.enUiSiViva(this, () -> {
                RankingAdapter adapter = new RankingAdapter(this, listaOrdenada);
                listaRanking.setAdapter(adapter);

                List<Double> chartValores = new ArrayList<>();
                List<String> chartLabels = new ArrayList<>();
                for (Map.Entry<String, Double> entry : ventasPorDia.entrySet()) {
                    chartLabels.add(entry.getKey());
                    chartValores.add(entry.getValue());
                }

                SalesChartView salesChartView = findViewById(R.id.salesChartView);
                if (salesChartView != null) {
                    salesChartView.setData(chartValores, chartLabels);
                }
            });
        });
    }

    // --- ADAPTADOR PERSONALIZADO ---
    private class RankingAdapter extends android.widget.ArrayAdapter<Map.Entry<String, Long>> {
        
        public RankingAdapter(android.content.Context context, List<Map.Entry<String, Long>> list) {
            super(context, 0, list);
        }

        @Override
        public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
            if (convertView == null) {
                convertView = android.view.LayoutInflater.from(getContext()).inflate(R.layout.item_ranking, parent, false);
            }

            Map.Entry<String, Long> item = getItem(position);

            TextView txtPosicion = convertView.findViewById(R.id.txtPosicion);
            TextView txtNombre = convertView.findViewById(R.id.txtNombreProducto);
            TextView txtVentas = convertView.findViewById(R.id.txtVentas);

            // Posición + Medallas
            if (position == 0) txtPosicion.setText("🥇");
            else if (position == 1) txtPosicion.setText("🥈");
            else if (position == 2) txtPosicion.setText("🥉");
            else txtPosicion.setText(String.valueOf(position + 1));

            if (item != null) {
                txtNombre.setText(item.getKey());
                txtVentas.setText(getString(R.string.estadisticas_unidades_fmt, item.getValue()));
            }
            
            return convertView;
        }
    }
}