package com.softmyc.tpv.data.local;

import com.softmyc.tpv.domain.NombreNormalizado;
import com.softmyc.tpv.domain.escandallo.InformeImportacion;
import com.softmyc.tpv.domain.escandallo.UnidadIngrediente;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Importa un escandallo en CSV separado por ';' (ver assets/escandallo_lanube.csv):
 * <pre>
 * ING;;Pollo;G;;;6.99          ingrediente (unidad G/ML/UD, precio por kg/L/ud)
 * REC;1;Pollo;;333.333;Pollo;  línea de receta del producto 1 (texto de la carta opcional)
 * EXT;Queso;Queso;;82;;        lo que gasta el extra "Queso"
 * GAS;1;;;1.69;;               gastos añadidos por unidad del producto 1
 * </pre>
 * Es idempotente: los ingredientes se casan por nombre (sin acentos ni mayúsculas) y conservan su stock; la
 * receta de cada producto y el consumo de cada extra que aparecen en el fichero se sustituyen enteros.
 * Todo va en una transacción: con un error de lectura no se cambia nada.
 */
public final class EscandalloCsvImporter {

    private EscandalloCsvImporter() { }

    public static InformeImportacion importar(AppDatabase db, InputStream entrada) throws IOException {
        List<String[]> filas = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = r.readLine()) != null) {
                String t = linea.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                filas.add(t.split(";", -1));
            }
        }
        return db.runInTransaction(() -> aplicar(db, filas));
    }

    private static InformeImportacion aplicar(AppDatabase db, List<String[]> filas) {
        EscandalloDao dao = db.escandalloDao();
        List<String> rechazadas = new ArrayList<>();
        int ingredientes = 0, gastos = 0;
        Map<Integer, List<RecetaLineaLocal>> recetas = new LinkedHashMap<>();
        Map<String, List<ExtraConsumoLocal>> extras = new LinkedHashMap<>();

        // Primero los ingredientes, para que las recetas puedan usar los del mismo fichero.
        for (String[] f : filas) {
            if (!"ING".equalsIgnoreCase(campo(f, 0))) continue;
            String nombre = campo(f, 2);
            if (nombre.isEmpty()) { rechazadas.add(texto(f) + " (sin nombre)"); continue; }
            Double precio = numero(campo(f, 6));
            String unidad = UnidadIngrediente.desde(campo(f, 3)).name();
            IngredienteLocal existente = dao.getIngredientePorNombre(NombreNormalizado.de(nombre));
            if (existente == null) {
                IngredienteLocal nuevo = new IngredienteLocal();
                nuevo.nombre = nombre;
                nuevo.nombreNormalizado = NombreNormalizado.de(nombre);
                nuevo.unidad = unidad;
                nuevo.precioCompra = precio;
                dao.insertarIngrediente(nuevo);
            } else {
                dao.actualizarIngrediente(existente.id, nombre, NombreNormalizado.de(nombre), unidad, precio);
            }
            ingredientes++;
        }

        for (String[] f : filas) {
            String tipo = campo(f, 0).toUpperCase(java.util.Locale.ROOT);
            switch (tipo) {
                case "ING":
                    break;
                case "REC": {
                    Integer codigo = entero(campo(f, 1));
                    IngredienteLocal ing = dao.getIngredientePorNombre(NombreNormalizado.de(campo(f, 2)));
                    Double cantidad = numero(campo(f, 4));
                    if (codigo == null || codigo <= 0) { rechazadas.add(texto(f) + " (código de producto no válido)"); break; }
                    if (ing == null) { rechazadas.add(texto(f) + " (ingrediente desconocido)"); break; }
                    if (cantidad == null || cantidad <= 0) { rechazadas.add(texto(f) + " (cantidad no válida)"); break; }
                    RecetaLineaLocal l = new RecetaLineaLocal();
                    l.productoCodigo = codigo;
                    l.ingredienteId = ing.id;
                    l.cantidad = cantidad;
                    String enCarta = campo(f, 5);
                    l.nombreEnCarta = enCarta.isEmpty() ? null : enCarta;
                    recetas.computeIfAbsent(codigo, k -> new ArrayList<>()).add(l);
                    break;
                }
                case "EXT": {
                    String extra = campo(f, 1);
                    IngredienteLocal ing = dao.getIngredientePorNombre(NombreNormalizado.de(campo(f, 2)));
                    Double cantidad = numero(campo(f, 4));
                    if (extra.isEmpty()) { rechazadas.add(texto(f) + " (sin nombre de extra)"); break; }
                    if (ing == null) { rechazadas.add(texto(f) + " (ingrediente desconocido)"); break; }
                    if (cantidad == null || cantidad <= 0) { rechazadas.add(texto(f) + " (cantidad no válida)"); break; }
                    ExtraConsumoLocal c = new ExtraConsumoLocal();
                    c.extra = extra;
                    c.extraNormalizado = NombreNormalizado.de(extra);
                    c.ingredienteId = ing.id;
                    c.cantidad = cantidad;
                    extras.computeIfAbsent(c.extraNormalizado, k -> new ArrayList<>()).add(c);
                    break;
                }
                case "GAS": {
                    Integer codigo = entero(campo(f, 1));
                    Double importe = numero(campo(f, 4));
                    if (codigo == null || importe == null || importe < 0) { rechazadas.add(texto(f) + " (valor no válido)"); break; }
                    if (db.productoDao().setGastosAnadidos(codigo, importe) == 0) {
                        rechazadas.add(texto(f) + " (producto inexistente)");
                    } else {
                        gastos++;
                    }
                    break;
                }
                default:
                    rechazadas.add(texto(f) + " (tipo desconocido)");
            }
        }
        for (Map.Entry<Integer, List<RecetaLineaLocal>> e : recetas.entrySet()) dao.reemplazarReceta(e.getKey(), e.getValue());
        for (Map.Entry<String, List<ExtraConsumoLocal>> e : extras.entrySet()) dao.reemplazarExtraConsumo(e.getKey(), e.getValue());
        return new InformeImportacion(ingredientes, recetas.size(), extras.size(), gastos, rechazadas);
    }

    private static String campo(String[] f, int i) {
        return i < f.length ? f[i].trim() : "";
    }

    private static String texto(String[] f) {
        return String.join(";", f);
    }

    /** Admite coma o punto decimal; vacío o no numérico → null. */
    static Double numero(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            double v = Double.parseDouble(s.trim().replace(',', '.'));
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer entero(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
