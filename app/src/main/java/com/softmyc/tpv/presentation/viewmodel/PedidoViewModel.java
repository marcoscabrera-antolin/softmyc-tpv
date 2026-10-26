package com.softmyc.tpv.presentation.viewmodel;

import com.softmyc.tpv.R;
import android.app.Application;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.domain.Category;
import com.softmyc.tpv.domain.CustomizedOrderItem;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.usecase.AgregarItemAlPedidoUseCase;
import com.softmyc.tpv.domain.usecase.CalcularTotalUseCase;
import com.softmyc.tpv.data.repo.ImpresionVentaFactory;
import com.softmyc.tpv.data.sync.SincronizacionServidor;
import com.softmyc.tpv.data.sync.SyncManager;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.repo.CatalogoTenantRepositoryRoom;
import com.softmyc.tpv.data.repo.ItemsSerializerSync;
import com.softmyc.tpv.data.repo.PrinterSettingsPrefs;
import com.softmyc.tpv.data.repo.TicketComposerAndroid;
import com.softmyc.tpv.data.repo.TicketPrinterAndroid;
import com.softmyc.tpv.data.repo.ProductoRepositoryRoom;
import com.softmyc.tpv.data.repo.VentaRepositoryRoom;
import com.softmyc.tpv.domain.repository.CatalogoTenantRepository;
import com.softmyc.tpv.domain.repository.ProductoRepository;
import com.softmyc.tpv.domain.repository.VentaRepository;
import com.softmyc.tpv.domain.repository.VentaResumen;
import com.softmyc.tpv.util.FalloImpresion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PedidoViewModel extends AndroidViewModel {
    private final Textos textos;

    // --- Use Cases ---
    private final CalcularTotalUseCase calcularTotalUseCase = new CalcularTotalUseCase();
    private final AgregarItemAlPedidoUseCase agregarItemUseCase = new AgregarItemAlPedidoUseCase();

    // Se escribe desde el hilo de disco (al guardar la venta) y se lee desde la interfaz.
    private volatile Integer _recuperadoVentaId = null;

    public Integer getRecuperadoVentaId() { return _recuperadoVentaId; }
    public void setRecuperadoVentaId(Integer id) { this._recuperadoVentaId = id; }

    public interface OnVentaLoadedListener {
        void onLoaded(List<PedidoItem> items, String metodoPago, String error);
    }

    /** Número de ventas que ofrece el diálogo «Recuperar pedido». */
    private static final int VENTAS_RECIENTES = 5;

    public interface OnVentasRecientesListener {
        void onResult(List<VentaResumen> ventas);
    }

    private final MutableLiveData<List<Producto>> _productosDisponibles = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<Producto>> productosDisponibles = _productosDisponibles;

    private final MutableLiveData<List<Producto>> _productosFiltrados = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<Producto>> productosFiltrados = _productosFiltrados;

    private final MutableLiveData<List<Category>> _categorias = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<Category>> categorias = _categorias;

    private final MutableLiveData<List<String>> _ingredientesDisponibles = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<String>> ingredientesDisponibles = _ingredientesDisponibles;

    private final MutableLiveData<Map<String, Double>> _preciosIngredientes = new MutableLiveData<>(new HashMap<>());
    public final LiveData<Map<String, Double>> preciosIngredientes = _preciosIngredientes;

    private final MutableLiveData<List<String>> _salsasDisponibles = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<String>> salsasDisponibles = _salsasDisponibles;

    private final MutableLiveData<List<PedidoItem>> _pedido = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<PedidoItem>> pedido = _pedido;

    private final MutableLiveData<Producto> _productoActual = new MutableLiveData<>(null);
    public final LiveData<Producto> productoActual = _productoActual;

    private final MutableLiveData<PedidoItem> _itemTemporal = new MutableLiveData<>(null);
    public final LiveData<PedidoItem> itemTemporal = _itemTemporal;

    private final MutableLiveData<Integer> _cantidadActual = new MutableLiveData<>(1);
    public final LiveData<Integer> cantidadActual = _cantidadActual;

    private final MutableLiveData<String> _horaRecogida = new MutableLiveData<>("");
    public final LiveData<String> horaRecogida = _horaRecogida;

    private final MutableLiveData<String> _ipImpresora = new MutableLiveData<>("192.168.0.20");
    public final LiveData<String> ipImpresora = _ipImpresora;

    private static final String TAG = "PedidoViewModel";
    private final MutableLiveData<String> _toastMessage = new MutableLiveData<>(null);
    public final LiveData<String> toastMessage = _toastMessage;

    // Fallo al imprimir una venta ya guardada: la Activity lo muestra en un diálogo persistente.
    private final MutableLiveData<FalloImpresion> _falloImpresion = new MutableLiveData<>(null);
    public final LiveData<FalloImpresion> falloImpresion = _falloImpresion;

    private final MutableLiveData<Double> _totalPedido = new MutableLiveData<>(0.0);
    public final LiveData<Double> totalPedido = _totalPedido;

    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();

    private final VentaRepository ventas;
    private final ReimpresionTicket reimpresion;
    private final CatalogoLocal catalogoLocal;
    private final SincronizacionServidor sincronizacion;
    private final CobroVenta cobro;

    public PedidoViewModel(@NonNull Application application) {
        super(application);
        this.textos = new TextosAndroid(application);
        ProductoRepository productos = new ProductoRepositoryRoom(application);
        CatalogoTenantRepository catalogo = new CatalogoTenantRepositoryRoom(application);
        ventas = new VentaRepositoryRoom(application);
        catalogoLocal = new CatalogoLocal(productos, catalogo,
                () -> AppDatabase.getActiveTenantId(application));
        sincronizacion = new SincronizacionServidor(application, diskExecutor, catalogo,
                this::cargarDatosDesdeRoom, _toastMessage::postValue);
        cobro = new CobroVenta(ImpresionVentaFactory::crear, diskExecutor, textos, _toastMessage::postValue,
                _falloImpresion::postValue, () -> _recuperadoVentaId = null,
                this::sincronizarVentasPendientesServidor, this::confirmarVentaGuardada);
        reimpresion = new ReimpresionTicket(diskExecutor, ventas, new ItemsSerializerSync(),
                new TicketComposerAndroid(application), new TicketPrinterAndroid(application),
                new PrinterSettingsPrefs(application), _toastMessage, _falloImpresion,
                _productosDisponibles, _preciosIngredientes, _ipImpresora);

        // 1. Carga inmediata de los datos locales (Room)
        cargarDatosDesdeRoom();

        // 2. Configura la IP de la impresora
        cargarConfiguracionImpresora();

        // 3. Sincronización en segundo plano con el servidor remoto (MongoDB)
        sincronizarConServidor();
    }

    /** Publica el pedido nuevo y recalcula su total. */
    private void publicarPedido(List<PedidoItem> lista) {
        _pedido.setValue(lista);
        actualizarTotal(lista);
    }

    /** Recalcula el total; si el pedido tiene una línea incoherente avisa en vez de tumbar la pantalla (NEG-06). */
    private void actualizarTotal(List<PedidoItem> lista) {
        try {
            _totalPedido.setValue(calcularTotalUseCase.calcularTotal(lista, _preciosIngredientes.getValue()));
        } catch (com.softmyc.tpv.domain.CalculoPedidoException e) {
            Log.w(TAG, "No se pudo calcular el total del pedido: " + e.getMessage());
            _totalPedido.setValue(0.0);   // mejor 0 con aviso que un importe desfasado del pedido que se ve
            _toastMessage.setValue(textos.cadena(R.string.aviso_advertencia_fmt, e.getMessage()));
        }
    }

    public void clearToast() {
        _toastMessage.setValue(null);
    }

    public void clearFalloImpresion() {
        _falloImpresion.setValue(null);
    }

    public void sincronizarConServidor() {
        sincronizacion.sincronizarConServidor();
    }

    public void sincronizarVentasPendientesServidor() {
        sincronizacion.sincronizarVentasPendientes();
    }

    private void cargarDatosDesdeRoom() {
        diskExecutor.execute(() -> {
            try {
                aplicarCatalogo(catalogoLocal.cargar());
            } catch (Exception e) {
                _toastMessage.postValue(textos.cadena(R.string.pedido_error_bd_local));
            }
        });
    }

    private void aplicarCatalogo(CatalogoLocal.Cargado c) {
        stockPorCodigo = c.stockPorCodigo();
        _productosDisponibles.postValue(c.productos());
        _categorias.postValue(c.categorias());
        _productosFiltrados.postValue(c.productos());
        _ingredientesDisponibles.postValue(c.nombresExtras());
        _preciosIngredientes.postValue(c.preciosExtras());
        _salsasDisponibles.postValue(c.salsas());
    }

    public void cargarConfiguracionImpresora() {
        String localIp = SecurePreferencesHelper.getPrinterPrefs(getApplication()).getString("impresora_ip", "192.168.0.20");
        _ipImpresora.setValue(localIp);
    }

    public void seleccionarProductoPorId(int id) {
        List<Producto> disponibles = _productosDisponibles.getValue();
        if (disponibles == null || disponibles.isEmpty()) {
            _toastMessage.setValue(textos.cadena(R.string.pedido_buscando_bd_local));
            return;
        }

        Producto encontrado = null;
        for (Producto p : disponibles) {
            if (p.getCodigo() == id) {
                encontrado = copiaProducto(p);
                break;
            }
        }

        if (encontrado != null) {
            _productoActual.setValue(encontrado);
            int cant = _cantidadActual.getValue() != null ? _cantidadActual.getValue() : 1;
            _itemTemporal.setValue(new PedidoItem(encontrado, cant));
            _toastMessage.setValue(textos.cadena(R.string.pedido_seleccionado_fmt, encontrado.getNombre()));
        } else {
            _toastMessage.setValue(textos.cadena(R.string.pedido_id_no_encontrado_fmt, id));
        }
    }

    private static Producto copiaProducto(Producto p) {
        return new Producto(p.getCodigo(), p.getNombre(), p.getPrecio(), p.getCategoria(),
                p.getIngredientes() != null ? new ArrayList<>(p.getIngredientes()) : new ArrayList<>(),
                p.getUnidadMedida());
    }

    public void actualizarCantidad(int cantidad) {
        _cantidadActual.setValue(cantidad);
        PedidoItem temp = _itemTemporal.getValue();
        if (temp != null) {
            temp.setCantidad(cantidad);
            _itemTemporal.setValue(temp);
        }
    }

    public void setIngredientesQuitados(List<String> quitados) {
        PedidoItem temp = _itemTemporal.getValue();
        if (temp != null) {
            temp.getIngredientesQuitados().clear();
            temp.getIngredientesQuitados().addAll(quitados);
            _itemTemporal.setValue(temp);
        }
    }

    public void setIngredientesExtra(List<String> extras) {
        PedidoItem temp = _itemTemporal.getValue();
        if (temp != null) {
            temp.getIngredientesExtra().clear();
            temp.getIngredientesExtra().addAll(extras);
            _itemTemporal.setValue(temp);
        }
    }

    public void setSalsas(List<String> salsas) {
        PedidoItem temp = _itemTemporal.getValue();
        if (temp != null) {
            temp.getSalsas().clear();
            temp.getSalsas().addAll(salsas);
            _itemTemporal.setValue(temp);
        }
    }

    public void filterByCategory(String categoryName) {
        List<Producto> disponibles = _productosDisponibles.getValue();
        if (disponibles == null) return;

        List<Producto> filtrados = new ArrayList<>();

        if (categoryName == null || categoryName.equalsIgnoreCase("TODOS") || categoryName.equalsIgnoreCase("Todos")) {
            filtrados.addAll(disponibles);
        } else {
            String normTarget = com.softmyc.tpv.util.CategoryUtils.capitalizeCategory(categoryName);
            for (Producto p : disponibles) {
                if (p.getCategoria() != null) {
                    String normProd = com.softmyc.tpv.util.CategoryUtils.capitalizeCategory(p.getCategoria());
                    if (normProd.equalsIgnoreCase(normTarget)) {
                        filtrados.add(p);
                    }
                }
            }
        }

        Collections.sort(filtrados, (a, b) -> Integer.compare(a.getCodigo(), b.getCodigo()));
        _productosFiltrados.setValue(filtrados);
    }

    public void agregarCustomizedItem(CustomizedOrderItem customItem) {
        if (customItem == null || customItem.getBaseProduct() == null) return;
        
        PedidoItem item = customItem.toPedidoItem();

        AgregarItemAlPedidoUseCase.Resultado r = agregarItemUseCase.agregar(_pedido.getValue(), item);
        if (r.isOk()) {
            publicarPedido(r.pedidoActualizado);
            _toastMessage.setValue(textos.cadena(R.string.pedido_anadido_fmt, customItem.getBaseProduct().getNombre()));
        } else {
            _toastMessage.setValue(r.error);
        }
    }

    public void agregarAlPedido() {
        PedidoItem temp = _itemTemporal.getValue();
        if (temp == null) {
            _toastMessage.setValue(textos.cadena(R.string.pedido_selecciona_producto));
            return;
        }
        AgregarItemAlPedidoUseCase.Resultado r = agregarItemUseCase.agregar(_pedido.getValue(), temp);
        if (r.isOk()) {
            publicarPedido(r.pedidoActualizado);
            _itemTemporal.setValue(null);
            _productoActual.setValue(null);
            _cantidadActual.setValue(1);
        } else {
            _toastMessage.setValue(r.error);
        }
    }

    /** Stock del catálogo cargado para el código (-1 = sin control de stock o desconocido). Solo aviso: no bloquea la venta. */
    public int stockDisponible(int codigo) {
        Integer stock = stockPorCodigo.get(codigo);
        return stock != null ? stock : -1;
    }

    /** Vuelve a insertar un item eliminado en su posición original (deshacer). */
    public void restaurarItem(int position, PedidoItem item) {
        if (item == null) return;
        List<PedidoItem> actual = _pedido.getValue();
        List<PedidoItem> nueva = new ArrayList<>(actual != null ? actual : new ArrayList<>());
        nueva.add(Math.max(0, Math.min(position, nueva.size())), item);
        publicarPedido(nueva);
    }

    /** Quita todas las líneas del pedido en curso (sin confirmación: la confirmación la pide la Activity). */
    public void vaciarPedido() {
        cleanPedido();
    }

    public void eliminarItem(int position) {
        AgregarItemAlPedidoUseCase.Resultado r = agregarItemUseCase.eliminar(_pedido.getValue(), position);
        if (r.isOk()) {
            publicarPedido(r.pedidoActualizado);
        }
    }

    public void modificarItemCantidad(int position, int delta) {
        AgregarItemAlPedidoUseCase.Resultado r = agregarItemUseCase.modificarCantidad(_pedido.getValue(), position, delta);
        if (r.isOk()) {
            publicarPedido(r.pedidoActualizado);
        }
    }

    public void iniciarEdicion(int position) {
        List<PedidoItem> lista = _pedido.getValue();
        if (lista == null || position < 0 || position >= lista.size()) return;

        PedidoItem itemAEditar = lista.get(position);
        PedidoItem tempCopy = new PedidoItem(itemAEditar);
        Producto pOriginal = itemAEditar.getProducto();
        Producto prodCopy = copiaProducto(pOriginal);

        AgregarItemAlPedidoUseCase.Resultado r = agregarItemUseCase.iniciarEdicion(lista, position);
        if (r.isOk()) {
            publicarPedido(r.pedidoActualizado);
            _productoActual.setValue(prodCopy);
            _itemTemporal.setValue(tempCopy);
            _cantidadActual.setValue(tempCopy.getCantidad());
            _toastMessage.setValue(textos.cadena(R.string.pedido_editando_fmt, prodCopy.getNombre()));
        }
    }

    public void actualizarHoraRecogida(String hora) {
        _horaRecogida.setValue(hora);
    }

    /** Delegado al use case — mantenido público para que PedidoAdapter pueda usarlo via lambda. */
    public double calcularExtras(PedidoItem item) {
        return calcularTotalUseCase.calcularExtras(item, _preciosIngredientes.getValue());
    }

    // Stock por código del catálogo cargado desde Room (-1 = sin control). Se reemplaza entero al recargar.
    private volatile Map<Integer, Integer> stockPorCodigo = new HashMap<>();

    /** Delega en {@link CobroVenta}: el ViewModel no sabe de impresoras ni de Room. */
    public void imprimirYGuardarVenta(Context context, String nombreTienda, String metodoPago) {
        cobro.cobrar(context, nombreTienda, metodoPago, _recuperadoVentaId, _pedido.getValue(),
                _horaRecogida.getValue(), _ipImpresora.getValue(), _preciosIngredientes.getValue());
    }

    private void confirmarVentaGuardada() {
        cleanPedido();
        _toastMessage.setValue(textos.cadena(R.string.pedido_venta_guardada));
    }

    /**
     * Vuelve a imprimir el ticket de una venta guardada. Si falla, se vuelve a mostrar el diálogo de fallo.
     * El ticket no conserva la hora de recogida (no se guarda en la venta).
     */
    public void reimprimirTicket(Context context, String nombreTienda, int ticketId) {
        reimpresion.reimprimir(context, nombreTienda, ticketId);
    }

    public void cleanPedido() {
        _pedido.setValue(new ArrayList<>());
        _totalPedido.setValue(0.0);
        _recuperadoVentaId = null;
        _horaRecogida.setValue("");
    }

    public void setPedidoDirectamente(List<PedidoItem> items) {
        publicarPedido(items);
    }

    public void getVentasRecientes(OnVentasRecientesListener listener) {
        diskExecutor.execute(() -> {
            try {
                // Solo se leen las 5 últimas ventas, no todo el histórico.
                List<VentaResumen> recientes = ventas.getRecientes(VENTAS_RECIENTES);
                listener.onResult(recientes != null ? recientes : new ArrayList<>());
            } catch (Exception e) {
                listener.onResult(new ArrayList<>());
            }
        });
    }

    public void cargarVentaPorId(int id, OnVentaLoadedListener listener) {
        diskExecutor.execute(() -> {
            try {
                VentaResumen venta = ventas.getById(id);
                if (venta == null) {
                    listener.onLoaded(null, null, textos.cadena(R.string.pedido_numero_no_encontrado_fmt, id));
                    return;
                }
                List<PedidoItem> itemsDeserializados = SyncManager.deserializarItems(venta.itemsJson, _productosDisponibles.getValue());
                listener.onLoaded(itemsDeserializados, venta.metodoPago, null);
            } catch (Exception e) {
                listener.onLoaded(null, null, textos.cadena(R.string.pedido_error_cargar_venta_fmt, e.getMessage()));
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (diskExecutor != null && !diskExecutor.isShutdown()) {
            diskExecutor.shutdown();
        }
    }
}
