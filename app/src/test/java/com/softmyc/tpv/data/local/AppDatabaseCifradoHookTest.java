package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.local.cifrado.ProveedorCifrado;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprueba que cada negocio pasa su propio fichero de base al proveedor de cifrado.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppDatabaseCifradoHookTest {

    private final List<String> ficherosPreparados = new ArrayList<>();
    private Context context;

    @Before
    public void prepararContexto() {
        context = ApplicationProvider.getApplicationContext();
    }

    @After
    public void restaurarSinCifradoParaOtrosTests() {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(context);
        AppDatabase.setProveedorCifradoParaTests(null);
    }

    @Test
    public void cadaNegocioPasaSuFicheroAlProveedorDeCifrado() {
        AppDatabase.setProveedorCifradoParaTests(grabador());

        AppDatabase.getDatabaseForTenant(context, "elbocata");
        AppDatabase.getDatabaseForTenant(context, "lanube");

        assertEquals(List.of("softmyc_elbocata.db", "softmyc_database.db"), ficherosPreparados);
    }

    private ProveedorCifrado grabador() {
        return (ctx, fichero) -> {
            ficherosPreparados.add(fichero.getName());
            return null;
        };
    }
}
