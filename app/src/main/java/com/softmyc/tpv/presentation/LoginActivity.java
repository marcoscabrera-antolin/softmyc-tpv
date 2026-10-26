package com.softmyc.tpv.presentation;

import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthApi;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.auth.BiometricUnlockKeys;
import com.softmyc.tpv.data.auth.BiometricUnlockPolicy;
import com.softmyc.tpv.data.auth.SessionStore;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.R;
import com.softmyc.tpv.presentation.viewmodel.LoginViewModel;

import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;

public class LoginActivity extends BaseActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private FloatingActionButton btnBio;
    private View loginContainer;

    private LoginViewModel viewModel;
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private BiometricUnlockPolicy.Action accionBiometricaPendiente = BiometricUnlockPolicy.Action.HIDDEN;

    @Override
    protected boolean requiresSession() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        loginContainer = findViewById(R.id.loginContainer);
        if (loginContainer != null && loginContainer.getBackground() instanceof AnimationDrawable) {
            AnimationDrawable animationDrawable = (AnimationDrawable) loginContainer.getBackground();
            animationDrawable.setEnterFadeDuration(2000);
            animationDrawable.setExitFadeDuration(4000);
            animationDrawable.start();
        }

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnBio = findViewById(R.id.btnBio);

        setupBiometrics();
        setupObservers();
        setupDemoProfiles();

        viewModel.checkSession(isDeviceOnline());

        btnLogin.setOnClickListener(v -> ejecutarLogin());

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            ejecutarLogin();
            return true;
        });

        btnBio.setOnClickListener(v -> lanzarBiometria());
    }

    private void ejecutarLogin() {
        ocultarTeclado();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(LoginActivity.this, getString(R.string.ingresa_usuario_y_contrasena), Toast.LENGTH_SHORT).show();
            return;
        }
        viewModel.loginEnServidor(email, password);
    }

    private void setupDemoProfiles() {
        // Los perfiles de demostración solo existen en compilaciones de depuración
        if (!com.softmyc.tpv.BuildConfig.DEBUG) {
            View label = findViewById(R.id.txtDemoProfilesLabel);
            View chips = findViewById(R.id.scrollDemoProfiles);
            if (label != null) label.setVisibility(View.GONE);
            if (chips != null) chips.setVisibility(View.GONE);
            return;
        }
        vincularBotonDemo(R.id.btnDemoAdmin, "admin", "admin");
        vincularBotonDemo(R.id.btnDemoFruteria, "fruteria", "fruteria");
        vincularBotonDemo(R.id.btnDemoTaller, "taller", "taller");
        vincularBotonDemo(R.id.btnDemoPaqueteria, "paqueteria", "paqueteria");
        vincularBotonDemo(R.id.btnDemoMayorista, "mayorista", "mayorista");
        vincularBotonDemo(R.id.btnDemoNube, "nube", "nube");
        vincularBotonDemo(R.id.btnDemoBocata, "bocata", "bocata");
    }

    private void vincularBotonDemo(int buttonId, String user, String pass) {
        View btn = findViewById(buttonId);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                etEmail.setText(user);
                etPassword.setText(pass);
                ejecutarLogin();
            });
        }
    }

    private void ocultarTeclado() {
        View view = getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private void setupObservers() {
        viewModel.loginSuccess.observe(this, success -> {
            if (success) {
                irAlTPV();
            }
        });

        viewModel.navigateToMainDirectly.observe(this, navigate -> {
            if (navigate) {
                irAlTPV();
            }
        });

        viewModel.errorMessage.observe(this, error -> {
            if (error != null) {
                Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.chooseTenant.observe(this, restaurantes -> {
            if (restaurantes != null && !restaurantes.isEmpty()) mostrarEleccionDeNegocio(restaurantes);
        });
        viewModel.showTenantSelector.observe(this, show -> {
            if (Boolean.TRUE.equals(show)) {
                mostrarSelectorRestaurantesAdmin();
            }
        });
    }

    /** El usuario tiene varios negocios: se le pide elegir uno antes de entrar. */
    private void mostrarEleccionDeNegocio(java.util.List<AuthApi.Restaurant> restaurantes) {
        String[] nombres = new String[restaurantes.size()];
        for (int i = 0; i < nombres.length; i++) nombres[i] = restaurantes.get(i).nombre;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.en_que_negocio_vas_a_trabajar))
                .setItems(nombres, (d, which) -> viewModel.elegirNegocio(restaurantes.get(which).id))
                .setNegativeButton(getString(R.string.cancelar), (d, w) -> viewModel.cancelarEleccionNegocio())
                .setOnCancelListener(d -> viewModel.cancelarEleccionNegocio())
                .show();
    }

    private void mostrarSelectorRestaurantesAdmin() {
        AdminTenantSelectorDialog dialog = AdminTenantSelectorDialog.newInstance();
        dialog.setOnTenantSwitchedListener((newTenantId, newTenantName) -> {
            irAlTPV();
        });
        dialog.show(getSupportFragmentManager(), "AdminTenantSelector");
    }

    private void setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(LoginActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                alAutenticarBiometria(result.getCryptoObject());
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.acceso_biometrico_softmyc))
                .setSubtitle(getString(R.string.toca_el_sensor_para_entrar))
                .setNegativeButtonText(getString(R.string.usar_contrasena))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();
        actualizarBotonBiometria();
    }

    /** Decide qué hace el botón de huella ahora mismo (ver {@link BiometricUnlockPolicy}). */
    private BiometricUnlockPolicy.Action accionBiometrica() {
        AuthSession s = Auth.store(this).load();
        SessionPolicy.State estado = SessionPolicy.evaluate(s, System.currentTimeMillis(), isDeviceOnline());
        boolean sesionUtilizable = estado == SessionPolicy.State.VALID || estado == SessionPolicy.State.NEEDS_REFRESH;
        boolean hayRegistro = Auth.store(this).loadBiometricEnrollment() != null;
        return BiometricUnlockPolicy.decide(BiometricUnlockKeys.isStrongBiometricReady(this), sesionUtilizable, hayRegistro);
    }

    private void actualizarBotonBiometria() {
        boolean visible = accionBiometrica() != BiometricUnlockPolicy.Action.HIDDEN;
        btnBio.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void lanzarBiometria() {
        accionBiometricaPendiente = accionBiometrica();
        if (accionBiometricaPendiente == BiometricUnlockPolicy.Action.HIDDEN) {
            Toast.makeText(this, getString(R.string.acceso_con_huella_no_disponible_inicia_s), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Cipher cipher = accionBiometricaPendiente == BiometricUnlockPolicy.Action.ENROLL
                    ? BiometricUnlockKeys.newEnrollCipher()
                    : BiometricUnlockKeys.newUnlockCipher(Auth.store(this).loadBiometricEnrollment().iv);
            biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
        } catch (GeneralSecurityException e) {
            reiniciarBiometria(getString(R.string.la_huella_ya_no_es_valida_inicia_sesion_), e);
        }
    }

    /** Solo se llega aquí tras una autenticación del Keystore; la sesión se reabre únicamente si el descifrado cuadra. */
    private void alAutenticarBiometria(BiometricPrompt.CryptoObject crypto) {
        Cipher cipher = crypto != null ? crypto.getCipher() : null;
        if (cipher == null) {
            reiniciarBiometria(getString(R.string.no_se_pudo_verificar_la_huella_inicia_se), null);
            return;
        }
        try {
            if (accionBiometricaPendiente == BiometricUnlockPolicy.Action.ENROLL) {
                enrolarBiometria(cipher);
            } else if (!desbloquearConBiometria(cipher)) {
                reiniciarBiometria(getString(R.string.la_huella_no_coincide_con_el_registro_in), null);
                return;
            }
            Toast.makeText(getApplicationContext(), getString(R.string.huella_ok), Toast.LENGTH_SHORT).show();
            viewModel.onBiometricSuccess();
        } catch (GeneralSecurityException e) {
            reiniciarBiometria(getString(R.string.la_huella_ya_no_es_valida_inicia_sesion_), e);
        }
    }

    private void enrolarBiometria(Cipher cipher) throws GeneralSecurityException {
        byte[] ciphertext = cipher.doFinal(BiometricUnlockPolicy.unlockMarker());
        Auth.store(this).saveBiometricEnrollment(new SessionStore.BiometricEnrollment(cipher.getIV(), ciphertext));
    }

    private boolean desbloquearConBiometria(Cipher cipher) throws GeneralSecurityException {
        SessionStore.BiometricEnrollment registro = Auth.store(this).loadBiometricEnrollment();
        return registro != null && BiometricUnlockPolicy.isUnlockMarker(cipher.doFinal(registro.ciphertext));
    }

    /** Borra el registro y la clave: el siguiente acceso será con contraseña y podrá volver a registrarse la huella. */
    private void reiniciarBiometria(String mensaje, Exception causa) {
        if (causa != null) android.util.Log.w("LoginActivity", "Fallo biométrico", causa);
        Auth.store(this).clearBiometricEnrollment();
        BiometricUnlockKeys.delete();
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
        actualizarBotonBiometria();
    }

    private void irAlTPV() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean isDeviceOnline() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (Exception e) {
            return true;
        }
    }
}