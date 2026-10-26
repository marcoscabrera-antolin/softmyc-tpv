package com.softmyc.tpv.presentation.viewmodel;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.port.ItemsDeserializer;
import com.softmyc.tpv.domain.port.PrinterSettings;
import com.softmyc.tpv.domain.port.TicketComposer;
import com.softmyc.tpv.domain.port.TicketPrinter;
import com.softmyc.tpv.domain.repository.VentaRepository;
import com.softmyc.tpv.domain.repository.VentaResumen;
import com.softmyc.tpv.util.FalloImpresion;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Reimprime el ticket de una venta ya guardada. Los datos del catálogo, la IP y los ajustes se leen al ejecutar la
 * tarea (en el hilo de disco). Depende solo de los puertos del dominio.
 */
final class ReimpresionTicket {
    private static final String TAG = "PedidoViewModel";

    private final ExecutorService executor;
    private final VentaRepository ventas;
    private final ItemsDeserializer deserializador;
    private final TicketComposer compositor;
    private final TicketPrinter impresora;
    private final PrinterSettings ajustes;
    private final MutableLiveData<String> toast;
    private final MutableLiveData<FalloImpresion> falloImpresion;
    private final LiveData<List<Producto>> productosDisponibles;
    private final LiveData<Map<String, Double>> preciosIngredientes;
    private final LiveData<String> ipImpresora;

    ReimpresionTicket(ExecutorService executor, VentaRepository ventas, ItemsDeserializer deserializador,
                      TicketComposer compositor, TicketPrinter impresora, PrinterSettings ajustes,
                      MutableLiveData<String> toast, MutableLiveData<FalloImpresion> falloImpresion,
                      LiveData<List<Producto>> productosDisponibles,
                      LiveData<Map<String, Double>> preciosIngredientes, LiveData<String> ipImpresora) {
        this.executor = executor;
        this.ventas = ventas;
        this.deserializador = deserializador;
        this.compositor = compositor;
        this.impresora = impresora;
        this.ajustes = ajustes;
        this.toast = toast;
        this.falloImpresion = falloImpresion;
        this.productosDisponibles = productosDisponibles;
        this.preciosIngredientes = preciosIngredientes;
        this.ipImpresora = ipImpresora;
    }

    /** Si la impresión falla, vuelve a mostrar el diálogo de fallo. El ticket no conserva la hora de recogida. */
    void reimprimir(Context actividad, String nombreTienda, int ticketId) {
        // El contexto de aplicación evita retener la Activity mientras la tarea espera en el hilo de disco.
        Context context = actividad.getApplicationContext();
        executor.execute(() -> {
            VentaResumen venta = buscarVenta(ticketId);
            if (venta == null) {
                toast.postValue(context.getString(R.string.reimpresion_venta_no_encontrada, ticketId));
                return;
            }
            String texto;
            try {
                texto = generarTexto(nombreTienda, ticketId, venta);
            } catch (RuntimeException e) {
                Log.e(TAG, "No se pudo regenerar el ticket " + ticketId, e);
                falloImpresion.postValue(
                        new FalloImpresion(ticketId, context.getString(R.string.reimpresion_no_regenerar)));
                return;
            }
            impresora.imprimir(ipImpresora.getValue(), ajustes.leer(), texto, (ok, error) -> {
                if (ok) {
                    toast.postValue(context.getString(R.string.reimpresion_ok, ticketId));
                } else {
                    falloImpresion.postValue(new FalloImpresion(ticketId, error));
                }
            });
        });
    }

    private VentaResumen buscarVenta(int ticketId) {
        try {
            return ventas.getById(ticketId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String generarTexto(String nombreTienda, int ticketId, VentaResumen venta) {
        List<PedidoItem> items = deserializador.deserializar(venta.itemsJson, productosDisponibles.getValue());
        return compositor.componer(items, nombreTienda, preciosIngredientes.getValue(), venta.metodoPago, ticketId)
                .texto;
    }
}
