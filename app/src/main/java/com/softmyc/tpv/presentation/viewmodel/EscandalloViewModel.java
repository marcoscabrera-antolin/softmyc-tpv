package com.softmyc.tpv.presentation.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.softmyc.tpv.composicion.Dependencias;
import com.softmyc.tpv.domain.escandallo.ConsumoExtra;
import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.InformeImportacion;
import com.softmyc.tpv.domain.escandallo.LineaReceta;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.repository.EscandalloRepository;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;
import com.softmyc.tpv.domain.usecase.DiagnosticoEscandallo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Estado y acciones de las pantallas de ingredientes y recetas. El trabajo con la base va en segundo plano. */
public class EscandalloViewModel extends AndroidViewModel {

    /** Todo lo que pintan las pantallas, de una vez (inmutable). */
    public static final class Estado {
        public final Recetario recetario;
        public final List<Ingrediente> ingredientes;
        public final List<ProductoCatalogo> productos;
        public final List<String> extras;
        public final DiagnosticoEscandallo diagnostico;

        Estado(Recetario recetario, List<ProductoCatalogo> productos, List<String> extras) {
            this.recetario = recetario;
            this.ingredientes = Collections.unmodifiableList(new ArrayList<>(recetario.ingredientes().values()));
            this.productos = Collections.unmodifiableList(productos);
            this.extras = Collections.unmodifiableList(extras);
            this.diagnostico = DiagnosticoEscandallo.de(productos, extras, recetario);
        }
    }

    private final EscandalloRepository repositorio;
    private final Executor fondo;
    private final ExecutorService propio;
    private final MutableLiveData<Estado> estado = new MutableLiveData<>();
    private final MutableLiveData<String> mensaje = new MutableLiveData<>();

    public EscandalloViewModel(@NonNull Application app) {
        this(app, Dependencias.escandallo(app), null);
    }

    /** Tests: repositorio y ejecutor propios (p. ej. Runnable::run para ejecutar en el mismo hilo). */
    public EscandalloViewModel(@NonNull Application app, EscandalloRepository repositorio, Executor fondo) {
        super(app);
        this.repositorio = repositorio;
        this.propio = fondo == null ? Executors.newSingleThreadExecutor() : null;
        this.fondo = fondo != null ? fondo : propio;
    }

    public LiveData<Estado> estado() { return estado; }

    /** Mensajes de una sola vez (guardado, error…); la pantalla los consume con {@link #mensajeLeido()}. */
    public LiveData<String> mensaje() { return mensaje; }

    public void mensajeLeido() { mensaje.setValue(null); }

    public void cargar() {
        enFondo(null, () -> { });
    }

    public void guardarIngrediente(Ingrediente ingrediente, Double stockInicial, boolean cambiarStock) {
        enFondo("Ingrediente guardado", () -> {
            long id = repositorio.guardarIngrediente(ingrediente);
            if (cambiarStock) repositorio.fijarStock(id, stockInicial);
        });
    }

    public void eliminarIngrediente(long id) {
        enFondo("Ingrediente eliminado", () -> repositorio.eliminarIngrediente(id));
    }

    public void fijarStock(long id, Double stock) {
        enFondo(null, () -> repositorio.fijarStock(id, stock));
    }

    public void guardarReceta(int codigo, List<LineaReceta> lineas, Double gastosAnadidos) {
        enFondo("Receta guardada", () -> {
            repositorio.guardarReceta(codigo, lineas);
            repositorio.fijarGastosAnadidos(codigo, gastosAnadidos);
        });
    }

    public void guardarConsumoExtra(String extra, List<ConsumoExtra> consumos) {
        enFondo("Extra guardado", () -> repositorio.guardarConsumoExtra(extra, consumos));
    }

    public void importar(InputStream csv) {
        fondo.execute(() -> {
            try (InputStream entrada = csv) {
                InformeImportacion informe = repositorio.importar(entrada);
                publicar();
                String texto = "Importado: " + informe.ingredientes + " ingredientes, " + informe.productosConReceta
                        + " recetas, " + informe.extras + " extras";
                if (!informe.rechazadas.isEmpty()) {
                    texto += "\n" + informe.rechazadas.size() + " filas rechazadas, p. ej.: " + informe.rechazadas.get(0);
                }
                mensaje.postValue(texto);
            } catch (Exception e) {
                mensaje.postValue("No se pudo importar: " + e.getMessage());
            }
        });
    }

    private void enFondo(String exito, Runnable accion) {
        fondo.execute(() -> {
            try {
                accion.run();
                publicar();
                if (exito != null) mensaje.postValue(exito);
            } catch (IllegalArgumentException e) {
                mensaje.postValue(e.getMessage());
            } catch (RuntimeException e) {
                mensaje.postValue("No se pudo guardar: " + e.getMessage());
            }
        });
    }

    private void publicar() {
        estado.postValue(new Estado(repositorio.recetario(), repositorio.productos(), repositorio.nombresExtras()));
    }

    @Override
    protected void onCleared() {
        if (propio != null) propio.shutdown();
        super.onCleared();
    }
}
