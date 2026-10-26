package com.softmyc.tpv.util;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.VentaLocal;
import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.presentation.AppExecutors;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ResumenSemanalCorreo {

    private static final int DIAS_RESUMEN = 7;

    /**
     * Muestra el resumen de los últimos 7 días. La consulta corre en segundo plano y solo referencia
     * la Activity de forma débil; el diálogo se cierra si la Activity se destruye.
     */
    public static void enviarResumen(Activity activity) {
        if (!AppExecutors.puedeActualizarUi(activity.isFinishing(), activity.isDestroyed())) return;

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_resumen_semanal, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        cerrarDialogoAlDestruir(activity, dialog);

        View btnCerrar = dialogView.findViewById(R.id.btnCerrarResumen);
        btnCerrar.setOnClickListener(v -> dialog.dismiss());

        Calendar inicio = inicioVentanaResumen();
        long fechaLimiteMs = inicio.getTimeInMillis();
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, dd MMM", new Locale("es", "ES"));
        List<String> listaDiasOrdenados = diasDeLaVentana(dayFormat);

        Context appContext = activity.getApplicationContext();
        WeakReference<Activity> actividadDebil = new WeakReference<>(activity);
        AppExecutors.disco().execute(() -> {
            ResumenDatos datos = calcularResumen(appContext, fechaLimiteMs, dayFormat, listaDiasOrdenados);
            Activity viva = actividadDebil.get();
            AppExecutors.enUiSiViva(viva, () ->
                    pintarResumen(dialogView, viva, datos, listaDiasOrdenados, fechaLimiteMs));
        });
    }

    private static Calendar inicioVentanaResumen() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -DIAS_RESUMEN);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private static List<String> diasDeLaVentana(SimpleDateFormat dayFormat) {
        List<String> dias = new ArrayList<>();
        for (int i = DIAS_RESUMEN - 1; i >= 0; i--) {
            Calendar loopCal = Calendar.getInstance();
            loopCal.add(Calendar.DAY_OF_YEAR, -i);
            dias.add(claveDia(dayFormat, loopCal.getTime()));
        }
        return dias;
    }

    private static String claveDia(SimpleDateFormat dayFormat, Date fecha) {
        String dia = dayFormat.format(fecha);
        return dia.isEmpty() ? dia : dia.substring(0, 1).toUpperCase() + dia.substring(1);
    }

    /** Agregados del periodo, calculados fuera del hilo principal. */
    private static final class ResumenDatos {
        final double total;
        final int pedidos;
        final String productoEstrella;
        final Map<String, Double> ventasPorDia;

        ResumenDatos(double total, int pedidos, String productoEstrella, Map<String, Double> ventasPorDia) {
            this.total = total;
            this.pedidos = pedidos;
            this.productoEstrella = productoEstrella;
            this.ventasPorDia = ventasPorDia;
        }
    }

    private static ResumenDatos calcularResumen(Context appContext, long fechaLimiteMs,
                                                SimpleDateFormat dayFormat, List<String> dias) {
        Map<String, Double> ventasPorDia = new LinkedHashMap<>();
        for (String dia : dias) {
            ventasPorDia.put(dia, 0.0);
        }
        List<VentaLocal> ventas = AppDatabase.getDatabase(appContext).ventaDao().getDesde(fechaLimiteMs);
        double totalFacturado = 0.0;
        Map<String, Integer> conteoProductos = new HashMap<>();
        for (VentaLocal v : ventas) {
            totalFacturado += v.total;
            String dia = claveDia(dayFormat, new Date(v.fecha));
            if (ventasPorDia.containsKey(dia)) {
                ventasPorDia.put(dia, ventasPorDia.get(dia) + v.total);
            }
            acumularProductos(v.itemsJson, conteoProductos);
        }
        return new ResumenDatos(totalFacturado, ventas.size(), productoEstrella(conteoProductos), ventasPorDia);
    }

    private static void acumularProductos(String itemsJson, Map<String, Integer> conteo) {
        List<PedidoItem> items;
        try {
            items = SyncManager.deserializarItems(itemsJson, null);
        } catch (com.softmyc.tpv.domain.DeserializacionVentaException e) {
            // Una venta sin detalle legible no aporta productos al resumen; el importe ya se contó.
            android.util.Log.w("ResumenSemanalCorreo", "Venta sin detalle legible omitida del ranking", e);
            return;
        }
        if (items == null) return;
        for (PedidoItem item : items) {
            conteo.merge(item.getNombre(), item.getCantidad(), Integer::sum);
        }
    }

    private static String productoEstrella(Map<String, Integer> conteo) {
        String estrella = null;
        int maxCantidad = 0;
        for (Map.Entry<String, Integer> entry : conteo.entrySet()) {
            if (entry.getValue() > maxCantidad) {
                maxCantidad = entry.getValue();
                estrella = entry.getKey();
            }
        }
        return maxCantidad > 0 ? estrella + " (" + maxCantidad + " uds)" : "Ninguno";
    }

    private static void pintarResumen(View dialogView, Activity activity,
                                      ResumenDatos datos, List<String> dias, long fechaLimiteMs) {
        TextView txtTotal = dialogView.findViewById(R.id.txtTotalSemanales);
        TextView txtPedidos = dialogView.findViewById(R.id.txtPedidosSemanales);
        TextView txtEstrella = dialogView.findViewById(R.id.txtProductoEstrella);
        LinearLayout containerDias = dialogView.findViewById(R.id.containerDias);
        LinearLayout layoutCarga = dialogView.findViewById(R.id.layoutCarga);
        LinearLayout layoutContenido = dialogView.findViewById(R.id.layoutContenido);
        View btnEnviarEmail = dialogView.findViewById(R.id.btnEnviarResumenEmail);

        txtTotal.setText(formatoEuros(datos.total));
        txtPedidos.setText(datos.pedidos + (datos.pedidos == 1 ? " pedido" : " pedidos"));
        txtEstrella.setText(datos.productoEstrella);

        containerDias.removeAllViews();
        for (String dia : dias) {
            double totalDia = datos.ventasPorDia.getOrDefault(dia, 0.0);
            View row = LayoutInflater.from(activity).inflate(R.layout.item_resumen_dia, containerDias, false);
            ((TextView) row.findViewById(R.id.txtDiaNombre)).setText(dia);
            TextView txtDiaTotal = row.findViewById(R.id.txtDiaTotal);
            txtDiaTotal.setText(formatoEuros(totalDia));
            if (totalDia > 0) {
                txtDiaTotal.setTextColor(activity.getResources().getColor(R.color.secondary_brand));
            }
            containerDias.addView(row);
        }

        layoutCarga.setVisibility(View.GONE);
        layoutContenido.setVisibility(View.VISIBLE);
        btnEnviarEmail.setOnClickListener(v -> enviarCorreo(activity, datos, dias, fechaLimiteMs));
    }

    private static void enviarCorreo(Activity activity, ResumenDatos datos, List<String> dias, long fechaLimiteMs) {
        SimpleDateFormat sdfHeader = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        StringBuilder body = new StringBuilder();
        body.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        body.append("📊 SOFTMYC TPV - REPORTE DE VENTAS SEMANAL\n");
        body.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        body.append("📅 Período: ").append(sdfHeader.format(new Date(fechaLimiteMs)))
                .append(" al ").append(sdfHeader.format(new Date())).append("\n");
        body.append("💰 Facturación Total: ").append(formatoEuros(datos.total)).append("\n");
        body.append("📦 Número de Pedidos: ").append(datos.pedidos).append("\n");
        body.append("⭐ Producto Estrella: ").append(datos.productoEstrella).append("\n\n");
        body.append("📈 DESGLOSE DIARIO DE CAJA:\n");
        body.append("──────────────────────────────\n");
        for (String dia : dias) {
            body.append("• ").append(dia).append(": ")
                    .append(formatoEuros(datos.ventasPorDia.getOrDefault(dia, 0.0))).append("\n");
        }
        body.append("\n──────────────────────────────\n");
        body.append("Documento reporte generado automáticamente por SoftMYC TPV.");

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_SUBJECT, "[SoftMYC TPV] Resumen de Ventas Semanal");
        intent.putExtra(Intent.EXTRA_TEXT, body.toString());
        try {
            activity.startActivity(Intent.createChooser(intent, "Enviar reporte vía..."));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(activity, activity.getString(R.string.no_hay_aplicaciones_de_correo_configurad), Toast.LENGTH_SHORT).show();
        }
    }

    private static String formatoEuros(double importe) {
        return String.format(Locale.getDefault(), "%.2f €", importe);
    }

    /** Cierra el diálogo si la Activity se destruye mientras la consulta está en curso (evita WindowLeaked). */
    private static void cerrarDialogoAlDestruir(Activity activity, AlertDialog dialog) {
        if (!(activity instanceof LifecycleOwner)) return;
        Lifecycle lifecycle = ((LifecycleOwner) activity).getLifecycle();
        LifecycleEventObserver observer = (source, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY && dialog.isShowing()) {
                dialog.dismiss();
            }
        };
        lifecycle.addObserver(observer);
        // Al cerrarse el diálogo (normal o por destrucción) se deja de observar: no retiene el diálogo.
        dialog.setOnDismissListener(d -> lifecycle.removeObserver(observer));
    }
}
