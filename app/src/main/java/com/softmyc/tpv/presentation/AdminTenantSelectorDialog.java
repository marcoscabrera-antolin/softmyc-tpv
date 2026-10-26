package com.softmyc.tpv.presentation;

import android.app.Dialog;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.card.MaterialCardView;
import com.softmyc.tpv.R;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.network.OrderSyncService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class AdminTenantSelectorDialog extends DialogFragment {

    private static final String TAG = "AdminTenantSelector";

    public interface OnTenantSwitchedListener {
        void onTenantSwitched(String newTenantId, String newTenantName);
    }

    public static class RestaurantInfo {
        public String id;
        public String nombre;
        public boolean activo;

        public RestaurantInfo(String id, String nombre, boolean activo) {
            this.id = id;
            this.nombre = nombre;
            this.activo = activo;
        }
    }

    private OnTenantSwitchedListener listener;
    private OrderSyncService syncService;

    private LinearLayout layoutLoading;
    private TextView tvLoadingMessage;
    private LinearLayout containerTenants;
    private TextView tvErrorBanner;
    private View btnConfirm;
    private View btnCancel;

    private String selectedTenantId = null;
    private String selectedTenantName = null;
    private List<RestaurantInfo> restaurantList = new ArrayList<>();

    public static AdminTenantSelectorDialog newInstance() {
        return new AdminTenantSelectorDialog();
    }

    public void setOnTenantSwitchedListener(OnTenantSwitchedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (getResources() != null && getResources().getDisplayMetrics() != null) {
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                int targetWidth = (int) (metrics.widthPixels * 0.85);
                dialog.getWindow().setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_admin_tenant_selector, container, false);

        syncService = new OrderSyncService(getContext());

        layoutLoading = view.findViewById(R.id.layoutLoading);
        tvLoadingMessage = view.findViewById(R.id.tvLoadingMessage);
        containerTenants = view.findViewById(R.id.containerTenants);
        tvErrorBanner = view.findViewById(R.id.tvErrorBanner);
        btnConfirm = view.findViewById(R.id.btnConfirmTenantSelect);
        btnCancel = view.findViewById(R.id.btnCancelTenantSelect);
        View btnClose = view.findViewById(R.id.btnCloseDialog);

        if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());
        if (btnCancel != null) btnCancel.setOnClickListener(v -> dismiss());

        // Validar permisos de administrador
        SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(requireContext());
        String role = prefs.getString("user_role", "tpv_operator");

        if (!"admin".equalsIgnoreCase(role)) {
            Toast.makeText(getContext(), getString(R.string.acceso_denegado_solo_administradores_pue), Toast.LENGTH_LONG).show();
            dismiss();
            return view;
        }

        btnConfirm.setOnClickListener(v -> {
            if (selectedTenantId == null || selectedTenantId.trim().isEmpty()) {
                showError("Por favor, selecciona un restaurante de la lista.");
                return;
            }
            ejecutarCambioDeRestaurante(selectedTenantId);
        });

        cargarRestaurantesServidor();

        return view;
    }

    private void cargarRestaurantesServidor() {
        showLoading("Cargando lista de restaurantes autorizados...");
        hideError();

        SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(requireContext());
        // El token lo añade el interceptor del cliente HTTP compartido.
        syncService.fetchAdminRestaurants("", new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) {
                    enUiSiAdjunto(() -> {
                        hideLoading();
                        cargarFallbackLocales();
                        renderizarListaRestaurantes();
                        showError("Modo offline: Cargando negocios locales autorizados.");
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (getActivity() == null) return;

                enUiSiAdjunto(() -> {
                    hideLoading();
                    try {
                        JSONObject obj = new JSONObject(responseBody);
                        if (response.isSuccessful() && obj.optBoolean("success", false)) {
                            JSONArray arr = obj.optJSONArray("restaurants");
                            restaurantList.clear();
                            if (arr != null && arr.length() > 0) {
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject r = arr.getJSONObject(i);
                                    String id = r.optString("id");
                                    String nombre = r.optString("nombre");
                                    boolean activo = r.optBoolean("activo", true);
                                    restaurantList.add(new RestaurantInfo(id, nombre, activo));
                                }
                            } else {
                                cargarFallbackLocales();
                            }
                            renderizarListaRestaurantes();
                        } else {
                            cargarFallbackLocales();
                            renderizarListaRestaurantes();
                        }
                    } catch (Exception ex) {
                        cargarFallbackLocales();
                        renderizarListaRestaurantes();
                    }
                });
            }
        });
    }

    private void cargarFallbackLocales() {
        restaurantList.clear();
        restaurantList.add(new RestaurantInfo("lanube", "La Nube Bocatería (Hostelería)", true));
        restaurantList.add(new RestaurantInfo("elbocata", "El Bocata Gourmet (Hostelería)", true));
        restaurantList.add(new RestaurantInfo("fruteria", "Frutería El Huerto Bio (Comercio al Peso)", true));
        restaurantList.add(new RestaurantInfo("taller", "Taller Mecánico MotorSport (Servicios)", true));
        restaurantList.add(new RestaurantInfo("paqueteria", "SoftMYC Express Paquetería (Logística)", true));
        restaurantList.add(new RestaurantInfo("mayorista", "Central Mayorista de Distribución (B2B)", true));
    }

    private void renderizarListaRestaurantes() {
        if (containerTenants == null || getContext() == null) return;
        containerTenants.removeAllViews();

        SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(requireContext());
        String currentTenant = prefs.getString("restaurant_id", "lanube");

        if (selectedTenantId == null) {
            selectedTenantId = currentTenant;
        }

        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (RestaurantInfo r : restaurantList) {
            View cardView = inflater.inflate(R.layout.item_admin_tenant_card, containerTenants, false);

            MaterialCardView materialCard = cardView.findViewById(R.id.cardTenantItem);
            RadioButton radioSelect = cardView.findViewById(R.id.radioTenantSelected);
            TextView tvName = cardView.findViewById(R.id.tvTenantName);
            TextView tvId = cardView.findViewById(R.id.tvTenantId);
            TextView tvBadge = cardView.findViewById(R.id.tvTenantBadge);

            String icon = com.softmyc.tpv.data.local.TenantCatalogSeeder.getBusinessIcon(r.id);
            String sector = com.softmyc.tpv.data.local.TenantCatalogSeeder.getBusinessSector(r.id);
            tvName.setText(icon + " " + r.nombre);
            tvId.setText(tvId.getContext().getString(R.string.admin_tenant_id_sector_fmt, r.id, sector));

            boolean isCurrent = r.id.equalsIgnoreCase(selectedTenantId);
            radioSelect.setChecked(isCurrent);

            if (r.id.equalsIgnoreCase(currentTenant)) {
                tvBadge.setText(getString(R.string.sesion_activa));
                tvBadge.setTextColor(Color.parseColor("#4ADE80"));
                tvBadge.setBackgroundColor(Color.parseColor("#264ADE80"));
            } else {
                tvBadge.setText(sector.toUpperCase());
                tvBadge.setTextColor(Color.parseColor("#38BDF8"));
                tvBadge.setBackgroundColor(Color.parseColor("#2638BDF8"));
            }

            materialCard.setOnClickListener(v -> {
                selectedTenantId = r.id;
                selectedTenantName = r.nombre;
                renderizarListaRestaurantes();
            });

            containerTenants.addView(cardView);
        }
    }

    private void ejecutarCambioDeRestaurante(String targetTenantId) {
        showLoading("Cambiando contexto de negocio en el servidor...");
        hideError();

        final Context appContext = requireContext().getApplicationContext();
        final com.softmyc.tpv.data.auth.SessionManager manager = com.softmyc.tpv.data.auth.Auth.manager(appContext);
        com.softmyc.tpv.data.auth.AuthSession actual = manager.current();
        if (actual != null && actual.demo) {            // sesión de demostración: el cambio es solo local
            hideLoading();
            aplicarCambioLocal(targetTenantId);
            return;
        }
        AppExecutors.disco().execute(() -> {
            com.softmyc.tpv.data.auth.AuthApi.Result<com.softmyc.tpv.data.auth.AuthSession> r = manager.switchRestaurant(targetTenantId);
            if (getActivity() == null) return;
            enUiSiAdjunto(() -> {
                if (r.ok) {
                    aplicarCambioLocal(r.value.restaurantId != null ? r.value.restaurantId : targetTenantId);
                    return;
                }
                hideLoading();
                // Sin conexión no se cambia de negocio: el servidor archiva las ventas bajo el negocio de la sesión,
                // y una base local de otro negocio acabaría subiendo sus ventas al equivocado.
                showError(r.error == com.softmyc.tpv.data.auth.AuthApi.Error.NETWORK
                        ? "Sin conexión: para cambiar de negocio hace falta conexión con el servidor."
                        : (r.message != null ? r.message : "No se pudo cambiar de negocio."));
            });
        });
    }

    /** Ejecuta en el hilo principal solo si el diálogo sigue adjunto a una Activity viva. */
    private void enUiSiAdjunto(Runnable accion) {
        Activity activity = getActivity();
        if (!isAdded() || activity == null) return;
        AppExecutors.enUiSiViva(activity, accion);
    }

    private void aplicarCambioLocal(String targetTenantId) {
        final Context appContext = requireContext().getApplicationContext();
        AppDatabase.setActiveTenantId(appContext, targetTenantId);

        AppExecutors.disco().execute(() -> {
            boolean ready = false;
            try {
                AppDatabase db = AppDatabase.getDatabaseForTenant(appContext, targetTenantId);
                // El resultado del sembrado solo dice si había catálogo inicial: un negocio sin él abre vacío y es válido.
                com.softmyc.tpv.data.local.TenantCatalogSeeder.seedTenantSync(appContext, db, targetTenantId);
                ready = true;
            } catch (Exception e) {
                Log.e(TAG, "No se pudo abrir la base del tenant " + targetTenantId, e);
            }

            final boolean databaseReady = ready;
            if (getActivity() != null) {
                enUiSiAdjunto(() -> {
                    hideLoading();
                    if (!databaseReady) {
                        showError(getString(R.string.admin_tenant_error_abrir_bd_fmt, getNombreLegible(targetTenantId)));
                        return;
                    }
                    String nombreLegible = getNombreLegible(targetTenantId);
                    Toast.makeText(getContext(), getString(R.string.admin_tenant_negocio_activo_fmt, nombreLegible), Toast.LENGTH_LONG).show();

                    if (listener != null) {
                        listener.onTenantSwitched(targetTenantId, nombreLegible);
                    }
                    dismiss();
                });
            }
        });
    }

    private String getNombreLegible(String id) {
        return com.softmyc.tpv.data.local.TenantCatalogSeeder.getBusinessName(id);
    }

    private void showLoading(String message) {
        if (layoutLoading != null) layoutLoading.setVisibility(View.VISIBLE);
        if (tvLoadingMessage != null) tvLoadingMessage.setText(message);
        if (containerTenants != null) containerTenants.setVisibility(View.GONE);
        if (btnConfirm != null) btnConfirm.setEnabled(false);
    }

    private void hideLoading() {
        if (layoutLoading != null) layoutLoading.setVisibility(View.GONE);
        if (containerTenants != null) containerTenants.setVisibility(View.VISIBLE);
        if (btnConfirm != null) btnConfirm.setEnabled(true);
    }

    private void showError(String message) {
        if (tvErrorBanner != null) {
            tvErrorBanner.setText(message);
            tvErrorBanner.setVisibility(View.VISIBLE);
        }
    }

    private void hideError() {
        if (tvErrorBanner != null) {
            tvErrorBanner.setVisibility(View.GONE);
        }
    }
}
