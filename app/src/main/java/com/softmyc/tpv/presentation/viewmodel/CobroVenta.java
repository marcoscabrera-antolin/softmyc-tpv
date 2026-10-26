package com.softmyc.tpv.presentation.viewmodel;

import com.softmyc.tpv.R;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.usecase.ImprimirYGuardarVentaUseCase;
import com.softmyc.tpv.util.FalloImpresion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Cobro del pedido en curso: imprime y guarda la venta a través de {@link ImprimirYGuardarVentaUseCase}, con un
 * guard contra el doble toque. Los avisos y efectos sobre el ViewModel llegan como callbacks.
 */
final class CobroVenta {
    private final Function<Context, ImprimirYGuardarVentaUseCase> fabrica;
    private final ExecutorService disco;
    private final Consumer<String> toast;
    private final Textos textos;
    private final Consumer<FalloImpresion> fallo;
    private final Runnable alGuardarEnDisco;
    private final Runnable sincronizarVentas;
    private final Runnable alGuardarEnPrincipal;

    private final AtomicBoolean ventaEnCurso = new AtomicBoolean(false);
    // Estado de la venta en curso: si ya se guardó (un fallo posterior es de impresión) y su número de ticket.
    private volatile boolean ventaGuardada = false;
    private volatile int ticketGuardado = FalloImpresion.TICKET_DESCONOCIDO;

    /**
     * @param fabrica crea el caso de uso de impresión y guardado para un contexto
     * @param toast avisos al usuario (puede llamarse desde cualquier hilo)
     * @param fallo fallo de impresión de una venta ya guardada
     * @param alGuardarEnDisco se ejecuta en el hilo de disco al guardar la venta (p. ej. limpiar el id recuperado)
     * @param sincronizarVentas sube las ventas pendientes tras guardar
     * @param alGuardarEnPrincipal limpia el pedido y avisa de la venta guardada, en el hilo principal
     */
    CobroVenta(Function<Context, ImprimirYGuardarVentaUseCase> fabrica, ExecutorService disco, Textos textos,
               Consumer<String> toast, Consumer<FalloImpresion> fallo, Runnable alGuardarEnDisco,
               Runnable sincronizarVentas, Runnable alGuardarEnPrincipal) {
        this.fabrica = fabrica;
        this.disco = disco;
        this.toast = toast;
        this.textos = textos;
        this.fallo = fallo;
        this.alGuardarEnDisco = alGuardarEnDisco;
        this.sincronizarVentas = sincronizarVentas;
        this.alGuardarEnPrincipal = alGuardarEnPrincipal;
    }

    void cobrar(Context context, String nombreTienda, String metodoPago, Integer targetId,
                List<PedidoItem> pedidoActual, String hora, String ipImpresora, Map<String, Double> precios) {
        // Evita que un doble toque duplique venta, ticket y número de pedido mientras hay una en curso.
        if (!ventaEnCurso.compareAndSet(false, true)) {
            return;
        }
        List<PedidoItem> snapshot = pedidoActual != null ? new ArrayList<>(pedidoActual) : null;
        ventaGuardada = false;
        ticketGuardado = FalloImpresion.TICKET_DESCONOCIDO;
        try {
            fabrica.apply(context).ejecutar(snapshot, nombreTienda, metodoPago, hora, ipImpresora, precios,
                    targetId, disco, callback());
        } catch (RuntimeException e) {
            ventaEnCurso.set(false);
            throw e;
        }
    }

    private ImprimirYGuardarVentaUseCase.Callback callback() {
        return new ImprimirYGuardarVentaUseCase.Callback() {
            @Override public void onImprimiendo() {
                toast.accept(textos.cadena(R.string.cobro_imprimiendo));
            }

            @Override public void onGuardado(long idVenta) {
                // Se ejecuta en el hilo de disco; el caso de uso entrega el id de la venta que acaba de guardar.
                ticketGuardado = (int) idVenta;
                ventaGuardada = true;
                try {
                    alGuardarEnDisco.run();
                    disco.execute(sincronizarVentas);
                    // Limpiar pedido activo y notificar en el hilo principal
                    new Handler(Looper.getMainLooper()).post(alGuardarEnPrincipal);
                } finally {
                    ventaEnCurso.set(false);
                }
            }

            @Override public void onImpreso() {
                toast.accept(textos.cadena(R.string.cobro_ticket_enviado));
            }

            @Override public void onError(String mensaje) {
                ventaEnCurso.set(false);
                if (ventaGuardada) {
                    // La venta ya existe: el fallo es de impresión y se ofrece reintentar, no volver a vender.
                    fallo.accept(new FalloImpresion(ticketGuardado, mensaje));
                } else {
                    toast.accept(mensaje);
                }
            }
        };
    }
}
