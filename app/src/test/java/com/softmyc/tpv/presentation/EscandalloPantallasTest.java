package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;
import com.softmyc.tpv.composicion.Dependencias;
import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.local.TenantContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Pantallas de ingredientes y recetas sobre un escandallo en memoria. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EscandalloPantallasTest {

    static {
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String NEGOCIO = "escandallotest";
    private final Application app = ApplicationProvider.getApplicationContext();
    private RepositorioEscandalloFalso repo;

    @Before
    public void setUp() {
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
        TenantContext.save(SecurePreferencesHelper.getPrinterPrefs(app), NEGOCIO);
        Auth.store(app).save(AuthSession.demo(NEGOCIO, "admin", "ana", System.currentTimeMillis()));
        repo = RepositorioEscandalloFalso.conNube();
        Dependencias.setEscandalloParaTests(repo);
    }

    @After
    public void tearDown() {
        Dependencias.setEscandalloParaTests(null);
        Auth.store(app).clear();
        SecurePreferencesHelper.getSessionPrefs(app).edit().clear().commit();
        SecurePreferencesHelper.getPrinterPrefs(app).edit().clear().commit();
    }

    // ------------------------------------------------------------------ ingredientes

    @Test
    public void ingredientes_lista_stock_descuadre_y_avisos() {
        ActivityController<IngredientesActivity> c = Robolectric.buildActivity(IngredientesActivity.class).setup();
        RecyclerView lista = esperarFilas(c.get(), R.id.recyclerIngredientes, 3);

        List<String> textos = textos(lista);
        assertTrue(textos.toString(), textos.contains("Pollo|6,99 €/kg|1 kg") || textos.contains("Pollo|6.99 €/kg|1 kg"));
        assertTrue(textos.toString(), textos.stream().anyMatch(t -> t.startsWith("Pan|") && t.contains("descuadre") && t.endsWith("|0 ud")));
        assertTrue(textos.toString(), textos.stream().anyMatch(t -> t.equals("Roquefort|sin precio|sin control")));
        TextView avisos = c.get().findViewById(R.id.txtDiagnostico);
        assertEquals(View.VISIBLE, avisos.getVisibility());

        avisos.performClick();
        AlertDialog d = ultimoDialogo();
        assertTrue(((TextView) d.findViewById(android.R.id.message)).getText().toString().contains("9 Tigger"));
    }

    @Test
    public void ingredientes_buscar_filtra_sin_acentos() {
        ActivityController<IngredientesActivity> c = Robolectric.buildActivity(IngredientesActivity.class).setup();
        esperarFilas(c.get(), R.id.recyclerIngredientes, 3);

        ((EditText) c.get().findViewById(R.id.edtBuscarIngrediente)).setText("ROQUE");
        ShadowLooper.idleMainLooper();

        assertEquals(1, ((RecyclerView) c.get().findViewById(R.id.recyclerIngredientes)).getAdapter().getItemCount());
    }

    @Test
    public void ingredientes_crear_validar_y_guardar() {
        ActivityController<IngredientesActivity> c = Robolectric.buildActivity(IngredientesActivity.class).setup();
        esperarFilas(c.get(), R.id.recyclerIngredientes, 3);

        c.get().findViewById(R.id.btnNuevoIngrediente).performClick();
        AlertDialog d = ultimoDialogo();
        List<EditText> campos = editTexts((ViewGroup) d.findViewById(androidx.appcompat.R.id.custom));
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue("sin nombre no guarda", d.isShowing());
        campos.get(0).setText("Lechuga");
        campos.get(1).setText("2,5");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

        esperarFilas(c.get(), R.id.recyclerIngredientes, 4);
        assertEquals(Double.valueOf(2.5), repo.ingredientes.get(100L).precioCompra);
        assertEquals(null, repo.ingredientes.get(100L).stock);
    }

    @Test
    public void ingredientes_editar_recuento_y_borrar() {
        ActivityController<IngredientesActivity> c = Robolectric.buildActivity(IngredientesActivity.class).setup();
        RecyclerView lista = esperarFilas(c.get(), R.id.recyclerIngredientes, 3);

        fila(lista, "Pollo").performClick();
        AlertDialog d = ultimoDialogo();
        List<EditText> campos = editTexts((ViewGroup) d.findViewById(androidx.appcompat.R.id.custom));
        campos.get(2).setText("400");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        esperarA(() -> Double.valueOf(400.0).equals(repo.ingredientes.get(1L).stock));

        fila(lista, "Roquefort").performLongClick();
        (ultimoDialogo()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        esperarFilas(c.get(), R.id.recyclerIngredientes, 2);
        assertFalse(repo.ingredientes.containsKey(3L));
    }

    @Test
    public void ingredientes_abre_recetas_y_el_selector_de_csv() {
        ActivityController<IngredientesActivity> c = Robolectric.buildActivity(IngredientesActivity.class).setup();

        c.get().findViewById(R.id.btnAbrirRecetas).performClick();
        assertEquals(RecetasActivity.class.getName(),
                shadowOf(c.get()).getNextStartedActivity().getComponent().getClassName());

        c.get().findViewById(R.id.btnImportarEscandallo).performClick();
        Intent elegir = shadowOf(c.get()).getNextStartedActivity();
        assertEquals(Intent.ACTION_CHOOSER, elegir.getAction());
    }

    @Test
    public void ingredientes_importar_csv_muestra_el_informe() {
        ActivityController<IngredientesActivity> c = Robolectric.buildActivity(IngredientesActivity.class).setup();
        android.net.Uri uri = android.net.Uri.parse("content://prueba/escandallo.csv");
        shadowOf(app.getContentResolver()).registerInputStream(uri, new java.io.ByteArrayInputStream(new byte[0]));

        c.get().onActivityResult(IngredientesActivity.PEDIR_CSV, android.app.Activity.RESULT_OK, new Intent().setData(uri));

        esperarA(() -> ShadowToast.getTextOfLatestToast() != null && ShadowToast.getTextOfLatestToast().startsWith("Importado"));
    }

    // ------------------------------------------------------------------ recetas

    @Test
    public void recetas_muestra_coste_y_margen_y_los_productos_sin_receta() {
        ActivityController<RecetasActivity> c = Robolectric.buildActivity(RecetasActivity.class).setup();
        RecyclerView lista = esperarFilas(c.get(), R.id.recyclerRecetas, 2);

        String nube = textos(lista).get(0);
        assertTrue(nube, nube.startsWith("1 · Nube|"));
        assertTrue(nube, nube.contains("2,68 €") || nube.contains("2.68 €"));
        assertTrue(textos(lista).get(1).startsWith("9 · Tigger|Sin receta"));

        ((EditText) c.get().findViewById(R.id.edtBuscarReceta)).setText("tigger");
        ShadowLooper.idleMainLooper();
        assertEquals(1, lista.getAdapter().getItemCount());
    }

    @Test
    public void recetas_editar_un_producto_guarda_lineas_y_gastos() {
        ActivityController<RecetasActivity> c = Robolectric.buildActivity(RecetasActivity.class).setup();
        RecyclerView lista = esperarFilas(c.get(), R.id.recyclerRecetas, 2);

        fila(lista, "1 · Nube").performClick();
        AlertDialog d = ultimoDialogo();
        List<EditText> campos = editTexts((ViewGroup) d.findViewById(androidx.appcompat.R.id.custom));
        assertEquals(3, campos.size());   // 2 líneas + gastos añadidos
        campos.get(0).setText("200");
        campos.get(2).setText("1,5");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

        esperarA(() -> Double.valueOf(1.5).equals(repo.gastos.get(1)));
        assertEquals(200.0, repo.lineas.stream().filter(l -> l.ingredienteId == 1).findFirst().get().cantidad, 1e-9);
    }

    @Test
    public void recetas_un_producto_sin_receta_propone_sus_ingredientes_de_carta() {
        ActivityController<RecetasActivity> c = Robolectric.buildActivity(RecetasActivity.class).setup();
        RecyclerView lista = esperarFilas(c.get(), R.id.recyclerRecetas, 2);

        fila(lista, "9 · Tigger").performClick();
        AlertDialog d = ultimoDialogo();
        List<EditText> campos = editTexts((ViewGroup) d.findViewById(androidx.appcompat.R.id.custom));
        assertEquals(3, campos.size());   // Lomo, Roquefort + gastos

        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue("sin cantidades no guarda", d.isShowing());

        campos.get(0).setText("100");
        campos.get(1).setText("30");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        esperarA(() -> repo.lineas.stream().filter(l -> l.productoCodigo == 9).count() == 2);
    }

    @Test
    public void recetas_modo_extras_lista_y_edita_lo_que_gasta_cada_extra() {
        ActivityController<RecetasActivity> c = Robolectric.buildActivity(RecetasActivity.class).setup();
        esperarFilas(c.get(), R.id.recyclerRecetas, 2);

        c.get().findViewById(R.id.btnModoExtras).performClick();
        ShadowLooper.idleMainLooper();
        RecyclerView lista = c.get().findViewById(R.id.recyclerRecetas);
        assertEquals(2, lista.getAdapter().getItemCount());
        assertTrue(textos(lista).toString(), textos(lista).get(1).startsWith("Bacon||sin enlazar"));

        fila(lista, "Bacon").performClick();
        AlertDialog d = ultimoDialogo();
        ViewGroup raiz = (ViewGroup) d.findViewById(androidx.appcompat.R.id.custom);
        botones(raiz).get(0).performClick();   // "Añadir ingrediente"
        editTexts(raiz).get(0).setText("20");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

        esperarA(() -> repo.extras.stream().anyMatch(e -> e.extra.equals("Bacon")));
    }

    // ------------------------------------------------------------------ utilidades

    /** El diálogo recién abierto, ya mostrado: su onShow (que engancha el botón Guardar) va por el bucle principal. */
    private static AlertDialog ultimoDialogo() {
        ShadowLooper.idleMainLooper();
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    private static RecyclerView esperarFilas(android.app.Activity a, int id, int n) {
        RecyclerView lista = a.findViewById(id);
        esperarA(() -> lista.getAdapter() != null && lista.getAdapter().getItemCount() == n);
        lista.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY));
        lista.layout(0, 0, 1000, 4000);
        return lista;
    }

    private static List<String> textos(RecyclerView lista) {
        lista.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY));
        lista.layout(0, 0, 1000, 4000);
        List<String> r = new ArrayList<>();
        for (int i = 0; i < lista.getChildCount(); i++) {
            View v = lista.getChildAt(i);
            r.add(texto(v, R.id.txtTitulo) + "|" + texto(v, R.id.txtDetalle) + "|" + texto(v, R.id.txtValor));
        }
        return r;
    }

    private static View fila(RecyclerView lista, String tituloEmpieza) {
        textos(lista);
        for (int i = 0; i < lista.getChildCount(); i++) {
            if (texto(lista.getChildAt(i), R.id.txtTitulo).startsWith(tituloEmpieza)) return lista.getChildAt(i);
        }
        throw new AssertionError("Sin fila " + tituloEmpieza);
    }

    private static String texto(View v, int id) {
        return ((TextView) v.findViewById(id)).getText().toString();
    }

    private static List<EditText> editTexts(ViewGroup g) {
        List<EditText> r = new ArrayList<>();
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof EditText) r.add((EditText) v);
            else if (v instanceof ViewGroup) r.addAll(editTexts((ViewGroup) v));
        }
        return r;
    }

    private static List<android.widget.Button> botones(ViewGroup g) {
        List<android.widget.Button> r = new ArrayList<>();
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof android.widget.Button) r.add((android.widget.Button) v);
            else if (v instanceof ViewGroup) r.addAll(botones((ViewGroup) v));
        }
        return r;
    }

    private static void esperarA(BooleanSupplier condicion) {
        long limite = System.currentTimeMillis() + 8000;
        while (!condicion.getAsBoolean()) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) fail("Tiempo agotado");
            try { Thread.sleep(10); } catch (InterruptedException e) { throw new AssertionError(e); }
        }
        ShadowLooper.idleMainLooper();
        assertNotNull(condicion);
    }
}
