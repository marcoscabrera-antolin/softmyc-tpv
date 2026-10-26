package com.softmyc.tpv.data.local.cifrado;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MigradorBaseCifradaTest {

    private static final String CLAVE = "cd".repeat(32);
    private static final byte[] CONTENIDO_EN_CLARO = bytesDeCabeceraSqlite();

    @Rule
    public TemporaryFolder carpeta = new TemporaryFolder();

    private OperacionesBaseFalsas operaciones;
    private MigradorBaseCifrada migrador;
    private File principal;

    @Before
    public void preparar() throws Exception {
        operaciones = new OperacionesBaseFalsas();
        operaciones.claveAceptada = CLAVE;
        migrador = new MigradorBaseCifrada(operaciones);
        principal = new File(carpeta.getRoot(), "softmyc_elbocata.db");
        Files.write(principal.toPath(), CONTENIDO_EN_CLARO);
        operaciones.instantaneas.put(principal.getAbsolutePath(), instantanea(7, 3L));
    }

    @Test
    public void migraDejandoSoloLaBaseCifradaYSinRestos() throws Exception {
        migrador.migrar(principal, CLAVE);

        assertArrayEquals(OperacionesBaseFalsas.MARCADOR_CIFRADO.getBytes(StandardCharsets.US_ASCII),
                Files.readAllBytes(principal.toPath()));
        assertFalse(MigradorBaseCifrada.rutaTemporal(principal).exists());
        assertFalse(MigradorBaseCifrada.rutaCopia(principal).exists());
        assertEquals(1, operaciones.exportaciones);
    }

    @Test
    public void noSustituyeLaOriginalSiLaCopiaCifradaNoCoincide() throws Exception {
        operaciones.exportacionesForzadas.put(
                MigradorBaseCifrada.rutaTemporal(principal).getAbsolutePath(), instantanea(7, 2L));

        try {
            migrador.migrar(principal, CLAVE);
            fail("Una copia con menos filas no debe sustituir la original");
        } catch (BaseCifradaException esperada) {
            assertArrayEquals(CONTENIDO_EN_CLARO, Files.readAllBytes(principal.toPath()));
            assertFalse(MigradorBaseCifrada.rutaTemporal(principal).exists());
            assertFalse(MigradorBaseCifrada.rutaCopia(principal).exists());
        }
    }

    @Test
    public void restauraLaOriginalSiLaBaseCifradaNoAbreTrasLaSustitucion() throws Exception {
        operaciones.fallarLecturaTrasSustitucion = true;

        try {
            migrador.migrar(principal, CLAVE);
            fail("Si la base cifrada no abre hay que restaurar la original");
        } catch (BaseCifradaException esperada) {
            assertArrayEquals(CONTENIDO_EN_CLARO, Files.readAllBytes(principal.toPath()));
            assertFalse(MigradorBaseCifrada.rutaCopia(principal).exists());
        }
    }

    @Test
    public void rechazaMigrarUnaBaseEnClaroQueNoSuperaIntegridad() throws Exception {
        operaciones.instantaneas.put(principal.getAbsolutePath(), new Instantanea(7, Collections.singletonMap("ventas", 3L), false));

        try {
            migrador.migrar(principal, CLAVE);
            fail("Una base corrupta no se migra");
        } catch (BaseCifradaException esperada) {
            assertArrayEquals(CONTENIDO_EN_CLARO, Files.readAllBytes(principal.toPath()));
            assertEquals(0, operaciones.exportaciones);
        }
    }

    @Test
    public void descartaUnTemporalDeUnIntentoAnteriorAntesDeMigrar() throws Exception {
        File temporal = MigradorBaseCifrada.rutaTemporal(principal);
        Files.write(temporal.toPath(), "restos".getBytes(StandardCharsets.US_ASCII));

        migrador.migrar(principal, CLAVE);

        assertFalse(temporal.exists());
        assertEquals(1, operaciones.exportaciones);
    }

    @Test
    public void cierraMigracionPendienteBorrandoLaCopiaCuandoLaCifradaEsIntegra() throws Exception {
        File copia = MigradorBaseCifrada.rutaCopia(principal);
        Files.write(copia.toPath(), CONTENIDO_EN_CLARO);
        principal.delete();
        File cifrada = new File(carpeta.getRoot(), "softmyc_elbocata.db");
        Files.write(cifrada.toPath(), OperacionesBaseFalsas.MARCADOR_CIFRADO.getBytes(StandardCharsets.US_ASCII));
        operaciones.instantaneas.put(cifrada.getAbsolutePath(), instantanea(7, 3L));

        migrador.cerrarMigracionPendiente(cifrada, CLAVE);

        assertFalse(copia.exists());
        assertTrue(cifrada.exists());
    }

    @Test
    public void cierraMigracionPendienteConservaLaCopiaSiLaCifradaNoEsIntegra() throws Exception {
        File copia = MigradorBaseCifrada.rutaCopia(principal);
        Files.write(copia.toPath(), CONTENIDO_EN_CLARO);
        operaciones.instantaneas.put(principal.getAbsolutePath(), new Instantanea(7, Collections.emptyMap(), false));

        try {
            migrador.cerrarMigracionPendiente(principal, CLAVE);
            fail("Con la base cifrada dañada se conserva la copia en claro");
        } catch (BaseCifradaException esperada) {
            assertTrue(copia.exists());
        }
    }

    @Test
    public void descartarRestosBorraSoloElTemporal() throws Exception {
        File temporal = MigradorBaseCifrada.rutaTemporal(principal);
        File copia = MigradorBaseCifrada.rutaCopia(principal);
        Files.write(temporal.toPath(), new byte[]{1});
        Files.write(copia.toPath(), CONTENIDO_EN_CLARO);

        migrador.descartarRestos(principal);

        assertFalse(temporal.exists());
        assertTrue(copia.exists());
    }

    private static Instantanea instantanea(int version, long ventas) {
        Map<String, Long> filas = new HashMap<>();
        filas.put("ventas", ventas);
        filas.put("productos", 10L);
        return new Instantanea(version, filas, true);
    }

    private static byte[] bytesDeCabeceraSqlite() {
        byte[] bytes = new byte[512];
        System.arraycopy(EstadoBase.CABECERA_SQLITE, 0, bytes, 0, EstadoBase.CABECERA_SQLITE.length);
        return bytes;
    }
}
