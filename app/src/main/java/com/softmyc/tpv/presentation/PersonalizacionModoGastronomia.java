package com.softmyc.tpv.presentation;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.IngredientOption;
import com.softmyc.tpv.domain.Producto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** MODO 1: GASTRONOMÍA / RESTAURACIÓN. Ingredientes a quitar, extras con precio (A-Z) y salsas (máximo 3). */
final class PersonalizacionModoGastronomia implements PersonalizacionModo {
    private static final int MAX_SALSAS = 3;

    private final List<IngredientOption> availableExtras;
    private final List<IngredientOption> availableSauces;

    PersonalizacionModoGastronomia(List<IngredientOption> availableExtras, List<IngredientOption> availableSauces) {
        this.availableExtras = availableExtras;
        this.availableSauces = availableSauces;
    }

    @Override
    public void configurar(Producto producto, PersonalizacionVistas vistas, PersonalizacionEstado estado,
                           PersonalizacionAcciones acciones) {
        configurarCabecera(producto, vistas);
        PersonalizacionVistas.setTexto(vistas.txtTituloTarjeta1, R.string.quitar_ingredientes);
        PersonalizacionVistas.setTexto(vistas.txtTituloTarjeta2, R.string.anadir_extras);
        PersonalizacionVistas.setTexto(vistas.txtTituloTarjeta3, R.string.salsas_max_3);
        configurarIngredientesQuitar(producto, vistas, acciones);
        configurarExtras(vistas, acciones);
        configurarSalsas(vistas, acciones);
        configurarStepper(vistas, estado, acciones);
    }

    private void configurarCabecera(Producto producto, PersonalizacionVistas vistas) {
        String emoji = emojiPara(producto.getCategoria());
        if (vistas.txtModalTitle != null) {
            vistas.txtModalTitle.setText(emoji + " " + producto.getCodigo() + ". " + producto.getNombre());
        }
        PersonalizacionVistas.setTexto(vistas.txtDescripcionBase, formatIngredientsList(producto.getIngredientes()));
        PersonalizacionVistas.setTexto(vistas.txtPrecioBase,
                vistas.txtPrecioBase == null ? "" : vistas.txtPrecioBase.getContext().getString(R.string.precio_base_gastro, producto.getPrecio()));
    }

    private static String emojiPara(String categoria) {
        if (categoria == null) return "🍽️";
        String cat = categoria.toLowerCase();
        if (cat.contains("bocadillo")) return "🥖";
        if (cat.contains("hamburguesa") || cat.contains("burger")) return "🍔";
        if (cat.contains("complemento") || cat.contains("patata") || cat.contains("fry")) return "🍟";
        if (cat.contains("perrito") || cat.contains("dog")) return "🌭";
        if (cat.contains("pizza")) return "🍕";
        if (cat.contains("bebida") || cat.contains("drink")) return "🥤";
        if (cat.contains("postre") || cat.contains("dessert")) return "🍰";
        return "🍽️";
    }

    /** Tarjeta 1: ingredientes base que el cliente puede quitar. */
    private void configurarIngredientesQuitar(Producto producto, PersonalizacionVistas vistas,
                                              PersonalizacionAcciones acciones) {
        List<String> baseIngs = producto.getIngredientes();
        if (baseIngs == null || vistas.chipGroupQuitar == null) return;
        Context ctx = acciones.contexto();
        if (ctx == null) return;
        for (String ing : baseIngs) {
            if (ing == null) continue;
            LightChipView chip = PersonalizacionChips.crear(acciones.contexto(), ctx.getString(R.string.chip_ingrediente_incluido, ing),
                    PersonalizacionChips.Estilo.ROJO);
            if (chip == null) continue;
            chip.setTag(ing);
            vistas.chipGroupQuitar.addView(chip);
            chip.setOnCheckedChangeListener((bv, isChecked) -> {
                chip.setText(chip.getContext().getString(isChecked ? R.string.chip_ingrediente_quitar : R.string.chip_ingrediente_incluido, ing));
                chip.announceForAccessibility(chip.getContext().getString(isChecked ? R.string.anuncio_ingrediente_excluido : R.string.anuncio_ingrediente_incluido, ing));
                acciones.actualizarPrecio();
            });
        }
    }

    /** Tarjeta 2: extras ordenados A-Z, con precio. */
    private void configurarExtras(PersonalizacionVistas vistas, PersonalizacionAcciones acciones) {
        if (vistas.chipGroupAnadir == null || availableExtras == null) return;
        List<IngredientOption> sortedExtras = new ArrayList<>(availableExtras);
        try { Collections.sort(sortedExtras); } catch (Exception ignored) {}
        for (IngredientOption extra : sortedExtras) {
            if (extra == null || extra.getName() == null) continue;
            String label = extra.getName() + String.format(Locale.getDefault(), " (+%.2f€)", extra.getExtraPrice());
            LightChipView chip = PersonalizacionChips.crear(acciones.contexto(), label,
                    PersonalizacionChips.Estilo.VERDE);
            if (chip == null) continue;
            chip.setTag(extra);
            vistas.chipGroupAnadir.addView(chip);
            chip.setOnCheckedChangeListener((bv, isChecked) -> acciones.actualizarPrecio());
        }
    }

    /** Tarjeta 3: salsas, con un máximo de {@link #MAX_SALSAS} marcadas. */
    private void configurarSalsas(PersonalizacionVistas vistas, PersonalizacionAcciones acciones) {
        if (vistas.chipGroupSalsas == null || availableSauces == null) return;
        for (IngredientOption sauce : availableSauces) {
            if (sauce == null || sauce.getName() == null) continue;
            LightChipView chip = PersonalizacionChips.crear(acciones.contexto(), sauce.getName(),
                    PersonalizacionChips.Estilo.AMBAR);
            if (chip == null) continue;
            chip.setTag(sauce);
            vistas.chipGroupSalsas.addView(chip);
            chip.setOnCheckedChangeListener((bv, isChecked) -> {
                if (isChecked && PersonalizacionChips.contarMarcados(vistas.chipGroupSalsas) > MAX_SALSAS) {
                    chip.setChecked(false);
                    Context ctx = acciones.contexto();
                    if (ctx != null) Toast.makeText(ctx, ctx.getString(R.string.maximo_3_salsas_por_producto), Toast.LENGTH_SHORT).show();
                    return;
                }
                acciones.actualizarPrecio();
            });
        }
    }

    /** Stepper de unidades: de 1 en 1, sin bajar de 1. */
    private void configurarStepper(PersonalizacionVistas vistas, PersonalizacionEstado estado,
                                   PersonalizacionAcciones acciones) {
        if (vistas.btnLess != null) vistas.btnLess.setOnClickListener(v -> {
            if (estado.getQuantity() > 1) {
                estado.setQuantity(estado.getQuantity() - 1);
                estado.setDecimalQuantity(estado.getQuantity());
                acciones.actualizarPrecio();
            }
        });
        if (vistas.btnMore != null) vistas.btnMore.setOnClickListener(v -> {
            estado.setQuantity(estado.getQuantity() + 1);
            estado.setDecimalQuantity(estado.getQuantity());
            acciones.actualizarPrecio();
        });
    }

    private static String formatIngredientsList(List<String> list) {
        if (list == null || list.isEmpty()) return "Producto individual";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            String item = list.get(i);
            if (item != null && !item.trim().isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(item.trim());
            }
        }
        return sb.length() > 0 ? sb.toString() : "Producto individual";
    }
}
