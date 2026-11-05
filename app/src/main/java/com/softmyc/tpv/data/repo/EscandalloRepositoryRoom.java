package com.softmyc.tpv.data.repo;

import android.content.Context;

import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.EscandalloCsvImporter;
import com.softmyc.tpv.data.local.EscandalloDao;
import com.softmyc.tpv.data.local.ExtraConsumoLocal;
import com.softmyc.tpv.data.local.ExtraLocal;
import com.softmyc.tpv.data.local.IngredienteLocal;
import com.softmyc.tpv.data.local.RecetaLineaLocal;
import com.softmyc.tpv.data.local.RecetarioRoom;
import com.softmyc.tpv.domain.NombreNormalizado;
import com.softmyc.tpv.domain.escandallo.ConsumoExtra;
import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.InformeImportacion;
import com.softmyc.tpv.domain.escandallo.LineaReceta;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.repository.EscandalloRepository;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** {@link EscandalloRepository} sobre la base Room del negocio activo. */
public final class EscandalloRepositoryRoom implements EscandalloRepository {

    private final Context appContext;

    public EscandalloRepositoryRoom(Context context) {
        this.appContext = context.getApplicationContext();
    }

    private AppDatabase db() {
        return AppDatabase.getDatabase(appContext);
    }

    private EscandalloDao dao() {
        return db().escandalloDao();
    }

    @Override
    public Recetario recetario() {
        return RecetarioRoom.cargar(db());
    }

    @Override
    public List<ProductoCatalogo> productos() {
        return new ProductoRepositoryRoom(appContext).listarTodos();
    }

    @Override
    public List<String> nombresExtras() {
        List<String> nombres = new ArrayList<>();
        for (ExtraLocal e : db().extraDao().getAll()) nombres.add(e.nombre);
        return nombres;
    }

    @Override
    public long guardarIngrediente(Ingrediente ing) {
        String normalizado = NombreNormalizado.de(ing.nombre);
        return db().runInTransaction(() -> {
            IngredienteLocal mismoNombre = dao().getIngredientePorNombre(normalizado);
            if (mismoNombre != null && mismoNombre.id != ing.id) {
                throw new IllegalArgumentException("Ya existe un ingrediente llamado " + mismoNombre.nombre);
            }
            if (ing.id == 0) {
                IngredienteLocal nuevo = new IngredienteLocal();
                nuevo.nombre = ing.nombre.trim();
                nuevo.nombreNormalizado = normalizado;
                nuevo.unidad = ing.unidad.name();
                nuevo.precioCompra = ing.precioCompra;
                nuevo.stock = ing.stock;
                return dao().insertarIngrediente(nuevo);
            }
            dao().actualizarIngrediente(ing.id, ing.nombre.trim(), normalizado, ing.unidad.name(), ing.precioCompra);
            return ing.id;
        });
    }

    @Override
    public void eliminarIngrediente(long id) {
        dao().eliminarIngrediente(id);
    }

    @Override
    public void fijarStock(long id, Double stock) {
        dao().setStock(id, stock);
    }

    @Override
    public void guardarReceta(int codigo, List<LineaReceta> lineas) {
        List<RecetaLineaLocal> entidades = new ArrayList<>();
        for (LineaReceta l : lineas) {
            RecetaLineaLocal e = new RecetaLineaLocal();
            e.productoCodigo = codigo;
            e.ingredienteId = l.ingredienteId;
            e.cantidad = l.cantidad;
            e.nombreEnCarta = l.nombreEnCarta;
            entidades.add(e);
        }
        dao().reemplazarReceta(codigo, entidades);
    }

    @Override
    public void guardarConsumoExtra(String extra, List<ConsumoExtra> consumos) {
        String normalizado = NombreNormalizado.de(extra);
        List<ExtraConsumoLocal> entidades = new ArrayList<>();
        for (ConsumoExtra c : consumos) {
            ExtraConsumoLocal e = new ExtraConsumoLocal();
            e.extra = extra;
            e.extraNormalizado = normalizado;
            e.ingredienteId = c.ingredienteId;
            e.cantidad = c.cantidad;
            entidades.add(e);
        }
        dao().reemplazarExtraConsumo(normalizado, entidades);
    }

    @Override
    public void fijarGastosAnadidos(int codigo, Double gastos) {
        db().productoDao().setGastosAnadidos(codigo, gastos);
    }

    @Override
    public InformeImportacion importar(InputStream csv) throws IOException {
        return EscandalloCsvImporter.importar(db(), csv);
    }
}
