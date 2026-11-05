package com.softmyc.tpv.domain.repository;

import com.softmyc.tpv.domain.escandallo.ConsumoExtra;
import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.InformeImportacion;
import com.softmyc.tpv.domain.escandallo.LineaReceta;
import com.softmyc.tpv.domain.escandallo.Recetario;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Acceso al escandallo del negocio activo (ingredientes, recetas, extras y gastos añadidos). */
public interface EscandalloRepository {

    Recetario recetario();

    List<ProductoCatalogo> productos();

    List<String> nombresExtras();

    /**
     * Crea (id 0) o edita un ingrediente. El stock no se toca aquí (ver {@link #fijarStock}).
     *
     * @return el id del ingrediente
     * @throws IllegalArgumentException si ya existe otro con el mismo nombre
     */
    long guardarIngrediente(Ingrediente ingrediente);

    /** Borra el ingrediente y sus líneas de receta y de extras. */
    void eliminarIngrediente(long id);

    /** Recuento: fija las existencias; null deja de controlar el stock de ese ingrediente. */
    void fijarStock(long id, Double stock);

    void guardarReceta(int codigo, List<LineaReceta> lineas);

    void guardarConsumoExtra(String extra, List<ConsumoExtra> consumos);

    void fijarGastosAnadidos(int codigo, Double gastos);

    InformeImportacion importar(InputStream csv) throws IOException;
}
