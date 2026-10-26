package com.softmyc.tpv.data.local;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppDatabaseTenantTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        SecurePreferencesHelper.getPrinterPrefs(context).edit().clear().commit();
    }

    @After
    public void cerrarBasesDeNegocioDelTest() {
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(context);
    }

    @Test
    public void aliasesOfTheSameBusinessShareOneDatabaseInstance() {
        AppDatabase byAlias = AppDatabase.getDatabaseForTenant(context, "softmyc_elbocata");

        assertSame(byAlias, AppDatabase.getDatabaseForTenant(context, "elbocata"));
        assertSame(byAlias, AppDatabase.getDatabaseForTenant(context, "elbocata_v2"));
    }

    @Test(expected = TenantContext.NoActiveTenantException.class)
    public void databaseForInvalidTenantIsRefusedInsteadOfOpeningAFileNamedAfterIt() {
        AppDatabase.getDatabaseForTenant(context, "../otra/base");
    }

    @Test(expected = TenantContext.NoActiveTenantException.class)
    public void databaseWithoutActiveTenantIsRefusedInsteadOfDefaultingToLaNube() {
        AppDatabase.getDatabase(context);
    }

    @Test
    public void activeTenantIsReadFromTheSessionOnly() {
        assertNull(AppDatabase.getActiveTenantId(context));

        AppDatabase.setActiveTenantId(context, "fruteria");

        assertEquals("fruteria", AppDatabase.getActiveTenantId(context));
        AppDatabase.setActiveTenantId(context, "  ");
        assertNull(AppDatabase.getActiveTenantId(context));
    }
}
