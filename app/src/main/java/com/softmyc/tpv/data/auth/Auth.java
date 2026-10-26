package com.softmyc.tpv.data.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.local.AppDatabase;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Fachada de la autenticación: un único gestor de sesión y un único cliente HTTP (con el token y la renovación
 * automática) compartidos por toda la aplicación.
 */
public final class Auth {

    private static final String TAG = "Auth";
    private static final OkHttpClient PLAIN = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).build();

    private static volatile SessionManager manager;
    private static volatile SessionStore store;
    private static volatile OkHttpClient client;
    /** Aplicación con la que se construyeron los singletons: si cambia (solo ocurre en tests) se descartan. */
    private static volatile Context owner;
    /** Contador de caducidades de sesión: las pantallas abiertas lo observan para volver al inicio de sesión al instante. */
    private static final MutableLiveData<Integer> EXPIRATIONS = new MutableLiveData<>(0);

    private Auth() { }

    public static SessionManager manager(Context context) {
        SessionManager m = manager;
        if (m != null && owner == context.getApplicationContext()) return m;
        synchronized (Auth.class) {
            descartarSiCambiaLaAplicacion(context);
            if (manager == null) manager = build(context.getApplicationContext());
            return manager;
        }
    }

    /**
     * Único almacén de sesión de la aplicación. Debe ser una sola instancia: su exclusión mutua es lo que impide que una
     * renovación que termina tarde reescriba la sesión tras un cierre de sesión.
     */
    public static SessionStore store(Context context) {
        SessionStore s = store;
        if (s != null && owner == context.getApplicationContext()) return s;
        synchronized (Auth.class) {
            descartarSiCambiaLaAplicacion(context);
            if (store == null) store = new SessionStore(SecurePreferencesHelper.getSessionPrefs(context.getApplicationContext()));
            return store;
        }
    }

    /** Se incrementa cada vez que el servidor invalida la sesión o caduca el refresco (aviso a las pantallas abiertas). */
    public static LiveData<Integer> expirations() {
        return EXPIRATIONS;
    }

    /** Cliente HTTP para hablar con la API: añade el token y, ante un 401, renueva la sesión y reintenta. */
    public static OkHttpClient client(Context context) {
        OkHttpClient c = client;
        if (c != null && owner == context.getApplicationContext()) return c;
        synchronized (Auth.class) {
            descartarSiCambiaLaAplicacion(context);
            if (client == null) {
                SessionManager m = manager(context);
                client = PLAIN.newBuilder().addInterceptor(new AuthInterceptor(m)).authenticator(new TokenAuthenticator(m)).build();
            }
            return client;
        }
    }

    /**
     * Descarta el gestor, el almacén y el cliente en caché y pone a cero el contador de caducidades: la siguiente llamada
     * los reconstruye con el contexto actual. Solo para tests, donde cada prueba tiene su propia aplicación y no debe
     * heredar las preferencias ni la URL de la anterior.
     */
    public static void reset() {
        synchronized (Auth.class) {
            descartar();
        }
        EXPIRATIONS.postValue(0);
    }

    /** En producción la aplicación es siempre la misma; con Robolectric cada test tiene la suya. Llamar con el candado. */
    private static void descartarSiCambiaLaAplicacion(Context context) {
        Context app = context.getApplicationContext();
        if (owner != app) {
            descartar();
            owner = app;
        }
    }

    private static void descartar() {
        manager = null;
        store = null;
        client = null;
        owner = null;
    }

    private static SessionManager build(Context app) {
        SessionManager m = new SessionManager(store(app), new AuthApi(PLAIN, System::currentTimeMillis),
                () -> BackendUrl.root(app), System::currentTimeMillis);
        m.setListener(new SessionManager.Listener() {
            @Override
            public void onSessionExpired() {
                Log.i(TAG, "La sesión ha caducado o ha sido revocada por el servidor");
                Integer actual = EXPIRATIONS.getValue();
                EXPIRATIONS.postValue((actual != null ? actual : 0) + 1);
            }

            @Override
            public void onSessionChanged(AuthSession session) {
                mirror(app, session);
            }
        });
        return m;
    }

    /** Mantiene al día el rol, el usuario y el negocio activos que leen algunas pantallas, y la base local del negocio. */
    private static void mirror(Context app, AuthSession s) {
        SharedPreferences.Editor e = SecurePreferencesHelper.getPrinterPrefs(app).edit().remove("auth_token");
        if (s.role != null) e.putString("user_role", s.role);
        if (s.username != null) e.putString("user_name", s.username);
        if (s.restaurantId != null) e.putString("restaurant_id", s.restaurantId);
        e.apply();
        if (s.restaurantId != null) AppDatabase.setActiveTenantId(app, s.restaurantId);
    }
}
