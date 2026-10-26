package com.softmyc.tpv.presentation.viewmodel;

import com.softmyc.tpv.R;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.repo.CatalogoTenantRepositoryRoom;
import com.softmyc.tpv.domain.repository.CatalogoTenantRepository;
import com.softmyc.tpv.data.auth.AuthApi;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.auth.SessionStore;
import com.softmyc.tpv.presentation.SessionPolicy;
import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.softmyc.tpv.BuildConfig;

import com.softmyc.tpv.data.SecurePreferencesHelper;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginViewModel extends AndroidViewModel {
    private final Textos textos;


    private final MutableLiveData<String> _currentUser = new MutableLiveData<>(null);
    public final LiveData<String> currentUser = _currentUser;

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public final LiveData<String> errorMessage = _errorMessage;

    private final MutableLiveData<Boolean> _loginSuccess = new MutableLiveData<>();
    public final LiveData<Boolean> loginSuccess = _loginSuccess;

    private final MutableLiveData<Boolean> _navigateToMainDirectly = new MutableLiveData<>();
    public final LiveData<Boolean> navigateToMainDirectly = _navigateToMainDirectly;

    private final MutableLiveData<Boolean> _showTenantSelector = new MutableLiveData<>(false);
    public final LiveData<Boolean> showTenantSelector = _showTenantSelector;

    private final MutableLiveData<java.util.List<TenantInfo>> _tenantsList = new MutableLiveData<>(new java.util.ArrayList<>());
    public final LiveData<java.util.List<TenantInfo>> tenantsList = _tenantsList;

    private final java.util.concurrent.ExecutorService io = java.util.concurrent.Executors.newSingleThreadExecutor();
    // Credenciales del intento en curso mientras el usuario elige negocio: solo en memoria y hasta que elige o cancela.
    private volatile String pendingUser;
    private volatile String pendingPassword;
    private final MutableLiveData<java.util.List<AuthApi.Restaurant>> _chooseTenant = new MutableLiveData<>();
    public final LiveData<java.util.List<AuthApi.Restaurant>> chooseTenant = _chooseTenant;

    public static class TenantInfo {
        public String id;
        public String nombre;

        public TenantInfo(String id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }
    }

    private final CatalogoTenantRepository catalogo;
    /** Ejecutor del alta del negocio tras el login; en producción el de disco compartido, en tests uno síncrono. */
    private final java.util.concurrent.Executor disco;

    public LoginViewModel(@NonNull Application application) {
        this(application, com.softmyc.tpv.presentation.AppExecutors.disco());
    }

    public LoginViewModel(@NonNull Application application, @NonNull java.util.concurrent.Executor disco) {
        super(application);
        this.textos = new TextosAndroid(application);
        catalogo = new CatalogoTenantRepositoryRoom(application);
        this.disco = disco;
    }

    /** Si ya hay una sesión utilizable (acceso vigente, o refresco vigente si hay que renovar), se entra directamente. */
    public void checkSession(boolean isOnline) {
        AuthSession s = Auth.store(getApplication()).load();
        SessionPolicy.State estado = SessionPolicy.evaluate(s, System.currentTimeMillis(), isOnline);
        if (estado == SessionPolicy.State.VALID || estado == SessionPolicy.State.NEEDS_REFRESH) {
            _currentUser.setValue(s.username);
            if (SessionPolicy.needsBusiness(s)) _showTenantSelector.setValue(true);   // aún no eligió negocio
            else {
                com.softmyc.tpv.data.local.AppDatabase.setActiveTenantId(getApplication(), s.restaurantId);
                _navigateToMainDirectly.setValue(true);
            }
        }
    }

    public void loginEnServidor(String usernameOrEmail, String password) {
        loginEnServidor(usernameOrEmail, password, null);
    }

    /** Inicia sesión contra la API. {@code restaurantId} solo se indica cuando el usuario tiene varios negocios y ha elegido uno. */
    public void loginEnServidor(String usernameOrEmail, String password, String restaurantId) {
        final String u = usernameOrEmail != null ? usernameOrEmail.trim().toLowerCase() : "";

        // La autenticación demo local sin contraseña solo existe en builds de depuración.
        if (BuildConfig.DEBUG && restaurantId == null && intentarLoginDemo(u)) {
            return;
        }

        io.execute(() -> {
            AuthApi.Result<AuthSession> r = Auth.manager(getApplication()).login(u, password, restaurantId);
            if (r.ok) {
                pendingUser = null;
                pendingPassword = null;
                _currentUser.postValue(r.value.username);
                if (r.value.restaurantId == null) {
                    _showTenantSelector.postValue(true);   // administrador de plataforma: elige negocio después
                } else {
                    selectTenant(r.value.restaurantId);
                }
                return;
            }
            switch (r.error) {
                case CHOOSE_TENANT:
                    pendingUser = u;
                    pendingPassword = password;
                    _chooseTenant.postValue(r.restaurants);
                    break;
                case NETWORK:
                    _errorMessage.postValue(textos.cadena(R.string.login_error_conexion));
                    break;
                case INVALID_CREDENTIALS:
                    _errorMessage.postValue(textos.cadena(R.string.login_credenciales_incorrectas));
                    break;
                case RATE_LIMITED:
                    _errorMessage.postValue(textos.cadena(R.string.login_demasiados_intentos));
                    break;
                case BUSY:
                    _errorMessage.postValue(textos.cadena(R.string.login_servidor_ocupado));
                    break;
                case FORBIDDEN:
                    _errorMessage.postValue(r.message != null ? r.message : textos.cadena(R.string.login_sin_acceso_negocio));
                    break;
                default:
                    _errorMessage.postValue(textos.cadena(R.string.login_error_servidor));
            }
        });
    }

    /** El usuario con varios negocios ha elegido uno: se repite el acceso indicándolo (la contraseña solo vive en memoria hasta ahora). */
    public void elegirNegocio(String restaurantId) {
        String u = pendingUser, p = pendingPassword;
        pendingUser = null;
        pendingPassword = null;
        _chooseTenant.setValue(null);
        if (u != null && p != null) loginEnServidor(u, p, restaurantId);
    }

    public void cancelarEleccionNegocio() {
        pendingUser = null;
        pendingPassword = null;
        _chooseTenant.setValue(null);
    }

    /** Login de demostración multi-sector (solo debug): no valida contraseña ni contacta con el servidor. */
    private boolean intentarLoginDemo(String u) {
        if ("admin".equals(u) || "administrador".equals(u)) {
            autenticarUsuarioDemo("admin", "admin", "Super Administrador (Root)", true);
            return true;
        } else if ("fruteria".equals(u) || "operador_fruteria".equals(u)) {
            autenticarUsuarioDemo("fruteria", "tpv_operator", "Operador Frutería Bio", false);
            return true;
        } else if ("taller".equals(u) || "operador_taller".equals(u)) {
            autenticarUsuarioDemo("taller", "tpv_operator", "Operador Taller Mecánico", false);
            return true;
        } else if ("paqueteria".equals(u) || "operador_paqueteria".equals(u)) {
            autenticarUsuarioDemo("paqueteria", "tpv_operator", "Operador SoftMYC Paquetería", false);
            return true;
        } else if ("mayorista".equals(u) || "operador_mayorista".equals(u)) {
            autenticarUsuarioDemo("mayorista", "tpv_operator", "Operador Central Mayorista", false);
            return true;
        } else if ("lanube".equals(u) || "nube".equals(u) || "operador_nube".equals(u)) {
            autenticarUsuarioDemo("lanube", "tpv_operator", "Operador La Nube", false);
            return true;
        } else if ("elbocata".equals(u) || "bocata".equals(u) || "operador_bocata".equals(u)) {
            autenticarUsuarioDemo("elbocata", "tpv_operator", "Operador El Bocata", false);
            return true;
        }
        return false;
    }

    private void autenticarUsuarioDemo(String tenantId, String role, String userName, boolean showSelector) {
        guardarSesion(AuthSession.demo(tenantId, role, userName, System.currentTimeMillis()));
        _currentUser.postValue(userName);

        if (showSelector) {
            _showTenantSelector.postValue(true);
        } else {
            selectTenant(tenantId);
        }
    }

    /** Guarda la sesión en el almacén de sesión. Los tokens no se copian a ningún otro sitio. */
    private void guardarSesion(AuthSession session) {
        Auth.store(getApplication()).save(session);
        SecurePreferencesHelper.getPrinterPrefs(getApplication()).edit()
                .remove("auth_token")
                .putString("user_role", session.role)
                .putString("user_name", session.username)
                .putString("restaurant_id", session.restaurantId)
                .apply();
        com.softmyc.tpv.data.local.AppDatabase.setActiveTenantId(getApplication(), session.restaurantId);
    }

    public void selectTenant(String tenantId) {
        final String activeTenant = com.softmyc.tpv.data.local.TenantContext.canonicalId(tenantId);
        if (activeTenant == null) {
            _errorMessage.setValue(com.softmyc.tpv.data.local.TenantContext.NO_TENANT_MESSAGE);
            return;
        }
        com.softmyc.tpv.data.local.AppDatabase.setActiveTenantId(getApplication(), activeTenant);

        disco.execute(() -> {
            try {
                catalogo.sembrar(activeTenant);
            } catch (Exception e) {
                android.util.Log.e("LoginViewModel", "Error inicializando base de datos para tenant: " + activeTenant, e);
            }

            _showTenantSelector.postValue(false);
            _loginSuccess.postValue(true);
        });
    }

    /**
     * La huella solo reabre una sesión que ya existe y sigue vigente; nunca concede una sesión por sí sola.
     * (La ligadura criptográfica de la huella a una clave del Keystore llega en la fase 5.)
     */
    public void onBiometricSuccess() {
        AuthSession s = Auth.store(getApplication()).load();
        SessionPolicy.State estado = SessionPolicy.evaluate(s, System.currentTimeMillis(), true);
        if (SessionPolicy.needsBusiness(s) && estado != SessionPolicy.State.EXPIRED) {
            _showTenantSelector.setValue(true);
        } else if (estado == SessionPolicy.State.VALID || estado == SessionPolicy.State.NEEDS_REFRESH) {
            com.softmyc.tpv.data.local.AppDatabase.setActiveTenantId(getApplication(), s.restaurantId);
            _loginSuccess.setValue(true);
        } else {
            _errorMessage.setValue(textos.cadena(R.string.login_sesion_caducada));
        }
    }

    @Override
    protected void onCleared() {
        io.shutdown();
        pendingUser = null;
        pendingPassword = null;
        super.onCleared();
    }
}
