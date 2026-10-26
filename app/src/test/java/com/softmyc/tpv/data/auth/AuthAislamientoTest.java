package com.softmyc.tpv.data.auth;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Los singletons de {@link Auth} no deben sobrevivir al cambio de aplicación (cada test Robolectric tiene la suya). */
@RunWith(RobolectricTestRunner.class)
public class AuthAislamientoTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private final Application base = ApplicationProvider.getApplicationContext();

    @Before
    @After
    public void limpiar() {
        Auth.reset();
    }

    @Test
    public void con_la_misma_aplicacion_devuelve_las_mismas_instancias() {
        Context a = aplicacionFalsa();

        assertSame(Auth.store(a), Auth.store(a));
        assertSame(Auth.manager(a), Auth.manager(a));
        assertSame(Auth.client(a), Auth.client(a));
    }

    @Test
    public void al_cambiar_de_aplicacion_reconstruye_almacen_gestor_y_cliente() {
        Context a = aplicacionFalsa();
        SessionStore storeA = Auth.store(a);
        SessionManager managerA = Auth.manager(a);
        okhttp3.OkHttpClient clientA = Auth.client(a);

        Context b = aplicacionFalsa();

        assertNotSame(storeA, Auth.store(b));
        assertNotSame(managerA, Auth.manager(b));
        assertNotSame(clientA, Auth.client(b));
    }

    /** Una aplicación distinta en cada llamada, con las mismas preferencias reales debajo. */
    private Context aplicacionFalsa() {
        return new ContextWrapper(base) {
            @Override
            public Context getApplicationContext() {
                return this;
            }
        };
    }
}
