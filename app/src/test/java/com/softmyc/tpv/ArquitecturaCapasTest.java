package com.softmyc.tpv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reglas de dependencia entre capas. {@code domain} no depende de nada de Android ni de {@code data}/{@code presentation},
 * y {@code presentation} solo importa {@code data} en las clases de la lista: es un "trinquete" que impide añadir
 * dependencias nuevas; al limpiar una clase hay que quitarla de la lista (el test falla si sobra).
 */
public class ArquitecturaCapasTest {
    private static final String PAQUETE = "src/main/java/com/softmyc/tpv";
    /** Gradle ejecuta los tests desde el módulo app; un IDE puede hacerlo desde la raíz del repositorio. */
    private static final Path RAIZ = Files.isDirectory(Paths.get(PAQUETE)) ? Paths.get(PAQUETE)
            : Paths.get("app", PAQUETE);

    /** Clases de presentation que todavía importan data directamente (deuda conocida). */
    private static final Set<String> PRESENTATION_CON_DATA = new TreeSet<>(Arrays.asList(
            "presentation/AdminTenantSelectorDialog.java",
            "presentation/AjustesActivity.java",
            "presentation/ArqueoCocinaActivity.java",
            "presentation/BaseActivity.java",
            "presentation/EstadisticasActivity.java",
            "presentation/HistorialVentasActivity.java",
            "presentation/ListaProductosActivity.java",
            "presentation/LoginActivity.java",
            "presentation/MainActivity.java",
            "presentation/ModificarPreciosActivity.java",
            "presentation/PedidoActivity.java",
            "presentation/PedidosWebActivity.java",
            "presentation/SessionPolicy.java",
            "presentation/SplashActivity.java",
            "presentation/StockActivity.java",
            "presentation/viewmodel/LoginViewModel.java",
            "presentation/viewmodel/PedidoViewModel.java"));

    private static Set<String> ficherosQueImportan(String carpeta, String prefijoImport) throws IOException {
        try (Stream<Path> paseo = Files.walk(RAIZ.resolve(carpeta))) {
            return paseo.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> importa(p, prefijoImport))
                    .map(p -> RAIZ.relativize(p).toString().replace(File.separatorChar, '/'))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    /** Solo cuenta sentencias import reales (también las estáticas), no comentarios ni cadenas. */
    private static boolean importa(Path fichero, String prefijo) {
        Pattern import_ = Pattern.compile("^\\s*import\\s+(static\\s+)?" + Pattern.quote(prefijo), Pattern.MULTILINE);
        try {
            return import_.matcher(new String(Files.readAllBytes(fichero), StandardCharsets.UTF_8)).find();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void presentation_soloImportaDataEnLasClasesDeLaListaConocida() throws IOException {
        Set<String> reales = ficherosQueImportan("presentation", "com.softmyc.tpv.data.");

        assertEquals("presentation importa data fuera de la lista (o la lista tiene clases ya limpias)",
                PRESENTATION_CON_DATA, reales);
    }

    @Test
    public void domain_noImportaDataNiPresentation() throws IOException {
        assertTrue(ficherosQueImportan("domain", "com.softmyc.tpv.data.").isEmpty());
        assertTrue(ficherosQueImportan("domain", "com.softmyc.tpv.presentation.").isEmpty());
    }

    @Test
    public void domain_noDependeDeAndroid() throws IOException {
        Set<String> conAndroid = ficherosQueImportan("domain", "android.");
        conAndroid.addAll(ficherosQueImportan("domain", "androidx."));

        assertTrue("domain con dependencias de Android: " + conAndroid, conAndroid.isEmpty());
    }
}
