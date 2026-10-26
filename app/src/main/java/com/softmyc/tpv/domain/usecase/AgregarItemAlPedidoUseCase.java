package com.softmyc.tpv.domain.usecase;

import com.softmyc.tpv.domain.PedidoItem;
import com.softmyc.tpv.domain.Producto;

import java.util.ArrayList;
import java.util.List;

/**
 * Caso de uso: agrega un item temporal al pedido o inicia la edición de uno existente.
 *
 * Encapsula las reglas de negocio del carrito:
 *  - No se puede agregar un item sin producto seleccionado.
 *  - Al editar, el item original se extrae de la lista antes de modificarlo.
 */
public class AgregarItemAlPedidoUseCase {

    public static class Resultado {
        public final List<PedidoItem> pedidoActualizado;
        public final String error; // null si fue OK

        private Resultado(List<PedidoItem> pedido, String error) {
            this.pedidoActualizado = pedido;
            this.error = error;
        }

        public static Resultado ok(List<PedidoItem> pedido) {
            return new Resultado(pedido, null);
        }

        public static Resultado error(String msg) {
            return new Resultado(null, msg);
        }

        public boolean isOk() {
            return error == null;
        }
    }

    /**
     * Agrega el item temporal al pedido existente.
     *
     * @param pedidoActual  lista actual del pedido (puede estar vacía, no null).
     * @param itemTemporal  item que se quiere añadir.
     * @return Resultado con la nueva lista o un mensaje de error.
     */
    public Resultado agregar(List<PedidoItem> pedidoActual, PedidoItem itemTemporal) {
        if (itemTemporal == null) {
            return Resultado.error("Selecciona un producto primero");
        }
        List<PedidoItem> nueva = new ArrayList<>(pedidoActual != null ? pedidoActual : new ArrayList<>());
        nueva.add(itemTemporal);
        return Resultado.ok(nueva);
    }

    /**
     * Extrae el item en 'position' de la lista para iniciar su edición.
     * Devuelve la lista sin ese item y el item listo para editar.
     *
     * @param pedidoActual  lista actual del pedido.
     * @param position      posición del item a editar.
     * @return Resultado con la lista sin el item, o error si la posición es inválida.
     */
    public Resultado iniciarEdicion(List<PedidoItem> pedidoActual, int position) {
        if (pedidoActual == null || position < 0 || position >= pedidoActual.size()) {
            return Resultado.error("Posición de edición inválida");
        }
        List<PedidoItem> nueva = new ArrayList<>(pedidoActual);
        nueva.remove(position);
        return Resultado.ok(nueva);
    }

    /**
     * Modifica la cantidad de un item en la lista.
     * Si la cantidad resultante es <= 0 el item NO se elimina (el ViewModel decide).
     *
     * @param pedidoActual lista actual.
     * @param position     posición del item.
     * @param delta        cantidad a sumar (positivo) o restar (negativo).
     * @return Resultado con la lista actualizada, o error si la posición no es válida.
     */
    public Resultado modificarCantidad(List<PedidoItem> pedidoActual, int position, int delta) {
        if (pedidoActual == null || position < 0 || position >= pedidoActual.size()) {
            return Resultado.error("Posición inválida");
        }
        List<PedidoItem> nueva = new ArrayList<>(pedidoActual);
        PedidoItem item = nueva.get(position);
        int nuevaCant = item.getCantidad() + delta;
        if (nuevaCant > 0) {
            item.setCantidad(nuevaCant);
            return Resultado.ok(nueva);
        }
        return Resultado.error("La cantidad no puede ser menor que 1");
    }

    /**
     * Elimina el item en 'position' de la lista.
     */
    public Resultado eliminar(List<PedidoItem> pedidoActual, int position) {
        if (pedidoActual == null || position < 0 || position >= pedidoActual.size()) {
            return Resultado.error("Posición inválida");
        }
        List<PedidoItem> nueva = new ArrayList<>(pedidoActual);
        nueva.remove(position);
        return Resultado.ok(nueva);
    }
}
