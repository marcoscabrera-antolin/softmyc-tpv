package com.softmyc.tpv.presentation;
import com.softmyc.tpv.R;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.TenantContext;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.auth.SessionStore;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * Habilita el botón de navegación hacia atrás tanto para la Toolbar nativa
     * como para vistas personalizadas con ID btnBack o btnVolver.
     */
    protected void setupBackButton() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        android.view.View btnBack = findViewById(R.id.btnBack);
        if (btnBack == null) {
            btnBack = findViewById(R.id.btnVolver);
        }
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private Integer expiracionesVistas;

    @Override
    protected void onStart() {
        super.onStart();
        if (!requiresSession()) return;
        // Si el servidor invalida la sesión con la pantalla abierta, se vuelve al inicio de sesión al instante.
        Auth.expirations().observe(this, n -> {
            if (expiracionesVistas == null) { expiracionesVistas = n; return; }
            if (!n.equals(expiracionesVistas)) {
                expiracionesVistas = n;
                irAlLogin("Sesión caducada: vuelve a iniciar sesión");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (requiresSession()) {
            comprobarSesion();
        }
    }

    /** Si es false, el control de sesión no se aplica (p. ej. la pantalla de login). */
    protected boolean requiresSession() {
        return true;
    }

    protected SharedPreferences getSecurePreferences() {
        return SecurePreferencesHelper.getSessionPrefs(this);
    }

    private void comprobarSesion() {
        // La guarda lee siempre el almacén de sesión: getSecurePreferences() puede estar redefinido
        // (AjustesActivity devuelve las de impresora, donde no existe la sesión).
        SessionStore store = Auth.store(this);
        AuthSession sesion = store.load();
        if (sesion != null && sesion.restaurantId != null
                && !TenantContext.hasActive(SecurePreferencesHelper.getPrinterPrefs(this))) {
            AppDatabase.setActiveTenantId(this, sesion.restaurantId);   // el negocio de la sesión es la fuente de verdad
        }
        SessionPolicy.State estado = SessionPolicy.evaluate(sesion, System.currentTimeMillis(), isOnline());
        if (estado == SessionPolicy.State.EXPIRED) {
            store.clear();
            irAlLogin("Sesión caducada: vuelve a iniciar sesión");
        } else if (estado == SessionPolicy.State.NO_SESSION) {
            irAlLogin(null);
        } else if (SessionPolicy.needsBusiness(sesion)) {
            irAlLogin(null);   // administrador que aún no ha elegido negocio: el inicio de sesión le abre el selector
        } else if (!TenantContext.hasActive(SecurePreferencesHelper.getPrinterPrefs(this))) {
            irAlLogin(null);   // sesión sin negocio utilizable: sin él no hay base local a la que acceder
        } else if (estado == SessionPolicy.State.NEEDS_REFRESH) {
            renovarSesionEnSegundoPlano(sesion.accessToken);
        }
    }

    /** Renueva el acceso sin bloquear la pantalla. Si el servidor ya no reconoce la sesión, se vuelve al inicio de sesión. */
    private void renovarSesionEnSegundoPlano(String accesoCaducado) {
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            AuthSession renovada = Auth.manager(app).refreshIfStale(accesoCaducado);
            if (renovada == null) runOnUiThread(() -> irAlLogin("Sesión caducada: vuelve a iniciar sesión"));
        }, "renovar-sesion").start();
    }

    protected boolean isOnline() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
            return info != null && info.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void irAlLogin(String mensaje) {
        if (isFinishing()) return;
        if (mensaje != null) Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
