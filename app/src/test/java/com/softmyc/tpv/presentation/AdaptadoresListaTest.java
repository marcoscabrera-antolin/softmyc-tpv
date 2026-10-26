package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.Category;
import com.softmyc.tpv.domain.Producto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/**
 * Adaptadores de listas de la pantalla de productos y categorías: textos formateados, celdas reutilizadas,
 * selección y casos límite (ingredientes vacíos o nulos, listas sin categorías, clics sin listener).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AdaptadoresListaTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private Context context;
    private Locale localeOriginal;

    @Before
    public void fijarLocale() {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(new Locale("es", "ES"));
        // Tema Material: los chips de categoría necesitan un tema MaterialComponents para inflarse.
        context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_MyApplication);
    }

    @After
    public void restaurarLocale() {
        Locale.setDefault(localeOriginal);
    }

    // ---------- ProductoAdapter (lista de ListaProductosActivity) ----------

    @Test
    public void productoAdapter_muestra_nombre_precio_codigo_e_ingredientes_unidos_por_coma() {
        Producto bocata = producto(12, "Bocata", 4.5, "UD", Arrays.asList("jamón", "queso"));
        ProductoAdapter adaptador = new ProductoAdapter(context, Collections.singletonList(bocata));

        View fila = adaptador.getView(0, null, new FrameLayout(context));

        assertEquals("Bocata", texto(fila, R.id.txtNombre));
        assertEquals("4,50€", texto(fila, R.id.txtPrecio));
        assertEquals("12", texto(fila, R.id.txtInicial));
        assertEquals("jamón, queso", texto(fila, R.id.txtIngredientes));
    }

    @Test
    public void productoAdapter_sin_ingredientes_deja_el_texto_vacio_tambien_si_la_lista_es_nula() {
        Producto vacio = producto(1, "Agua", 1.0, "UD", new ArrayList<>());
        Producto nulo = producto(2, "Hielo", 0.5, "UD", null);
        ProductoAdapter adaptador = new ProductoAdapter(context, Arrays.asList(vacio, nulo));

        assertEquals("", texto(adaptador.getView(0, null, new FrameLayout(context)), R.id.txtIngredientes));
        assertEquals("", texto(adaptador.getView(1, null, new FrameLayout(context)), R.id.txtIngredientes));
    }

    @Test
    public void productoAdapter_reutiliza_la_celda_y_actualiza_sus_textos() {
        ProductoAdapter adaptador = new ProductoAdapter(context, Arrays.asList(
                producto(1, "Uno", 1.0, "UD", Collections.emptyList()),
                producto(2, "Dos", 2.0, "UD", Collections.emptyList())));
        View celda = adaptador.getView(0, null, new FrameLayout(context));

        View reutilizada = adaptador.getView(1, celda, new FrameLayout(context));

        assertSame(celda, reutilizada);
        assertEquals("Dos", texto(reutilizada, R.id.txtNombre));
        assertEquals("2,00€", texto(reutilizada, R.id.txtPrecio));
    }

    @Test
    public void productoAdapter_con_elemento_nulo_devuelve_la_celda_sin_lanzar() {
        ProductoAdapter adaptador = new ProductoAdapter(context, Collections.singletonList((Producto) null));

        View fila = adaptador.getView(0, null, new FrameLayout(context));

        assertNotNull(fila);
        // La celda se devuelve tal cual (sin datos de ningún producto): no se pinta nada del elemento nulo.
        assertNotNull(fila.findViewById(R.id.txtNombre));
        assertEquals(1, adaptador.getCount());
    }

    @Test
    public void productoAdapter_copia_la_lista_recibida() {
        List<Producto> original = new ArrayList<>(Collections.singletonList(producto(1, "A", 1.0, "UD", null)));
        ProductoAdapter adaptador = new ProductoAdapter(context, original);

        original.clear();

        assertEquals(1, adaptador.getCount());
    }

    // ---------- ProductoGridAdapter (rejilla de productos del TPV) ----------

    @Test
    public void productoGrid_formatea_el_codigo_con_dos_cifras_y_el_precio_por_unidad_de_medida() throws Exception {
        Producto pera = producto(7, "Pera", 2.5, "KG", null);
        ProductoGridAdapter adaptador = new ProductoGridAdapter(p -> { });
        publicar(adaptador, Collections.singletonList(pera));

        RecyclerView.ViewHolder celda = adaptador.onCreateViewHolder(new FrameLayout(context), 0);
        adaptador.onBindViewHolder((ProductoGridAdapter.ViewHolder) celda, 0);

        assertEquals("#07", texto(celda.itemView, R.id.txtProductId));
        assertEquals("Pera", texto(celda.itemView, R.id.txtProductName));
        assertEquals("2,50€ / kg", texto(celda.itemView, R.id.txtProductPrice));
    }

    @Test
    public void productoGrid_unidad_UD_muestra_solo_el_precio() throws Exception {
        Producto bocata = producto(120, "Bocata", 4.0, "ud", null);
        ProductoGridAdapter adaptador = new ProductoGridAdapter(p -> { });
        publicar(adaptador, Collections.singletonList(bocata));

        RecyclerView.ViewHolder celda = adaptador.onCreateViewHolder(new FrameLayout(context), 0);
        adaptador.onBindViewHolder((ProductoGridAdapter.ViewHolder) celda, 0);

        assertEquals("#120", texto(celda.itemView, R.id.txtProductId));
        assertEquals("4,00€", texto(celda.itemView, R.id.txtProductPrice));
    }

    @Test
    public void productoGrid_pulsar_la_celda_notifica_el_producto_de_esa_posicion() throws Exception {
        Producto uno = producto(1, "Uno", 1.0, "UD", null);
        Producto dos = producto(2, "Dos", 2.0, "UD", null);
        List<Producto> pulsados = new ArrayList<>();
        ProductoGridAdapter adaptador = new ProductoGridAdapter(pulsados::add);
        publicar(adaptador, Arrays.asList(uno, dos));

        RecyclerView.ViewHolder celda = adaptador.onCreateViewHolder(new FrameLayout(context), 0);
        adaptador.onBindViewHolder((ProductoGridAdapter.ViewHolder) celda, 1);
        celda.itemView.performClick();

        assertEquals(Collections.singletonList(dos), pulsados);
    }

    @Test
    public void productoGrid_pulsar_sin_listener_no_lanza() throws Exception {
        ProductoGridAdapter adaptador = new ProductoGridAdapter(null);
        publicar(adaptador, Collections.singletonList(producto(1, "Uno", 1.0, "UD", null)));

        RecyclerView.ViewHolder celda = adaptador.onCreateViewHolder(new FrameLayout(context), 0);
        adaptador.onBindViewHolder((ProductoGridAdapter.ViewHolder) celda, 0);
        celda.itemView.performClick();

        assertEquals(1, adaptador.getItemCount());
    }

    @Test
    public void productoGrid_el_mismo_codigo_con_nombre_distinto_se_considera_contenido_cambiado() throws Exception {
        ProductoGridAdapter adaptador = new ProductoGridAdapter(p -> { });
        publicar(adaptador, Collections.singletonList(producto(1, "Antes", 1.0, "UD", null)));

        publicar(adaptador, Collections.singletonList(producto(1, "Después", 1.0, "UD", null)));

        RecyclerView.ViewHolder celda = adaptador.onCreateViewHolder(new FrameLayout(context), 0);
        adaptador.onBindViewHolder((ProductoGridAdapter.ViewHolder) celda, 0);
        assertEquals("Después", texto(celda.itemView, R.id.txtProductName));
    }

    // ---------- CategoriaAdapter (chips de categorías) ----------

    @Test
    public void categoria_sin_categorias_no_tiene_filas() {
        CategoriaAdapter adaptador = new CategoriaAdapter(c -> { });

        assertEquals(0, adaptador.getItemCount());
    }

    @Test
    public void categoria_pinta_el_nombre_y_el_estado_seleccionado_de_cada_chip() {
        Category todos = new Category("Todos", "Todos", 0, true);
        Category bebidas = new Category("Bebidas", "Bebidas", 0, false);
        CategoriaAdapter adaptador = new CategoriaAdapter(c -> { });
        adaptador.setCategories(Arrays.asList(todos, bebidas));

        RecyclerView.ViewHolder primera = adaptador.onCreateViewHolder(new FrameLayout(context), 0);
        adaptador.onBindViewHolder((CategoriaAdapter.ViewHolder) primera, 0);
        RecyclerView.ViewHolder segunda = adaptador.onCreateViewHolder(new FrameLayout(context), 0);
        adaptador.onBindViewHolder((CategoriaAdapter.ViewHolder) segunda, 1);

        assertEquals(2, adaptador.getItemCount());
        assertEquals("Todos", ((Chip) primera.itemView.findViewById(R.id.chipCategoria)).getText().toString());
        assertTrue(((Chip) primera.itemView.findViewById(R.id.chipCategoria)).isChecked());
        assertFalse(((Chip) segunda.itemView.findViewById(R.id.chipCategoria)).isChecked());
    }

    @Test
    public void categoria_pulsar_un_chip_deja_solo_esa_categoria_seleccionada_y_notifica() {
        Category todos = new Category("Todos", "Todos", 0, true);
        Category bebidas = new Category("Bebidas", "Bebidas", 0, false);
        Category postres = new Category("Postres", "Postres", 0, false);
        List<Category> notificadas = new ArrayList<>();
        CategoriaAdapter adaptador = new CategoriaAdapter(notificadas::add);
        adaptador.setCategories(Arrays.asList(todos, bebidas, postres));
        RecyclerView.ViewHolder fila = adaptador.onCreateViewHolder(new FrameLayout(context), 0);
        adaptador.onBindViewHolder((CategoriaAdapter.ViewHolder) fila, 1);

        fila.itemView.findViewById(R.id.chipCategoria).performClick();

        assertEquals(Collections.singletonList(bebidas), notificadas);
        assertTrue(bebidas.isSelected());
        assertFalse(todos.isSelected());
        assertFalse(postres.isSelected());
    }

    // ---------- helpers ----------

    private static Producto producto(int codigo, String nombre, double precio, String unidad, List<String> ingredientes) {
        return new Producto(codigo, nombre, precio, "Cat", ingredientes != null ? new ArrayList<>(ingredientes) : null, unidad);
    }

    private static String texto(View raiz, int id) {
        TextView vista = raiz.findViewById(id);
        assertNotNull("vista " + id + " no encontrada", vista);
        return vista.getText().toString();
    }

    /** Publica una lista en un ListAdapter y espera a que DiffUtil la confirme en el hilo principal. */
    private static void publicar(ProductoGridAdapter adaptador, List<Producto> productos) throws InterruptedException {
        CountDownLatch confirmada = new CountDownLatch(1);
        adaptador.submitList(new ArrayList<>(productos), confirmada::countDown);
        long limite = System.currentTimeMillis() + 5000;
        while (confirmada.getCount() > 0) {
            ShadowLooper.idleMainLooper();
            if (System.currentTimeMillis() > limite) {
                fail("DiffUtil no confirmó la lista");
            }
            Thread.sleep(5);
        }
        ShadowLooper.idleMainLooper();
    }
}
