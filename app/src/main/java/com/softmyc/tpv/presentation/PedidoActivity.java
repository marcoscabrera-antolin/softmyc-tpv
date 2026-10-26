package com.softmyc.tpv.presentation;
import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.*;
import com.softmyc.tpv.data.*;
import com.softmyc.tpv.domain.repository.VentaResumen;
import com.softmyc.tpv.presentation.viewmodel.PedidoViewModel;
import com.softmyc.tpv.domain.usecase.StockAdvisor;
import com.softmyc.tpv.util.FalloImpresion;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.*;

public class PedidoActivity extends BaseActivity {

    // ----------- VISTAS DE NAVEGACIÓN Y RECYCLER --------------
    private RecyclerView recyclerCategorias;
    private RecyclerView recyclerProductosGrid;

    private CategoriaAdapter categoryAdapter;
    private ProductoGridAdapter productAdapter;

    private LinearLayout btnIntroducirID, btnImprimir, btnHora;
    private View btnRecuperarPedido, btnVaciarPedido;

    // Diálogo de fallo de impresión abierto (evita apilar varios si llega otro fallo).
    private androidx.appcompat.app.AlertDialog dialogoFalloImpresion;

    // ----------- TEXTOS DINÁMICOS ----------
    private TextView txtProductoSeleccionado, textoTotal, txtHoraRecogida, txtTituloID;

    // ----------- PEDIDO ------------------
    private ListView listaPedido;

    private PedidoViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pedido);

        setupBackButton();
        inicializarComponentes();
        configurarBotones();

        viewModel = new ViewModelProvider(this).get(PedidoViewModel.class);

        setupObservers();
    }

    private void inicializarComponentes() {
        recyclerCategorias = findViewById(R.id.recyclerCategorias);
        recyclerProductosGrid = findViewById(R.id.recyclerProductosGrid);

        btnIntroducirID = findViewById(R.id.btnIntroducirID);
        txtTituloID = findViewById(R.id.txtTituloID);
        txtProductoSeleccionado = findViewById(R.id.txtProductoSeleccionado);
        
        listaPedido = findViewById(R.id.listaPedido);
        textoTotal = findViewById(R.id.textoTotal);
        btnHora = findViewById(R.id.btnHora);
        btnImprimir = findViewById(R.id.btnImprimir);
        txtHoraRecogida = findViewById(R.id.txtHoraRecogida);
        btnRecuperarPedido = findViewById(R.id.btnRecuperarPedido);
        btnVaciarPedido = findViewById(R.id.btnVaciarPedido);

        // Configurar RecyclerViews
        if (recyclerCategorias != null) {
            recyclerCategorias.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            categoryAdapter = new CategoriaAdapter(category -> {
                if (viewModel != null) viewModel.filterByCategory(category.getId());
            });
            recyclerCategorias.setAdapter(categoryAdapter);
        }

        if (recyclerProductosGrid != null) {
            int spanCount = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 4 : 3;
            recyclerProductosGrid.setLayoutManager(new GridLayoutManager(this, spanCount));
            productAdapter = new ProductoGridAdapter(this::abrirModalPersonalizacion);
            recyclerProductosGrid.setAdapter(productAdapter);
        }
    }

    private void configurarBotones() {
        if (btnIntroducirID != null) btnIntroducirID.setOnClickListener(v -> pedirIDProducto());
        btnHora.setOnClickListener(v -> pedirHora());
        btnImprimir.setOnClickListener(v -> seleccionarMetodoPagoYProcesar());
        if (btnRecuperarPedido != null) {
            btnRecuperarPedido.setOnClickListener(v -> mostrarDialogoRecuperarPedido());
        }
        if (btnVaciarPedido != null) {
            btnVaciarPedido.setOnClickListener(v -> confirmarVaciarPedido());
        }
        listaPedido.setOnItemClickListener((parent, view, position, id) -> mostrarOpcionesEdicion(position));
    }

    private void abrirModalPersonalizacion(Producto producto) {
        if (producto == null) return;
        try {
            List<IngredientOption> availableExtras = new ArrayList<>();
            if (viewModel != null && viewModel.preciosIngredientes != null && viewModel.preciosIngredientes.getValue() != null) {
                Map<String, Double> mapExtras = viewModel.preciosIngredientes.getValue();
                for (Map.Entry<String, Double> entry : mapExtras.entrySet()) {
                    if (entry != null && entry.getKey() != null) {
                        double price = entry.getValue() != null ? entry.getValue() : 0.0;
                        availableExtras.add(new IngredientOption(entry.getKey(), price, IngredientOption.Type.ANADIR));
                    }
                }
            }

            List<IngredientOption> availableSauces = new ArrayList<>();
            if (viewModel != null && viewModel.salsasDisponibles != null && viewModel.salsasDisponibles.getValue() != null) {
                List<String> listSalsas = viewModel.salsasDisponibles.getValue();
                for (String salsa : listSalsas) {
                    if (salsa != null) {
                        availableSauces.add(new IngredientOption(salsa, 0.0, IngredientOption.Type.SALSA));
                    }
                }
            }

            PersonalizacionBottomSheetDialog dialog = new PersonalizacionBottomSheetDialog(
                    producto,
                    availableExtras,
                    availableSauces,
                    customItem -> {
                        if (viewModel != null && customItem != null) {
                            avisarSiSuperaStock(customItem);
                            viewModel.agregarCustomizedItem(customItem);
                        }
                    }
            );
            if (!isFinishing() && !isDestroyed()) {
                dialog.show(getSupportFragmentManager(), "PersonalizarProducto");
            }
        } catch (Exception e) {
            android.util.Log.e("PedidoActivity", "Error al abrir modal de personalización", e);
            Toast.makeText(this, getString(R.string.no_se_pudo_abrir_la_personalizacion_de_p), Toast.LENGTH_SHORT).show();
        }
    }

    private void seleccionarMetodoPagoYProcesar() {
        List<PedidoItem> lista = viewModel.pedido.getValue();
        if (lista == null || lista.isEmpty()) {
            Toast.makeText(this, getString(R.string.pedido_vacio), Toast.LENGTH_SHORT).show();
            return;
        }

        String[] opciones = {"Efectivo", "Tarjeta"};
        // Mientras el selector está abierto, Imprimir no admite otro toque (evita dos diálogos de pago).
        habilitarImprimir(false);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.seleccionar_metodo_de_pago))
                .setItems(opciones, (dialog, which) -> {
                    String metodo = opciones[which];
                    String nombre = getNombreRestaurante();
                    viewModel.imprimirYGuardarVenta(this, nombre, metodo);
                })
                .setOnDismissListener(dialog -> habilitarImprimir(true))
                .show();
    }

    private void habilitarImprimir(boolean habilitado) {
        if (btnImprimir == null) return;
        btnImprimir.setEnabled(habilitado);
        btnImprimir.setAlpha(habilitado ? 1f : 0.5f);
    }

    /** Lee el nombre del restaurante desde las preferencias cifradas. Devuelve un fallback seguro si no hay ninguno configurado. */
    private String getNombreRestaurante() {
        try {
            android.content.SharedPreferences prefs = com.softmyc.tpv.data.SecurePreferencesHelper.getPrinterPrefs(this);
            String nombre = prefs.getString("nombre_restaurante", "");
            return nombre.isEmpty() ? "Mi Restaurante" : nombre;
        } catch (Exception e) {
            return "Mi Restaurante";
        }
    }

    @Override
    protected void onDestroy() {
        // Al destruirse (p. ej. rotación) se cierra el diálogo; el fallo sigue en el ViewModel y se vuelve a mostrar.
        if (dialogoFalloImpresion != null && dialogoFalloImpresion.isShowing()) {
            dialogoFalloImpresion.dismiss();
        }
        super.onDestroy();
    }

    private void setupObservers() {
        viewModel.pedido.observe(this, this::actualizarLista);

        viewModel.categorias.observe(this, cats -> {
            if (categoryAdapter != null && cats != null) {
                categoryAdapter.setCategories(cats);
            }
        });

        viewModel.productosFiltrados.observe(this, prods -> {
            if (productAdapter != null && prods != null) {
                productAdapter.submitList(prods);
            }
        });

        viewModel.totalPedido.observe(this, total -> {
            textoTotal.setText(getString(R.string.pedido_total_fmt, total));
        });

        viewModel.horaRecogida.observe(this, hora -> {
            txtHoraRecogida.setText(hora.isEmpty() ? "" : getString(R.string.pedido_hora_fmt, hora));
        });

        viewModel.toastMessage.observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                viewModel.clearToast();
            }
        });

        viewModel.falloImpresion.observe(this, this::mostrarDialogoFalloImpresion);
    }

    /**
     * Diálogo persistente para un ticket que no se pudo imprimir (la venta ya está guardada).
     * El fallo se limpia al pulsar una acción, así que sobrevive a una rotación sin mostrarse dos veces.
     */
    private void mostrarDialogoFalloImpresion(FalloImpresion fallo) {
        if (fallo == null || isFinishing() || isDestroyed()) return;
        if (dialogoFalloImpresion != null && dialogoFalloImpresion.isShowing()) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(fallo.getTitulo())
                .setMessage(fallo.getMensajeDialogo())
                .setCancelable(false)
                .setNegativeButton(getString(R.string.cerrar), (dialog, which) -> viewModel.clearFalloImpresion());
        if (fallo.getTicketId() > 0) {
            builder.setPositiveButton(getString(R.string.reintentar_impresion), (dialog, which) -> {
                viewModel.clearFalloImpresion();
                viewModel.reimprimirTicket(this, getNombreRestaurante(), fallo.getTicketId());
            });
        }
        dialogoFalloImpresion = builder.show();
    }

    /** Aviso no bloqueante cuando la cantidad añadida supera el stock controlado del producto. */
    private void avisarSiSuperaStock(CustomizedOrderItem customItem) {
        if (customItem.getBaseProduct() == null) return;
        int codigo = customItem.getBaseProduct().getCodigo();
        int stock = viewModel.stockDisponible(codigo);
        // Lo que ya hay en el pedido más lo que se añade, y solo para líneas en unidades (las de peso/horas no descuentan stock).
        int enPedido = StockAdvisor.cantidadUdEnPedido(viewModel.pedido.getValue(), codigo);
        StockAdvisor.Aviso aviso = StockAdvisor.evaluar(stock, customItem.getUnit(), enPedido + customItem.getQuantity());
        if (aviso.superaStock()) {
            Snackbar.make(findViewById(android.R.id.content),
                    getString(R.string.pedido_aviso_stock_fmt, customItem.getBaseProduct().getNombre(), aviso.mensaje()),
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void confirmarVaciarPedido() {
        List<PedidoItem> lista = viewModel.pedido.getValue();
        if (lista == null || lista.isEmpty()) {
            Toast.makeText(this, getString(R.string.el_pedido_ya_esta_vacio), Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.vaciar_pedido))
                .setMessage(getResources().getQuantityString(R.plurals.pedido_quitar_lineas, lista.size(), lista.size()))
                .setPositiveButton(getString(R.string.vaciar), (dialog, which) -> viewModel.vaciarPedido())
                .setNegativeButton(getString(R.string.cancelar), null)
                .show();
    }

    /** Quita la línea y ofrece deshacer en un Snackbar (restaura el item en su posición). */
    private void eliminarConDeshacer(int position, PedidoItem item) {
        viewModel.eliminarItem(position);
        Snackbar.make(findViewById(android.R.id.content),
                getString(R.string.pedido_linea_eliminada_fmt, item.getNombre()), Snackbar.LENGTH_LONG)
                .setAction("Deshacer", v -> viewModel.restaurarItem(position, item))
                .show();
    }

    private void pedirIDProducto() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.introduce_el_id_del_producto));
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton(getString(R.string.aceptar), (dialog, which) -> {
            String texto = input.getText().toString();
            if (texto.isEmpty()) return;
            try {
                int id = Integer.parseInt(texto);
                viewModel.seleccionarProductoPorId(id);
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.codigo_invalido), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(getString(R.string.cancelar), null);
        builder.show();
    }

    private void actualizarLista(List<PedidoItem> lista) {
        PedidoAdapter.Data data = PedidoAdapter.buildFromPedido(
                lista, item -> viewModel.calcularExtras(item)
        );
        listaPedido.setAdapter(new PedidoAdapter(this, data.detalles, data.totales));
    }

    private void mostrarOpcionesEdicion(int position) {
        List<PedidoItem> lista = viewModel.pedido.getValue();
        if (lista == null || position < 0 || position >= lista.size()) return;
        PedidoItem item = lista.get(position);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.pedido_opciones_fmt, item.getNombre()));
        String[] opciones = {getString(R.string.pedido_opcion_editar), getString(R.string.pedido_opcion_mas_uno),
                getString(R.string.pedido_opcion_menos_uno), getString(R.string.pedido_opcion_eliminar)};

        builder.setItems(opciones, (dialog, which) -> {
            switch (which) {
                case 0: viewModel.iniciarEdicion(position); break;
                case 1: viewModel.modificarItemCantidad(position, 1); break;
                case 2: viewModel.modificarItemCantidad(position, -1); break;
                case 3: eliminarConDeshacer(position, item); break;
            }
        });
        builder.show();
    }

    private void pedirHora() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.hora_de_recogida));
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(getString(R.string.ej_21_00));
        String horaActual = viewModel.horaRecogida.getValue();
        input.setText(horaActual != null ? horaActual : "");
        builder.setView(input);

        builder.setPositiveButton(getString(R.string.aceptar), (dialog, which) -> {
            viewModel.actualizarHoraRecogida(input.getText().toString());
        });
        builder.setNegativeButton(getString(R.string.cancelar), null);
        builder.show();
    }

    private void mostrarDialogoRecuperarPedido() {
        viewModel.getVentasRecientes(ventas -> AppExecutors.enUiSiViva(this, () -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.recuperar_pedido));

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(40, 30, 40, 30);

            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setHint(getString(R.string.introduce_numero_de_ticket));
            layout.addView(input);

            if (!ventas.isEmpty()) {
                TextView tvRecent = new TextView(this);
                tvRecent.setText(getString(R.string.no_selecciona_uno_reciente));
                tvRecent.setTextColor(0xFF94A3B8);
                tvRecent.setTextSize(14);
                tvRecent.setPadding(0, 16, 0, 8);
                layout.addView(tvRecent);

                for (VentaResumen v : ventas) {
                    Button btnVenta = new Button(this, null, android.R.attr.borderlessButtonStyle);
                    String displayTime = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(v.fechaMs));
                    btnVenta.setText(getString(R.string.pedido_ticket_boton_fmt, v.id, displayTime, v.total));
                    btnVenta.setTextColor(0xFF38BDF8);
                    btnVenta.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
                    layout.addView(btnVenta);
                }
            }

            builder.setView(layout);
            builder.setPositiveButton(getString(R.string.aceptar), (dialog, which) -> {
                String text = input.getText().toString().trim();
                if (!text.isEmpty()) {
                    try {
                        int ticketId = Integer.parseInt(text);
                        cargarPedidoRecuperado(ticketId);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, getString(R.string.numero_no_valido), Toast.LENGTH_SHORT).show();
                    }
                }
            });
            builder.setNegativeButton(getString(R.string.cancelar), null);

            AlertDialog dialog = builder.create();
            dialog.show();

            // Configurar click listeners de tickets recientes para cerrar el diálogo
            for (int i = 0; i < layout.getChildCount(); i++) {
                View child = layout.getChildAt(i);
                if (child instanceof Button) {
                    child.setOnClickListener(v1 -> {
                        String btnText = ((Button) child).getText().toString();
                        try {
                            int sharpIdx = btnText.indexOf('#');
                            int spaceIdx = btnText.indexOf(' ', sharpIdx);
                            int ticketId = Integer.parseInt(btnText.substring(sharpIdx + 1, spaceIdx));
                            cargarPedidoRecuperado(ticketId);
                            dialog.dismiss();
                        } catch (Exception ex) {
                            Toast.makeText(this, getString(R.string.error_al_seleccionar_ticket), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }));
    }

    private void cargarPedidoRecuperado(int ticketId) {
        viewModel.cargarVentaPorId(ticketId, (items, metodoPago, error) -> AppExecutors.enUiSiViva(this, () -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                return;
            }
            if (items == null || items.isEmpty()) {
                Toast.makeText(this, getString(R.string.el_pedido_no_contiene_productos_o_esta_v), Toast.LENGTH_LONG).show();
                return;
            }

            viewModel.setPedidoDirectamente(items);
            viewModel.setRecuperadoVentaId(ticketId);
            Toast.makeText(this, getString(R.string.pedido_cargado_para_editar_fmt, ticketId), Toast.LENGTH_LONG).show();
        }));
    }

}