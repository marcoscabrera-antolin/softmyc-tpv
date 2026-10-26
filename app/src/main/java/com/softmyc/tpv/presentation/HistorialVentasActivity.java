package com.softmyc.tpv.presentation;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.*;
import com.softmyc.tpv.data.*;
import com.softmyc.tpv.data.repo.VentaRepositoryRoom;
import com.softmyc.tpv.domain.DeserializacionVentaException;
import com.softmyc.tpv.domain.port.AjustesImpresora;
import com.softmyc.tpv.domain.repository.VentaResumen;
import com.softmyc.tpv.data.printer.PrinterHelper;
import com.softmyc.tpv.data.printer.TicketGenerator;
import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.presentation.util.VentasNavegacionHelper;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistorialVentasActivity extends BaseActivity {

    private ListView listaVentas;
    private TextView txtTotalCaja, txtTituloFiltro;
    private Button btnDia, btnMes, btnAnio;
    private EditText etBuscarVentas;
    private Button btnImprimirCierre;

    private List<VentaResumen> todasVentas = new ArrayList<>(); 
    private List<VentaResumen> listaVentasFiltradas = new ArrayList<>(); 
    private VentaAdapter adapter;

    private String queryBusqueda = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historial_ventas);

        setupBackButton();

        // Inicializar Vistas
        txtTituloFiltro = findViewById(R.id.txtTituloFiltro);
        txtTotalCaja = findViewById(R.id.txtTotalCaja);
        listaVentas = findViewById(R.id.listaVentas);
        
        btnDia = findViewById(R.id.btnDia);
        btnMes = findViewById(R.id.btnMes);
        btnAnio = findViewById(R.id.btnAnio);

        etBuscarVentas = findViewById(R.id.etBuscarVentas);
        btnImprimirCierre = findViewById(R.id.btnImprimirCierre);

        // Listeners Filtros Fecha
        btnDia.setOnClickListener(v -> {
            cargarVentas(getInicioDia(), "VENTAS DE HOY");
            actualizarBotones(btnDia);
        });
        
        btnMes.setOnClickListener(v -> {
            cargarVentas(getInicioMes(), "VENTAS DEL MES");
            actualizarBotones(btnMes);
        });
        
        btnAnio.setOnClickListener(v -> {
            cargarVentas(getInicioAnio(), "VENTAS DEL AÑO");
            actualizarBotones(btnAnio);
        });

        // Listener Buscador
        etBuscarVentas.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                queryBusqueda = s.toString().trim();
                aplicarFiltros();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Impresión de Cierre
        btnImprimirCierre.setOnClickListener(v -> imprimirCierreDeCaja());

        // Click en lista
        listaVentas.setOnItemClickListener((parent, view, position, id) -> {
            if (position < listaVentasFiltradas.size()) {
                VentaResumen venta = listaVentasFiltradas.get(position);
                mostrarDetalleVenta(venta);
            }
        });

        // Cargar hoy por defecto
        cargarVentas(getInicioDia(), "VENTAS DE HOY");
        actualizarBotones(btnDia);
    }
    
    private void actualizarBotones(Button activo) {
        int colorInactive = 0xFF334155; 
        int colorActive = 0xFF3B82F6; // Blue 500
        
        btnDia.setBackgroundColor(colorInactive);
        btnMes.setBackgroundColor(colorInactive);
        btnAnio.setBackgroundColor(colorInactive);
        
        activo.setBackgroundColor(colorActive);
    }

    private void cargarVentas(Date fechaInicio, String titulo) {
        txtTituloFiltro.setText(titulo);
        txtTotalCaja.setText("...");
        
        long inicioMs = fechaInicio.getTime();

        AppExecutors.disco().execute(() -> {
            // El filtro por periodo lo hace SQLite con el índice de fecha, en lugar de cargar todo el histórico.
            List<VentaResumen> resultado = new VentaRepositoryRoom(getApplicationContext()).getDesde(inicioMs);

            AppExecutors.enUiSiViva(this, () -> {
                todasVentas = resultado;
                aplicarFiltros();
            });
        });
    }

    private void aplicarFiltros() {
        List<VentaResumen> filtrados = new ArrayList<>();
        double sumaTotal = 0;

        for (VentaResumen venta : todasVentas) {
            String totalStr = String.format(Locale.getDefault(), "%.2f", venta.total);
            boolean coincide = queryBusqueda.isEmpty();

            if (venta.itemsJson != null && venta.itemsJson.toLowerCase().contains(queryBusqueda.toLowerCase())) {
                coincide = true;
            }

            if (totalStr.contains(queryBusqueda)) {
                coincide = true;
            }

            if (coincide) {
                filtrados.add(venta);
                sumaTotal += venta.total;
            }
        }

        txtTotalCaja.setText(String.format(Locale.getDefault(), "%.2f€", sumaTotal));
        listaVentasFiltradas = filtrados;

        if (adapter == null) {
            adapter = new VentaAdapter(listaVentasFiltradas);
            listaVentas.setAdapter(adapter);
        } else {
            adapter.clear();
            adapter.addAll(listaVentasFiltradas);
            adapter.notifyDataSetChanged();
        }
    }

    private void imprimirCierreDeCaja() {
        if (listaVentasFiltradas == null || listaVentasFiltradas.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_hay_ventas_en_este_periodo_para_el_ci), Toast.LENGTH_SHORT).show();
            return;
        }

        String ip = "192.168.0.20";
        int fontStyle = 0;
        int port = AjustesImpresora.PUERTO_POR_DEFECTO;
        try {
            SharedPreferences sp = com.softmyc.tpv.data.SecurePreferencesHelper.getPrinterPrefs(this);
            ip = sp.getString("impresora_ip", "192.168.0.20");
            port = sp.getInt("impresora_puerto", AjustesImpresora.PUERTO_POR_DEFECTO);
            fontStyle = sp.getInt("tamanio_letra", 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        int cols = (fontStyle == 2) ? 24 : 48;
        String divider = repeat("-", cols);

        double totalPeriodo = 0;
        int totalTickets = listaVentasFiltradas.size();
        double totalEfectivo = 0;
        double totalTarjeta = 0;

        for (VentaResumen venta : listaVentasFiltradas) {
            totalPeriodo += venta.total;
            if ("Tarjeta".equalsIgnoreCase(venta.metodoPago)) {
                totalTarjeta += venta.total;
            } else {
                totalEfectivo += venta.total;
            }
        }

        String nombreEstablecimiento = "SoftMYC";
        try {
            SharedPreferences settingsPrefs = com.softmyc.tpv.data.SecurePreferencesHelper.getPrinterPrefs(this);
            nombreEstablecimiento = settingsPrefs.getString("nombre_restaurante", "SoftMYC");
        } catch (Exception e) {
            e.printStackTrace();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(divider).append("\n");
        sb.append(center("CIERRE DE CAJA DIARIO", cols)).append("\n");
        sb.append(divider).append("\n");

        String fechaActual = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        sb.append("FECHA EMISION: ").append(fechaActual).append("\n");
        sb.append("ESTABLECIMIENTO: ").append(nombreEstablecimiento.toUpperCase()).append("\n");
        sb.append("PERIODO TIPO: ").append(txtTituloFiltro.getText().toString()).append("\n");
        sb.append(divider).append("\n\n");

        sb.append("TICKETS TRAMITADOS: ").append(totalTickets).append("\n");
        sb.append(rightAlign("TOTAL CAJA: " + String.format(Locale.getDefault(), "%.2f EUR", totalPeriodo), cols)).append("\n\n");

        sb.append("DESGLOSE DE PAGO:\n");
        sb.append("- EFECTIVO: ").append(String.format(Locale.getDefault(), "%.2f EUR", totalEfectivo)).append("\n");
        sb.append("- TARJETA:  ").append(String.format(Locale.getDefault(), "%.2f EUR", totalTarjeta)).append("\n");
        sb.append(divider).append("\n\n");
        
        sb.append(center("CIERRE DE CAJA CUADRADO", cols)).append("\n");
        sb.append(divider).append("\n\n");

        PrinterHelper printer = new PrinterHelper(ip, port, fontStyle);
        printer.print(this, sb.toString());
        Toast.makeText(this, getString(R.string.historial_imprimiendo_cierre_fmt, ip), Toast.LENGTH_SHORT).show();
    }

    private String center(String text, int width) {
        int pad = (width - text.length()) / 2;
        if (pad <= 0) return text;
        return repeat(" ", pad) + text;
    }

    private String rightAlign(String text, int width) {
        int pad = width - text.length();
        if (pad <= 0) return text;
        return repeat(" ", pad) + text;
    }

    private String repeat(String s, int times) {
        if (times <= 0) return "";
        return new String(new char[times]).replace("\0", s);
    }

    private void mostrarDetalleVenta(VentaResumen venta) {
        StringBuilder detalle = new StringBuilder();
        List<PedidoItem> items;
        try {
            items = SyncManager.deserializarItems(venta.itemsJson, null);
        } catch (DeserializacionVentaException e) {
            items = null;
        }

        if (items != null && !items.isEmpty()) {
            for (PedidoItem item : items) {
                detalle.append(item.getCantidad()).append(" x ").append(item.getNombre()).append("\n");
                if (item.getIngredientesExtra() != null && !item.getIngredientesExtra().isEmpty()) {
                    detalle.append("   + ").append(item.getIngredientesExtra()).append("\n");
                }
            }
        } else {
            detalle.append(items == null ? "El detalle de esta venta no se puede leer." : "No hay detalle de la venta.");
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.historial_detalle_ticket_fmt, venta.id))
                .setMessage(detalle.toString())
                .setPositiveButton(getString(R.string.cerrar), null)
                .show();
    }

    // --- ADAPTADOR PERSONALIZADO ---
    class VentaAdapter extends ArrayAdapter<VentaResumen> {
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

        public VentaAdapter(List<VentaResumen> docs) {
            super(HistorialVentasActivity.this, 0, new ArrayList<>(docs));
        }

        class ViewHolder {
            TextView txtFecha;
            TextView txtMonto;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_venta, parent, false);
                holder = new ViewHolder();
                holder.txtFecha = convertView.findViewById(R.id.txtFechaHora);
                holder.txtMonto = convertView.findViewById(R.id.txtMonto);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            VentaResumen venta = getItem(position);
            if (venta != null) {
                holder.txtFecha.setText(sdf.format(new Date(venta.fechaMs)));
                holder.txtMonto.setText(String.format(Locale.getDefault(), "%.2f€", venta.total));
            }
            return convertView;
        }
    }

    // --- CÁLCULO DE FECHAS ---
    private Date getInicioDia() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0);
        return cal.getTime();
    }
    private Date getInicioMes() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0);
        return cal.getTime();
    }
    private Date getInicioAnio() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_YEAR, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0);
        return cal.getTime();
    }
}