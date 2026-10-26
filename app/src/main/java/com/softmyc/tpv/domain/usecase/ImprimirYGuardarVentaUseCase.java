package com.softmyc.tpv.domain.usecase;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.port.AjustesImpresora;
import com.softmyc.tpv.domain.port.ItemsSerializer;
import com.softmyc.tpv.domain.port.PrinterSettings;
import com.softmyc.tpv.domain.port.TicketComposer;
import com.softmyc.tpv.domain.port.TicketPrinter;
import com.softmyc.tpv.domain.port.VentaCobro;
import com.softmyc.tpv.domain.port.VentaStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.LongSupplier;

/**
 * Caso de uso: guardar la venta y, después, imprimir su ticket.
 *
 * Depende solo de puertos ({@code domain/port}); las implementaciones Android viven en {@code data/repo}.
 * Así el cobro se prueba en la JVM con dobles de prueba.
 */
public class ImprimirYGuardarVentaUseCase {

    public interface Callback {
        void onImprimiendo();
        default void onGuardado() { }
        /** La venta quedó guardada con este id (el número del ticket). Por defecto avisa con {@link #onGuardado()}. */
        default void onGuardado(long idVenta) { onGuardado(); }
        /** El ticket se envió correctamente a la impresora (tras guardar la venta). */
        default void onImpreso() { }
        void onError(String mensaje);
    }

    private final VentaStore ventaStore;
    private final TicketComposer ticketComposer;
    private final ItemsSerializer itemsSerializer;
    private final TicketPrinter ticketPrinter;
    private final PrinterSettings printerSettings;
    private final LongSupplier reloj;

    public ImprimirYGuardarVentaUseCase(VentaStore ventaStore, TicketComposer ticketComposer,
                                        ItemsSerializer itemsSerializer, TicketPrinter ticketPrinter,
                                        PrinterSettings printerSettings, LongSupplier reloj) {
        this.ventaStore = ventaStore;
        this.ticketComposer = ticketComposer;
        this.itemsSerializer = itemsSerializer;
        this.ticketPrinter = ticketPrinter;
        this.printerSettings = printerSettings;
        this.reloj = reloj;
    }

    /**
     * Ejecuta el guardado y la impresión de la venta.
     *
     * @param lista          items del pedido.
     * @param nombreTienda   nombre del restaurante para el encabezado del ticket.
     * @param metodoPago     "Efectivo" o "Tarjeta".
     * @param horaRecogida   hora de recogida, puede ser vacío.
     * @param ipImpresora    IP de la impresora térmica.
     * @param preciosExtras  mapa nombre→precio de extras para calcular el total.
     * @param targetVentaId  ID de venta a sobreescribir (null = nueva venta).
     * @param executor       ExecutorService para tareas de disco.
     * @param callback       notificaciones de progreso/error.
     */
    public void ejecutar(
            List<PedidoItem> lista,
            String nombreTienda,
            String metodoPago,
            String horaRecogida,
            String ipImpresora,
            Map<String, Double> preciosExtras,
            Integer targetVentaId,
            ExecutorService executor,
            Callback callback
    ) {
        if (lista == null || lista.isEmpty()) {
            callback.onError("Pedido vacío");
            return;
        }

        final AjustesImpresora ajustes = printerSettings.leer();

        // Primero se guarda la venta y solo después se imprime: un fallo de impresión
        // no puede dejar un ticket sin venta, ni una venta sin aviso.
        executor.execute(() -> {
            final long idVenta;
            try {
                // Total con el mismo motor que el ticket; el número definitivo del ticket es el id de la venta,
                // que solo se conoce al guardarla (así «Recuperar pedido» encuentra la venta que dice el ticket).
                double totalFinal = ticketComposer.componer(lista, nombreTienda, preciosExtras, metodoPago, 0).total;
                String itemsJson = itemsSerializer.serializar(lista, preciosExtras);
                idVenta = ventaStore.guardar(new VentaCobro(
                        targetVentaId, totalFinal, itemsJson, metodoPago, lista, reloj.getAsLong()));
            } catch (Exception e) {
                callback.onError("Error al guardar venta: " + e.getMessage());
                return;
            }
            callback.onGuardado(idVenta);

            // La venta ya está guardada: un fallo al montar el ticket no debe presentarse como fallo de guardado
            // (el usuario reintentaría y duplicaría la venta).
            final String ticketTexto;
            try {
                ticketTexto = conRecogida(ticketComposer.componer(
                        lista, nombreTienda, preciosExtras, metodoPago, (int) idVenta).texto,
                        horaRecogida, ajustes.columnas());
            } catch (Exception e) {
                callback.onError("⚠️ Venta guardada (ticket n.º " + idVenta + "), pero no se pudo generar el ticket.");
                return;
            }

            callback.onImprimiendo();
            ticketPrinter.imprimir(ipImpresora, ajustes, ticketTexto, (ok, error) -> {
                if (ok) {
                    callback.onImpreso();
                } else {
                    callback.onError("⚠️ Venta guardada, pero falló la impresión del ticket"
                            + (error != null ? " (" + error + ")" : "") + ". Revisa la impresora.");
                }
            });
        });
    }

    /** Añade la línea de hora de recogida al final del ticket, si existe. */
    private static String conRecogida(String ticket, String horaRecogida, int cols) {
        if (horaRecogida == null || horaRecogida.isEmpty()) return ticket;
        String horaLine = "RECOGIDA: " + horaRecogida;
        int pad = Math.max(0, (cols - horaLine.length()) / 2);
        String divider = new String(new char[cols]).replace("\0", "-");
        String centrado = new String(new char[pad]).replace("\0", " ") + horaLine;
        return ticket + "\n" + divider + "\n" + centrado + "\n\n\n";
    }
}
