package com.softmyc.tpv.domain;

import java.util.ArrayList;
import java.util.List;


public class PedidoItem {
    private int id;
    private String nombre;
    private int cantidad;
    private double cantidadMedida = 1.0;
    private String unidadMedida = "UD";
    private double taraKg = 0.0;
    private List<String> ingredientesQuitados = new ArrayList<>();
    private List<String> ingredientesExtra = new ArrayList<>();
    private List<String> salsasExtra = new ArrayList<>();
    private Producto producto;
    private double precioExtra = 0.0;

    /**
     * Crea un nuevo elemento de pedido basado en un producto y una cantidad inicial.
     * Inicializa las listas de ingredientes modificados y asocia el producto original.
     *
     * @param producto Producto base del que se genera el pedido.
     * @param cantidad Cantidad inicial seleccionada para este producto.
     */
    public PedidoItem(Producto producto, int cantidad) {
        this.producto = producto;
        this.id = producto.getCodigo();
        this.nombre = producto.getNombre();
        this.cantidad = cantidad;
        this.cantidadMedida = cantidad;
        this.unidadMedida = producto.getUnidadMedida();

        // Inicializar con copia de ingredientes base
        this.ingredientesQuitados = new ArrayList<>();
        this.ingredientesExtra = new ArrayList<>();
        this.salsasExtra = new ArrayList<>();

        // Copia los ingredientes base del producto
    }
    /**
     * Crea una copia completa de otro PedidoItem, incluyendo ingredientes,
     * salsas, cantidad y una copia profunda del producto asociado.
     *
     * @param original Elemento del pedido que se desea duplicar.
     */
    public PedidoItem(PedidoItem original) {
        this.id = original.id;
        this.nombre = original.nombre;
        this.cantidad = original.cantidad;
        this.cantidadMedida = original.cantidadMedida;
        this.unidadMedida = original.unidadMedida;
        this.taraKg = original.taraKg;

        // --- CORRECCIÓN AQUÍ ---
        // En lugar de 'new Producto(original.producto)', creamos uno nuevo con sus datos
        this.producto = new Producto(
                original.producto.getCodigo(),
                original.producto.getNombre(),
                original.producto.getPrecio(),
                original.producto.getCategoria(), // Importante: pasamos la categoría
                // Copiamos la lista de ingredientes para que no sea la misma referencia
                original.producto.getIngredientes() != null ? new ArrayList<>(original.producto.getIngredientes()) : new ArrayList<>(),
                original.producto.getUnidadMedida()
        );
        // -----------------------

        this.ingredientesExtra = new ArrayList<>(original.ingredientesExtra);
        this.ingredientesQuitados = new ArrayList<>(original.ingredientesQuitados);
        this.salsasExtra = new ArrayList<>(original.salsasExtra);
        this.precioExtra = original.precioExtra; // Importante copiar también el precio extra acumulado
    }
    /**
     * Obtiene el identificador del producto asociado a este pedido.
     *
     * @return ID del producto.
     */

    public int getId() {
        return id;
    }

    /**
     * Devuelve el nombre del producto asociado a este elemento del pedido.
     *
     * @return Nombre del producto.
     */
    public String getNombre() {
        return nombre;
    }

    /**
     * Obtiene la cantidad actual seleccionada para este producto.
     *
     * @return Cantidad del producto en el pedido.
     */
    public int getCantidad() {
        return cantidad;
    }

    /**
     * Incrementa la cantidad del producto en el pedido.
     *
     * @param n Número de unidades a añadir.
     */
    public void aumentarCantidad(int n) {
        this.cantidad += n;
    }

    /**
     * Reduce la cantidad del producto en el pedido.
     *
     * @param n Número de unidades a restar.
     */
    public void reducirCantidad(int n) {
        this.cantidad -= n;
    }


    /**
     * Indica si la cantidad del producto ha llegado a cero o menos.
     *
     * @return true si no quedan unidades; false en caso contrario.
     */
    public boolean sinUnidades() {
        return cantidad <= 0;
    }

    /**
     * Devuelve la lista de ingredientes que el cliente ha decidido quitar.
     *
     * @return Lista de ingredientes eliminados.
     */
    public List<String> getIngredientesQuitados() {
        return ingredientesQuitados;
    }


    /**
     * Obtiene la lista de ingredientes añadidos como extra.
     *
     * @return Lista de ingredientes adicionales.
     */
    public List<String> getIngredientesExtra() {
        return ingredientesExtra;
    }


    /**
     * Devuelve el producto original asociado a este elemento del pedido.
     *
     * @return Objeto Producto correspondiente.
     */
    public Producto getProducto() {
        return producto;
    }


    /**
     * Obtiene el precio acumulado de los ingredientes extra añadidos.
     *
     * @return Precio adicional total.
     */
    public double getPrecioExtra() {
        return precioExtra;
    }


    /**
     * Establece el precio total correspondiente a los ingredientes extra.
     *
     * @param precioExtra Importe adicional a asignar.
     */
    public void setPrecioExtra(double precioExtra) {
        this.precioExtra = precioExtra;
    }


    /**
     * Genera una descripción en texto de las modificaciones del producto,
     * incluyendo tara, ingredientes quitados y añadidos.
     *
     * @return Cadena con los detalles visuales del pedido.
     */
    public String getDetallesVisuales() {
        StringBuilder detalles = new StringBuilder();
        if (taraKg > 0.0) {
            detalles.append(String.format(java.util.Locale.getDefault(), "Tara envase: -%.3f kg\n", taraKg));
        }
        if (!ingredientesQuitados.isEmpty()) {
            detalles.append("Sin: ").append(String.join(", ", ingredientesQuitados)).append("\n");
        }
        if (!ingredientesExtra.isEmpty()) {
            detalles.append("Añadidos: ").append(String.join(", ", ingredientesExtra)).append("\n");
        }
        if (!salsasExtra.isEmpty()) {
            detalles.append("Salsas: ").append(String.join(", ", salsasExtra));
        }
        return detalles.toString().trim();
    }

    /**
     * Actualiza la cantidad del producto en el pedido.
     *
     * @param cantidadActual Nueva cantidad a establecer.
     */
    public void setCantidad(int cantidadActual) {
        this.cantidad = cantidadActual;
        this.cantidadMedida = cantidadActual;
    }

    public double getCantidadMedida() {
        return cantidadMedida > 0 ? cantidadMedida : cantidad;
    }

    public void setCantidadMedida(double cantidadMedida) {
        this.cantidadMedida = cantidadMedida;
        this.cantidad = (int) Math.ceil(cantidadMedida);
    }

    public String getUnidadMedida() {
        return (unidadMedida != null && !unidadMedida.isEmpty()) ? unidadMedida : "UD";
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
    }

    public double getTaraKg() {
        return taraKg;
    }

    public void setTaraKg(double taraKg) {
        this.taraKg = taraKg;
    }

    /**
     * Devuelve la lista de salsas añadidas al producto.
     *
     * @return Lista de salsas seleccionadas.
     */
    public List<String> getSalsas() {
        return salsasExtra;
    }
}
