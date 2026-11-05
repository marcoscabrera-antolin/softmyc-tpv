package com.softmyc.tpv.domain.usecase;

import com.softmyc.tpv.domain.NombreNormalizado;
import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.LineaReceta;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lo que le falta al escandallo para ser exacto, para enseñarlo en vez de ignorarlo en silencio: productos
 * sin receta, ingredientes de la carta que no casan con ninguna línea (quitarlos no descontaría nada) y
 * extras que no gastan ningún ingrediente.
 */
public final class DiagnosticoEscandallo {

    public final List<String> productosSinReceta;
    /** "Producto: ingrediente" de la carta sin línea de receta que case. */
    public final List<String> ingredientesSinCasar;
    public final List<String> extrasSinConsumo;
    public final List<String> ingredientesSinPrecio;

    private DiagnosticoEscandallo(List<String> productosSinReceta, List<String> ingredientesSinCasar,
                                  List<String> extrasSinConsumo, List<String> ingredientesSinPrecio) {
        this.productosSinReceta = productosSinReceta;
        this.ingredientesSinCasar = ingredientesSinCasar;
        this.extrasSinConsumo = extrasSinConsumo;
        this.ingredientesSinPrecio = ingredientesSinPrecio;
    }

    public boolean completo() {
        return productosSinReceta.isEmpty() && ingredientesSinCasar.isEmpty()
                && extrasSinConsumo.isEmpty() && ingredientesSinPrecio.isEmpty();
    }

    public static DiagnosticoEscandallo de(List<ProductoCatalogo> productos, List<String> extras, Recetario recetario) {
        List<String> sinReceta = new ArrayList<>();
        List<String> sinCasar = new ArrayList<>();
        for (ProductoCatalogo p : productos) {
            if (!recetario.tieneReceta(p.codigo)) {
                sinReceta.add(p.codigo + " " + p.nombre);
                continue;
            }
            Set<String> casados = new HashSet<>();
            for (LineaReceta l : recetario.receta(p.codigo)) {
                if (l.nombreEnCarta != null) casados.add(NombreNormalizado.de(l.nombreEnCarta));
                Ingrediente ing = recetario.ingrediente(l.ingredienteId);
                if (ing != null) casados.add(ing.nombreNormalizado());
            }
            if (p.ingredientes == null) continue;
            for (String enCarta : p.ingredientes) {
                if (enCarta == null || enCarta.trim().isEmpty()) continue;
                if (!casados.contains(NombreNormalizado.de(enCarta))) sinCasar.add(p.nombre + ": " + enCarta.trim());
            }
        }
        List<String> extrasSin = new ArrayList<>();
        if (extras != null) {
            for (String e : extras) {
                if (recetario.consumoExtra(NombreNormalizado.de(e)).isEmpty()) extrasSin.add(e);
            }
        }
        List<String> sinPrecio = new ArrayList<>();
        for (Ingrediente i : recetario.ingredientes().values()) {
            if (i.precioCompra == null) sinPrecio.add(i.nombre);
        }
        return new DiagnosticoEscandallo(sinReceta, sinCasar, extrasSin, sinPrecio);
    }
}
