package com.softmyc.tpv.data.repo;

import android.content.Context;

import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.ProductoDao;
import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.local.SalsaLocal;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;
import com.softmyc.tpv.domain.repository.ProductoRepository;

import java.util.ArrayList;
import java.util.List;

/** Implementación de {@link ProductoRepository} sobre la base de datos del negocio activo. */
public final class ProductoRepositoryRoom implements ProductoRepository {

    private final Context appContext;

    public ProductoRepositoryRoom(Context appContext) {
        this.appContext = appContext;
    }

    private ProductoDao dao() {
        return AppDatabase.getDatabase(appContext).productoDao();
    }

    @Override
    public List<ProductoCatalogo> listarTodos() {
        List<ProductoCatalogo> resultado = new ArrayList<>();
        for (ProductoLocal p : dao().getAll()) {
            resultado.add(aModelo(p));
        }
        return resultado;
    }

    @Override
    public ProductoCatalogo buscarPorCodigo(int codigo) {
        ProductoLocal p = dao().getByCodigo(codigo);
        return p != null ? aModelo(p) : null;
    }

    @Override
    public void guardar(ProductoCatalogo producto) {
        dao().insert(aEntidad(producto));
    }

    @Override
    public int actualizarDatos(ProductoCatalogo producto) {
        return dao().actualizarDatosCatalogo(producto.codigo, producto.nombre, producto.precio,
                producto.categoria, producto.ingredientes);
    }

    @Override
    public void actualizarStock(int codigo, int stock) {
        dao().setStock(codigo, stock);
    }

    @Override
    public void eliminar(int codigo) {
        ProductoLocal p = dao().getByCodigo(codigo);
        if (p != null) {
            dao().delete(p);
        }
    }

    @Override
    public List<String> listarNombresSalsas() {
        List<String> nombres = new ArrayList<>();
        for (SalsaLocal s : AppDatabase.getDatabase(appContext).salsaDao().getAll()) {
            if (s.nombre != null && !s.nombre.trim().isEmpty()) {
                nombres.add(s.nombre.trim());
            }
        }
        return nombres;
    }

    private static ProductoCatalogo aModelo(ProductoLocal p) {
        return new ProductoCatalogo(p.codigo, p.nombre, p.precio, p.categoria, p.ingredientes,
                p.unidadMedida, p.stock);
    }

    private static ProductoLocal aEntidad(ProductoCatalogo p) {
        ProductoLocal entidad = new ProductoLocal(p.codigo, p.nombre, p.precio, p.categoria, p.ingredientes,
                p.unidadMedida);
        entidad.stock = p.stock;
        return entidad;
    }
}
