package com.softmyc.tpv.presentation.viewmodel;

import com.softmyc.tpv.domain.Category;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.repository.CatalogoTenantRepository;
import com.softmyc.tpv.domain.repository.ExtraCatalogo;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;
import com.softmyc.tpv.domain.repository.ProductoRepository;
import com.softmyc.tpv.util.CategoryUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lee el catálogo local del negocio activo (productos, stock, categorías, extras y salsas). Si no hay productos
 * siembra el catálogo inicial. No publica nada: devuelve un {@link Cargado} que el ViewModel aplica.
 */
final class CatalogoLocal {
    private static final String UNIDAD_POR_DEFECTO = "UD";
    private static final String CATEGORIA_TODAS = "Todos";

    private final ProductoRepository productos;
    private final CatalogoTenantRepository catalogo;
    private final Supplier<String> tenantActivo;

    /** Resultado inmutable de una lectura del catálogo local. */
    static final class Cargado {
        private final List<Producto> productos;
        private final Map<Integer, Integer> stockPorCodigo;
        private final List<Category> categorias;
        private final List<String> nombresExtras;
        private final Map<String, Double> preciosExtras;
        private final List<String> salsas;

        Cargado(List<Producto> productos, Map<Integer, Integer> stockPorCodigo, List<Category> categorias,
                List<String> nombresExtras, Map<String, Double> preciosExtras, List<String> salsas) {
            this.productos = productos;
            this.stockPorCodigo = stockPorCodigo;
            this.categorias = categorias;
            this.nombresExtras = nombresExtras;
            this.preciosExtras = preciosExtras;
            this.salsas = salsas;
        }

        List<Producto> productos() { return productos; }
        Map<Integer, Integer> stockPorCodigo() { return stockPorCodigo; }
        List<Category> categorias() { return categorias; }
        List<String> nombresExtras() { return nombresExtras; }
        Map<String, Double> preciosExtras() { return preciosExtras; }
        List<String> salsas() { return salsas; }
    }

    /**
     * @param tenantActivo negocio activo, usado solo para sembrar el catálogo cuando la base local está vacía
     */
    CatalogoLocal(ProductoRepository productos, CatalogoTenantRepository catalogo, Supplier<String> tenantActivo) {
        this.productos = productos;
        this.catalogo = catalogo;
        this.tenantActivo = tenantActivo;
    }

    /** Lanza la excepción de Room o del repositorio tal cual; el llamador decide el aviso al usuario. */
    Cargado cargar() {
        String tenant = tenantActivo.get();
        List<ProductoCatalogo> filas = productos.listarTodos();
        if (filas.isEmpty()) {
            catalogo.sembrar(tenant);
            filas = productos.listarTodos();
        }
        List<Producto> prods = aProductos(filas);
        Map<Integer, Integer> stocks = stocksPorCodigo(filas);

        List<ExtraCatalogo> extras = catalogo.listarExtras();
        List<String> nombresExtras = new ArrayList<>();
        Map<String, Double> preciosExtras = new HashMap<>();
        for (ExtraCatalogo extra : extras) {
            nombresExtras.add(extra.nombre);
            preciosExtras.put(extra.nombre, extra.precio);
        }
        return new Cargado(prods, stocks, categoriasDe(prods), nombresExtras, preciosExtras, catalogo.listarSalsas());
    }

    private static List<Producto> aProductos(List<ProductoCatalogo> filas) {
        List<Producto> prods = new ArrayList<>();
        for (ProductoCatalogo lp : filas) {
            String catNorm = CategoryUtils.capitalizeCategory(lp.categoria);
            String uMed = (lp.unidadMedida != null && !lp.unidadMedida.trim().isEmpty())
                    ? lp.unidadMedida.trim() : UNIDAD_POR_DEFECTO;
            prods.add(new Producto(lp.codigo, lp.nombre, lp.precio, catNorm, lp.ingredientes, uMed));
        }
        Collections.sort(prods, (a, b) -> Integer.compare(a.getCodigo(), b.getCodigo()));
        return prods;
    }

    private static Map<Integer, Integer> stocksPorCodigo(List<ProductoCatalogo> filas) {
        Map<Integer, Integer> stocks = new HashMap<>();
        for (ProductoCatalogo lp : filas) {
            stocks.put(lp.codigo, lp.stock);
        }
        return stocks;
    }

    /** "Todos" seguido de una categoría por cada nombre distinto de la carta (sin distinguir mayúsculas). */
    private static List<Category> categoriasDe(List<Producto> prods) {
        List<Category> cats = new ArrayList<>();
        cats.add(new Category(CATEGORIA_TODAS, CATEGORIA_TODAS, 0, true));
        Map<String, String> vistas = new LinkedHashMap<>();
        for (Producto p : prods) {
            if (p.getCategoria() == null || p.getCategoria().trim().isEmpty()) continue;
            String catNombre = CategoryUtils.capitalizeCategory(p.getCategoria());
            String catKey = catNombre.toLowerCase().trim();
            if (!vistas.containsKey(catKey)) {
                vistas.put(catKey, catNombre);
                cats.add(new Category(catNombre, catNombre, 0, false));
            }
        }
        return cats;
    }
}
