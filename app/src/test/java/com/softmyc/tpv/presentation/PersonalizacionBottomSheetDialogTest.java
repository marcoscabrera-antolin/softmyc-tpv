package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.button.MaterialButton;
import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.CustomizedOrderItem;
import com.softmyc.tpv.domain.IngredientOption;
import com.softmyc.tpv.domain.Producto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Modal de personalización: los cuatro modos según el sector (gastronomía, granel, servicios y mayorista),
 * steppers, límite de salsas, validación de tara y el item que se entrega al carrito.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PersonalizacionBottomSheetDialogTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private Locale localeOriginal;
    private ActivityController<Anfitrion> controlador;
    private List<CustomizedOrderItem> entregados;

    /** Activity anfitriona con el tema Material que usa el modal. */
    public static class Anfitrion extends AppCompatActivity {
        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            setTheme(R.style.Theme_MyApplication);
            super.onCreate(savedInstanceState);
        }
    }

    @Before
    public void setUp() {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(new Locale("es", "ES"));
        controlador = Robolectric.buildActivity(Anfitrion.class).create().start().resume().visible();
        entregados = new ArrayList<>();
    }

    @After
    public void tearDown() {
        Locale.setDefault(localeOriginal);
    }

    @Test
    public void gastronomia_muestra_emoji_de_la_categoria_ingredientes_y_precio_base() {
        Producto bocadillo = producto(12, "Bocadillo Serrano", 3.5, "Bocadillos", "UD", Arrays.asList("Jamón", "Queso"));

        DialogFragment modal = abrir(bocadillo, Collections.emptyList(), Collections.emptyList());

        assertEquals("🥖 12. Bocadillo Serrano", texto(modal, R.id.txtModalTitle));
        assertEquals("Jamón, Queso", texto(modal, R.id.txtDescripcionBase));
        assertEquals("Precio base: 3,50€", texto(modal, R.id.txtPrecioBase));
    }

    @Test
    public void producto_sin_ingredientes_se_describe_como_individual() {
        Producto agua = producto(1, "Agua", 1.0, "Bebidas", "UD", null);

        DialogFragment modal = abrir(agua, Collections.emptyList(), Collections.emptyList());

        assertEquals("Producto individual", texto(modal, R.id.txtDescripcionBase));
    }

    @Test
    public void stepper_de_gastronomia_sube_y_no_baja_de_uno() {
        DialogFragment modal = abrir(producto(2, "Café", 1.5, "Bebidas", "UD", null),
                Collections.emptyList(), Collections.emptyList());

        clic(modal, R.id.btnMoreQuantity);
        clic(modal, R.id.btnMoreQuantity);
        assertEquals("3", texto(modal, R.id.txtQuantity));

        for (int i = 0; i < 5; i++) clic(modal, R.id.btnLessQuantity);
        assertEquals("1", texto(modal, R.id.txtQuantity));
    }

    @Test
    public void confirmar_entrega_el_item_con_ingredientes_quitados_extras_y_salsas() {
        Producto bocadillo = producto(12, "Bocadillo", 3.5, "Bocadillos", "UD", Arrays.asList("Jamón", "Queso"));
        IngredientOption extra = new IngredientOption("Bacon", 1.0, IngredientOption.Type.ANADIR);
        IngredientOption salsa = new IngredientOption("Alioli", 0.0, IngredientOption.Type.SALSA);
        DialogFragment modal = abrir(bocadillo, Collections.singletonList(extra), Collections.singletonList(salsa));

        chip(modal, R.id.chipGroupQuitar, 0).performClick();
        chip(modal, R.id.chipGroupAnadir, 0).performClick();
        chip(modal, R.id.chipGroupSalsas, 0).performClick();
        clic(modal, R.id.btnMoreQuantity);
        clic(modal, R.id.btnConfirmAdd);

        assertEquals(1, entregados.size());
        CustomizedOrderItem item = entregados.get(0);
        assertEquals(2, item.getQuantity());
        assertEquals(Collections.singletonList("Jamón"), item.getRemovedIngredients());
        assertEquals("Bacon", item.getAddedIngredients().get(0).getName());
        assertEquals(1, item.getAddedSauces().size());
        assertEquals("Alioli", item.getAddedSauces().get(0).getName());
        assertEquals(9.0, new com.softmyc.tpv.domain.usecase.CalcularTotalUseCase().calcularTotal(
                Collections.singletonList(item.toPedidoItem()), Collections.singletonMap("Bacon", 1.0)), 1e-9); // 2 x (3,50 + 1,00)
        controlador.get().getSupportFragmentManager().executePendingTransactions();
        assertFalse("el modal se cierra tras confirmar", modal.getDialog() != null && modal.getDialog().isShowing());
    }

    @Test
    public void confirmar_sin_listener_no_lanza_y_cierra_el_modal() {
        DialogFragment modal = abrirSinListener(producto(1, "Agua", 1.0, "Bebidas", "UD", null));

        clic(modal, R.id.btnConfirmAdd);

        assertTrue(entregados.isEmpty());
    }

    @Test
    public void como_mucho_tres_salsas_y_la_cuarta_se_desmarca_con_aviso() {
        List<IngredientOption> salsas = Arrays.asList(
                new IngredientOption("Ketchup", 0.0, IngredientOption.Type.SALSA),
                new IngredientOption("Mostaza", 0.0, IngredientOption.Type.SALSA),
                new IngredientOption("Alioli", 0.0, IngredientOption.Type.SALSA),
                new IngredientOption("Picante", 0.0, IngredientOption.Type.SALSA));
        DialogFragment modal = abrir(producto(4, "Patatas", 2.0, "Complementos", "UD", null),
                Collections.emptyList(), salsas);

        for (int i = 0; i < 4; i++) chip(modal, R.id.chipGroupSalsas, i).performClick();

        assertTrue(chip(modal, R.id.chipGroupSalsas, 2).isChecked());
        assertFalse("la cuarta salsa debe quedar desmarcada", chip(modal, R.id.chipGroupSalsas, 3).isChecked());
        assertEquals("⚠️ Máximo 3 salsas por producto", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void granel_con_peso_que_no_supera_la_tara_no_permite_confirmar() {
        DialogFragment modal = abrir(producto(7, "Pera", 2.5, "Frutas", "KG", null),
                Collections.emptyList(), Collections.emptyList());
        // Tara de caja de madera (0,350 kg) con un peso de 0,250 kg
        chip(modal, R.id.chipGroupAnadir, 3).performClick();
        chip(modal, R.id.chipGroupQuitar, 0).performClick();

        assertFalse(((MaterialButton) modal.getView().findViewById(R.id.btnConfirmAdd)).isEnabled());
        assertEquals("El peso debe superar la tara del envase", texto(modal, R.id.txtTotalEstimado));

        clic(modal, R.id.btnConfirmAdd);
        assertTrue(entregados.isEmpty());
    }

    @Test
    public void granel_confirma_peso_neto_con_la_tara_elegida() {
        DialogFragment modal = abrir(producto(7, "Pera", 2.5, "Frutas", "KG", null),
                Collections.emptyList(), Collections.emptyList());
        chip(modal, R.id.chipGroupAnadir, 1).performClick();
        chip(modal, R.id.chipGroupQuitar, 2).performClick();

        clic(modal, R.id.btnConfirmAdd);

        assertEquals(1, entregados.size());
        CustomizedOrderItem item = entregados.get(0);
        assertEquals(1.0, item.getDecimalQuantity(), 1e-9);
        assertEquals(0.050, item.getTareKg(), 1e-9);
        assertEquals("KG", item.getUnit());
    }

    @Test
    public void granel_desmarcar_la_tara_vuelve_a_cero() {
        DialogFragment modal = abrir(producto(7, "Pera", 2.5, "Frutas", "KG", null),
                Collections.emptyList(), Collections.emptyList());
        chip(modal, R.id.chipGroupAnadir, 1).performClick();

        chip(modal, R.id.chipGroupAnadir, 1).performClick();
        clic(modal, R.id.btnConfirmAdd);

        assertEquals(0.0, entregados.get(0).getTareKg(), 1e-9);
    }

    @Test
    public void servicios_avanzan_en_medias_horas_y_el_suplemento_entra_en_el_item() {
        DialogFragment modal = abrir(producto(9, "Mano de obra", 40.0, "Taller", "HORA", null),
                Collections.emptyList(), Collections.emptyList());
        chip(modal, R.id.chipGroupQuitar, 3).performClick();
        assertEquals("2,0 h", texto(modal, R.id.txtQuantity));

        clic(modal, R.id.btnMoreQuantity);
        assertEquals("2,5 h", texto(modal, R.id.txtQuantity));

        clic(modal, R.id.btnLessQuantity);
        clic(modal, R.id.btnLessQuantity);
        clic(modal, R.id.btnLessQuantity);
        clic(modal, R.id.btnLessQuantity);
        assertEquals("0,5 h", texto(modal, R.id.txtQuantity));
        clic(modal, R.id.btnConfirmAdd);
        assertEquals(1, entregados.size());
        assertEquals(0.5, entregados.get(0).getDecimalQuantity(), 1e-9);
    }

    @Test
    public void servicios_el_suplemento_de_desplazamiento_se_incluye_al_confirmar() {
        DialogFragment modal = abrir(producto(9, "Mano de obra", 40.0, "Taller", "HORA", null),
                Collections.emptyList(), Collections.emptyList());
        chip(modal, R.id.chipGroupAnadir, 1).performClick();

        clic(modal, R.id.btnConfirmAdd);

        List<String> nombres = new ArrayList<>();
        for (IngredientOption opcion : entregados.get(0).getAddedIngredients()) nombres.add(opcion.getName());
        assertTrue(nombres.contains("Desplazamiento Domicilio (+15.00€)"));
    }

    @Test
    public void mayorista_salta_por_tramos_de_volumen_y_el_stepper_avanza_de_cinco_en_cinco() {
        DialogFragment modal = abrir(producto(401, "Aceite 5L", 45.0, "Mayorista Aceites", "UD", null),
                Collections.emptyList(), Collections.emptyList());
        assertEquals("10", texto(modal, R.id.txtQuantity));

        chip(modal, R.id.chipGroupQuitar, 1).performClick();
        assertEquals("25", texto(modal, R.id.txtQuantity));

        clic(modal, R.id.btnMoreQuantity);
        assertEquals("30", texto(modal, R.id.txtQuantity));
        clic(modal, R.id.btnConfirmAdd);

        assertEquals(30, entregados.get(0).getQuantity());
    }

    @Test
    public void mayorista_el_stepper_no_baja_de_cinco() {
        DialogFragment modal = abrir(producto(401, "Aceite 5L", 45.0, "Mayorista Aceites", "UD", null),
                Collections.emptyList(), Collections.emptyList());

        clic(modal, R.id.btnLessQuantity);
        clic(modal, R.id.btnLessQuantity);

        assertEquals("5", texto(modal, R.id.txtQuantity));
    }

    @Test
    public void producto_nulo_abre_el_modal_con_el_producto_vacio_sin_fallar() {
        DialogFragment modal = abrir(null, Collections.emptyList(), Collections.emptyList());

        assertNotNull(modal.getView());
    }

    private DialogFragment abrir(Producto producto, List<IngredientOption> extras, List<IngredientOption> salsas) {
        PersonalizacionBottomSheetDialog dialogo = new PersonalizacionBottomSheetDialog(producto, extras, salsas,
                entregados::add);
        return mostrar(dialogo);
    }

    private DialogFragment abrirSinListener(Producto producto) {
        return mostrar(new PersonalizacionBottomSheetDialog(producto, null, null, null));
    }

    private DialogFragment mostrar(PersonalizacionBottomSheetDialog dialogo) {
        dialogo.show(controlador.get().getSupportFragmentManager(), "personalizacion-test");
        controlador.get().getSupportFragmentManager().executePendingTransactions();
        ShadowLooper.idleMainLooper();
        assertNotNull("el modal debe tener vista", dialogo.getView());
        return dialogo;
    }

    private static Producto producto(int codigo, String nombre, double precio, String categoria, String unidad,
                                     List<String> ingredientes) {
        return new Producto(codigo, nombre, precio, categoria, ingredientes != null ? new ArrayList<>(ingredientes) : null, unidad);
    }

    private static String texto(DialogFragment modal, int id) {
        TextView vista = modal.getView().findViewById(id);
        assertNotNull("vista " + id, vista);
        return vista.getText().toString();
    }

    private static void clic(DialogFragment modal, int id) {
        View vista = modal.getView().findViewById(id);
        assertNotNull("botón " + id, vista);
        vista.performClick();
    }

    private static com.softmyc.tpv.presentation.LightChipView chip(DialogFragment modal, int grupo, int indice) {
        ViewGroup contenedor = modal.getView().findViewById(grupo);
        assertNotNull("grupo " + grupo, contenedor);
        View hijo = contenedor.getChildAt(indice);
        assertTrue("no hay chip " + indice + " en el grupo", hijo instanceof com.softmyc.tpv.presentation.LightChipView);
        return (com.softmyc.tpv.presentation.LightChipView) hijo;
    }
}
