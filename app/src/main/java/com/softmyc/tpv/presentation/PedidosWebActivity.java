package com.softmyc.tpv.presentation;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.network.OrderSyncService;
import com.softmyc.tpv.data.network.PedidoWebParser;
import com.softmyc.tpv.data.printer.PedidoWebPrinter;
import com.softmyc.tpv.data.printer.TicketGenerator;

import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Pantalla de Gestión de Pedidos Web / Monitor de Cocina (KDS).
 * Disposición profesional en pantalla dividida (En Cocina / Listos para Recoger).
 * Sincroniza en tiempo real mediante consulta periódica (polling) al backend local.
 */
public class PedidosWebActivity extends BaseActivity {

    private static final String TAG = "PedidosWebActivity";

    private RecyclerView rvPendingOrders;
    private RecyclerView rvReadyOrders;
    private View emptyStateView;
    private TextView tvOrderCountHeader;
    private TextView tvPendingCount;
    private TextView tvReadyCount;
    private ImageButton btnBack;
    private MaterialButton btnRefreshOrders;
    private Spinner spinnerTiempoEsperaCocina;
    private ProgressBar pbSyncTiempoEspera;
    private ImageView ivSyncOk;
    private boolean isInitializingSpinner = true;
    private boolean isSyncingFromServer = false;
    private int currentConfirmedWaitTime = 30;

    private static final int[] TIEMPO_MINUTOS_OPCIONES = {15, 20, 25, 30, 40, 45, 60, 75, 90};
    private static final String[] TIEMPO_ETIQUETAS_OPCIONES = {"15 min", "20 min", "25 min", "30 min", "40 min", "45 min", "60 min", "75 min", "90 min"};

    private OrderSyncService orderSyncService;
    private android.os.Handler pollHandler;
    
    private PedidosWebAdapter pendingAdapter;
    private PedidosWebAdapter readyAdapter;
    
    private final List<Map<String, Object>> orderList = new ArrayList<>();
    private final List<Map<String, Object>> pendingList = new ArrayList<>();
    private final List<Map<String, Object>> readyList = new ArrayList<>();
    
    private ToneGenerator toneGenerator;
    private final java.util.Set<String> processedPendingOrders = new java.util.HashSet<>();

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            fetchOrdersFromApi();
            if (pollHandler != null) {
                pollHandler.postDelayed(this, 10000); // Polling cada 10 segundos
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_pedidos_web);

            setupBackButton();

            orderSyncService = new OrderSyncService(this);
            pollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            
            try {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            } catch (Exception e) {
                Log.e(TAG, "No se pudo inicializar ToneGenerator: " + e.getMessage());
            }

            initViews();
            setupRecyclerViews();
        } catch (Throwable t) {
            Log.e(TAG, "Error crítico en onCreate de PedidosWebActivity: " + t.getMessage(), t);
            Toast.makeText(this, getString(R.string.pedidos_web_error_iniciar_fmt, t.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startPolling();
        syncWaitTimeFromBackend();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    private void startPolling() {
        if (pollHandler != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollHandler.post(pollRunnable);
        }
    }

    private void stopPolling() {
        if (pollHandler != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    private void initViews() {
        rvPendingOrders = findViewById(R.id.rvPendingOrders);
        rvReadyOrders = findViewById(R.id.rvReadyOrders);
        emptyStateView = findViewById(R.id.emptyStateView);
        tvOrderCountHeader = findViewById(R.id.tvOrderCountHeader);
        tvPendingCount = findViewById(R.id.tvPendingCount);
        tvReadyCount = findViewById(R.id.tvReadyCount);
        btnBack = findViewById(R.id.btnBack);
        btnRefreshOrders = findViewById(R.id.btnRefreshOrders);

        btnBack.setOnClickListener(v -> finish());
        btnRefreshOrders.setOnClickListener(v -> {
            fetchOrdersFromApi();
            syncWaitTimeFromBackend();
        });

        spinnerTiempoEsperaCocina = findViewById(R.id.spinnerTiempoEsperaCocina);
        pbSyncTiempoEspera = findViewById(R.id.pbSyncTiempoEspera);
        ivSyncOk = findViewById(R.id.ivSyncOk);

        if (spinnerTiempoEsperaCocina != null) {
            ArrayAdapter<String> adapterTiempo = new ArrayAdapter<>(this, R.layout.spinner_item_dark, TIEMPO_ETIQUETAS_OPCIONES);
            adapterTiempo.setDropDownViewResource(R.layout.spinner_dropdown_dark);
            spinnerTiempoEsperaCocina.setAdapter(adapterTiempo);

            currentConfirmedWaitTime = getSecurePreferences().getInt("tiempo_espera_estimado", 30);
            int defaultPos = getPositionForMinutes(currentConfirmedWaitTime);
            spinnerTiempoEsperaCocina.setSelection(defaultPos);

            spinnerTiempoEsperaCocina.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (isInitializingSpinner) {
                        isInitializingSpinner = false;
                        return;
                    }
                    if (isSyncingFromServer) {
                        isSyncingFromServer = false;
                        return;
                    }

                    int selectedMins = (position >= 0 && position < TIEMPO_MINUTOS_OPCIONES.length)
                            ? TIEMPO_MINUTOS_OPCIONES[position]
                            : 30;

                    sendWaitTimeToBackend(selectedMins);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        // Consultar inmediatamente el tiempo real configurado en el backend
        syncWaitTimeFromBackend();
    }

    private int getPositionForMinutes(int mins) {
        for (int i = 0; i < TIEMPO_MINUTOS_OPCIONES.length; i++) {
            if (TIEMPO_MINUTOS_OPCIONES[i] == mins) {
                return i;
            }
        }
        int bestIdx = 3; // 30 min por defecto
        int minDiff = Integer.MAX_VALUE;
        for (int i = 0; i < TIEMPO_MINUTOS_OPCIONES.length; i++) {
            int diff = Math.abs(TIEMPO_MINUTOS_OPCIONES[i] - mins);
            if (diff < minDiff) {
                minDiff = diff;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private void setSpinnerSelectionSilently(int mins) {
        if (spinnerTiempoEsperaCocina == null) return;
        int pos = getPositionForMinutes(mins);
        isSyncingFromServer = true;
        spinnerTiempoEsperaCocina.setSelection(pos);
    }

    private void syncWaitTimeFromBackend() {
        if (orderSyncService == null) return;

        if (pbSyncTiempoEspera != null) pbSyncTiempoEspera.setVisibility(View.VISIBLE);
        if (ivSyncOk != null) ivSyncOk.setVisibility(View.GONE);

        orderSyncService.getWaitTime(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "No se pudo sincronizar tiempo de espera desde backend: " + e.getMessage());
                enUi(() -> {
                    if (pbSyncTiempoEspera != null) pbSyncTiempoEspera.setVisibility(View.GONE);
                    if (ivSyncOk != null) ivSyncOk.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String bodyStr = response.body().string();
                        JSONObject json = new JSONObject(bodyStr);
                        int serverMins = -1;
                        if (json.has("tiempo_espera")) {
                            serverMins = json.optInt("tiempo_espera", -1);
                        } else if (json.has("tiempoEspera")) {
                            serverMins = json.optInt("tiempoEspera", -1);
                        }

                        if (serverMins > 0) {
                            final int finalMins = serverMins;
                            enUi(() -> {
                                currentConfirmedWaitTime = finalMins;
                                getSecurePreferences().edit().putInt("tiempo_espera_estimado", finalMins).apply();
                                setSpinnerSelectionSilently(finalMins);
                                if (pbSyncTiempoEspera != null) pbSyncTiempoEspera.setVisibility(View.GONE);
                                if (ivSyncOk != null) ivSyncOk.setVisibility(View.VISIBLE);
                                Log.d(TAG, "⏱️ Tiempo de espera sincronizado desde backend: " + finalMins + " min");
                            });
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parseando respuesta de tiempo de espera: " + e.getMessage());
                } finally {
                    response.close();
                }

                enUi(() -> {
                    if (pbSyncTiempoEspera != null) pbSyncTiempoEspera.setVisibility(View.GONE);
                    if (ivSyncOk != null) ivSyncOk.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void sendWaitTimeToBackend(int selectedMins) {
        if (pbSyncTiempoEspera != null) pbSyncTiempoEspera.setVisibility(View.VISIBLE);
        if (ivSyncOk != null) ivSyncOk.setVisibility(View.GONE);

        if (orderSyncService != null) {
            orderSyncService.updateWaitTime(selectedMins, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Error de red al actualizar tiempo de espera: " + e.getMessage());
                    enUi(() -> {
                        if (pbSyncTiempoEspera != null) pbSyncTiempoEspera.setVisibility(View.GONE);
                        if (ivSyncOk != null) ivSyncOk.setVisibility(View.GONE);
                        setSpinnerSelectionSilently(currentConfirmedWaitTime);
                        Toast.makeText(PedidosWebActivity.this, getString(R.string.pedidos_web_error_red_tiempo_fmt, e.getMessage()), Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) {
                    boolean success = response.isSuccessful();
                    response.close();
                    enUi(() -> {
                        if (pbSyncTiempoEspera != null) pbSyncTiempoEspera.setVisibility(View.GONE);
                        if (success) {
                            currentConfirmedWaitTime = selectedMins;
                            getSecurePreferences().edit().putInt("tiempo_espera_estimado", selectedMins).apply();
                            if (ivSyncOk != null) ivSyncOk.setVisibility(View.VISIBLE);
                            Toast.makeText(PedidosWebActivity.this, getString(R.string.pedidos_web_tiempo_actualizado_fmt, selectedMins), Toast.LENGTH_SHORT).show();
                        } else {
                            if (ivSyncOk != null) ivSyncOk.setVisibility(View.GONE);
                            setSpinnerSelectionSilently(currentConfirmedWaitTime);
                            Toast.makeText(PedidosWebActivity.this, getString(R.string.pedidos_web_error_servidor_tiempo_fmt, response.code()), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        }
    }

    private void setupRecyclerViews() {
        // Adaptador para pedidos en cocina (preparando)
        pendingAdapter = new PedidosWebAdapter(pendingList, new PedidosWebAdapter.OrderActionListener() {
            @Override
            public void onMarkStatus(Map<String, Object> doc, String newStatus) {
                updateOrderStatus(doc, newStatus);
            }

            @Override
            public void onPrintTicket(Map<String, Object> doc) {
                printKitchenTicket(doc);
            }
        });
        rvPendingOrders.setLayoutManager(new LinearLayoutManager(this));
        rvPendingOrders.setAdapter(pendingAdapter);

        // Adaptador para pedidos listos para entregar
        readyAdapter = new PedidosWebAdapter(readyList, new PedidosWebAdapter.OrderActionListener() {
            @Override
            public void onMarkStatus(Map<String, Object> doc, String newStatus) {
                updateOrderStatus(doc, newStatus);
            }

            @Override
            public void onPrintTicket(Map<String, Object> doc) {
                printKitchenTicket(doc);
            }
        });
        rvReadyOrders.setLayoutManager(new LinearLayoutManager(this));
        rvReadyOrders.setAdapter(readyAdapter);
    }

    private void fetchOrdersFromApi() {
        orderSyncService.getOrders(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errorMsg = (e != null && e.getMessage() != null) ? e.getMessage() : "Error de conexión con el servidor";
                Log.e(TAG, "Error fetching orders: " + errorMsg);
                mostrarAviso(getString(R.string.aviso_advertencia_fmt, errorMsg), Toast.LENGTH_LONG);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Failed response code: " + response.code());
                    mostrarAviso(getString(R.string.pedidos_web_error_servidor_fmt, response.code()), Toast.LENGTH_SHORT);
                    return;
                }

                if (response.body() == null) {
                    Log.e(TAG, "Empty response body");
                    mostrarAviso("Respuesta vacía del servidor", Toast.LENGTH_SHORT);
                    return;
                }

                String body = response.body().string();
                try {
                    Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                    List<Map<String, Object>> recibidos = new Gson().fromJson(body, listType);
                    enUi(() -> aplicarPedidos(recibidos));
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing JSON orders: " + e.getMessage());
                }
            }
        });
    }

    /** En el hilo principal: sustituye la lista por los pedidos del negocio activo y auto-imprime los pendientes. */
    private void aplicarPedidos(List<Map<String, Object>> recibidos) {
        try {
            boolean isInitialLoad = orderList.isEmpty();
            int previousCount = orderList.size();
            orderList.clear();
            List<Map<String, Object>> validados = PedidoWebParser.pedidosDelNegocio(recibidos, negocioActivo());
            orderList.addAll(validados);
            applyFilter();

            boolean hasAutoPrinted = false;
            for (Map<String, Object> pedido : validados) {
                if (imprimirAutomaticamente(pedido)) {
                    hasAutoPrinted = true;
                }
            }

            // Reproducir alerta si llega un nuevo pedido tras la carga inicial o si se auto-imprime
            int activeCount = orderList.size();
            if (hasAutoPrinted || (!isInitialLoad && activeCount > previousCount)) {
                playAlertChime();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error procesando pedidos en el hilo de UI: " + t.getMessage(), t);
        }
    }

    /**
     * Imprime el ticket de un pedido pendiente que aún no se ha intentado. Solo se marca "impreso" cuando la impresora
     * confirma; si falla, el pedido se reintenta en el siguiente sondeo (ERR-05). Devuelve true si lo intentó.
     */
    private boolean imprimirAutomaticamente(Map<String, Object> pedido) {
        String orderId = PedidoWebParser.idPedido(pedido);
        if (orderId == null || !PedidoWebParser.esPendiente(pedido) || processedPendingOrders.contains(orderId)) {
            return false;
        }
        processedPendingOrders.add(orderId);
        imprimirCocina(pedido, (ok, error) -> {
            if (ok) {
                updateOrderStatus(pedido, "impreso");
            } else {
                processedPendingOrders.remove(orderId);
                mostrarAviso(getString(R.string.pedidos_web_cocina_no_impreso_reintento_fmt, error), Toast.LENGTH_LONG);
            }
        });
        return true;
    }

    private void printKitchenTicket(Map<String, Object> doc) {
        imprimirCocina(doc, (ok, error) -> mostrarAviso(ok
                ? getString(R.string.pedidos_web_cocina_impreso)
                : getString(R.string.pedidos_web_cocina_no_impreso_fmt, error), Toast.LENGTH_SHORT));
    }

    /** Imprime el ticket de cocina y entrega el resultado en el hilo principal. */
    private void imprimirCocina(Map<String, Object> pedido, PedidoWebPrinter.ResultadoImpresion resultado) {
        PedidoWebPrinter.ResultadoImpresion alVolverAUi = (ok, error) -> enUi(() -> resultado.onResult(ok, error));
        try {
            String texto = generarTicket(pedido).texto;
            PedidoWebPrinter.delDispositivo(this).imprimirTicketCocina(texto, alVolverAUi);
        } catch (Exception e) {
            Log.e(TAG, "Error generando el ticket de cocina: " + e.getMessage());
            alVolverAUi.onResult(false, e.getMessage());
        }
    }

    private TicketGenerator.TicketResult generarTicket(Map<String, Object> pedido) {
        SharedPreferences prefs = TicketGenerator.getSecurePrefs(this);
        int numeroPedido = prefs.getInt("numero_pedido", 1);
        return TicketGenerator.generarTicketOnline(this, PedidoWebParser.cliente(pedido),
                PedidoWebParser.lineas(pedido), PedidoWebParser.total(pedido), numeroPedido);
    }

    /** Negocio al que pertenecen los pedidos que se muestran: el de la sesión activa. */
    private String negocioActivo() {
        SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(this);
        String rawActiveTenant = prefs.getString("current_tenant", prefs.getString("restaurant_id", "lanube"));
        return OrderSyncService.sanitizeTenantId(rawActiveTenant);
    }

    /** Ejecuta en el hilo principal solo si la pantalla sigue viva (CAL-03). */
    private void enUi(Runnable accion) {
        AppExecutors.enUiSiViva(this, accion);
    }

    private void mostrarAviso(String mensaje, int duracion) {
        enUi(() -> Toast.makeText(this, mensaje, duracion).show());
    }

    private void applyFilter() {
        pendingList.clear();
        readyList.clear();

        for (Map<String, Object> doc : orderList) {
            String status = (String) doc.get("estado");
            if (status == null) status = "pendiente";
            
            String statusLower = status.toLowerCase();
            
            if (statusLower.contains("listo")) {
                readyList.add(doc);
            } else if (!statusLower.contains("entregado") && !statusLower.contains("cancelado")) {
                pendingList.add(doc);
            }
        }

        pendingAdapter.notifyDataSetChanged();
        readyAdapter.notifyDataSetChanged();

        tvPendingCount.setText(String.valueOf(pendingList.size()));
        tvReadyCount.setText(String.valueOf(readyList.size()));

        int totalActive = pendingList.size() + readyList.size();
        tvOrderCountHeader.setText(getResources().getQuantityString(R.plurals.pedidos_web_monitor_activos, totalActive, totalActive));

        if (totalActive == 0) {
            emptyStateView.setVisibility(View.VISIBLE);
            findViewById(R.id.dashboardLayout).setVisibility(View.GONE);
        } else {
            emptyStateView.setVisibility(View.GONE);
            findViewById(R.id.dashboardLayout).setVisibility(View.VISIBLE);
        }
    }

    private void playAlertChime() {
        if (toneGenerator != null) {
            try {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800);
            } catch (Exception e) {
                Log.e(TAG, "Error al reproducir tono de alerta: " + e.getMessage());
            }
        }
    }

    private void updateOrderStatus(Map<String, Object> doc, final String newStatus) {
        String orderId = (String) doc.get("_id");
        if (orderId == null) {
            Toast.makeText(this, getString(R.string.id_de_pedido_no_encontrado), Toast.LENGTH_SHORT).show();
            return;
        }

        String apiStatus = newStatus;
        if (newStatus.equals("¡Listo para recoger!")) {
            apiStatus = "listo";
        } else if (newStatus.equals("Retrasado (+15 min)")) {
            apiStatus = "retrasado";
        } else if (newStatus.equals("Entregado")) {
            apiStatus = "entregado";
        }

        orderSyncService.updateOrderStatus(orderId, apiStatus, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                enUi(() -> {
                    Toast.makeText(PedidosWebActivity.this, getString(R.string.error_de_red_al_actualizar_estado), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                enUi(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(PedidosWebActivity.this, getString(R.string.pedidos_web_estado_actualizado_fmt, newStatus), Toast.LENGTH_SHORT).show();
                        fetchOrdersFromApi();
                    } else {
                        Toast.makeText(PedidosWebActivity.this, getString(R.string.pedidos_web_error_actualizar_fmt, response.code()), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}
