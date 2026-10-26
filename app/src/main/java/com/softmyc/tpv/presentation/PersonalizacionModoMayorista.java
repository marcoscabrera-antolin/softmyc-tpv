package com.softmyc.tpv.presentation;

import android.content.Context;
import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.IngredientOption;
import com.softmyc.tpv.domain.Producto;

import java.util.Locale;

/** MODO 4: MAYORISTA Y DISTRIBUCIÓN. Escalones por volumen, embalaje y observaciones de albarán. */
final class PersonalizacionModoMayorista implements PersonalizacionModo {
    private static final int PASO_UNIDADES = 5;

    @Override
    public void configurar(Producto producto, PersonalizacionVistas vistas, PersonalizacionEstado estado,
                           PersonalizacionAcciones acciones) {
        configurarCabecera(producto, vistas);
        PersonalizacionVistas.setTexto(vistas.txtTituloTarjeta1, R.string.escalones_por_volumen);
        PersonalizacionVistas.setTexto(vistas.txtTituloTarjeta2, R.string.paletizacion_y_embalaje);
        PersonalizacionVistas.setTexto(vistas.txtTituloTarjeta3, R.string.observaciones_de_albaran);
        configurarTramos(vistas, estado, acciones);
        configurarEmbalaje(vistas, acciones);
        configurarNotas(vistas, acciones);
        configurarStepper(vistas, estado, acciones);
    }

    private void configurarCabecera(Producto producto, PersonalizacionVistas vistas) {
        if (vistas.txtModalTitle != null) {
            vistas.txtModalTitle.setText("📦 #" + producto.getCodigo() + ". " + producto.getNombre());
        }
        PersonalizacionVistas.setTexto(vistas.txtDescripcionBase,
                R.string.venta_mayorista_a_profesionales_con_rapp);
        PersonalizacionVistas.setTexto(vistas.txtPrecioBase,
                vistas.txtPrecioBase == null ? "" : vistas.txtPrecioBase.getContext().getString(R.string.precio_base_mayorista, producto.getPrecio()));
    }

    /** Tarjeta 1: tramos de volumen; cada uno fija la cantidad a un valor representativo del tramo. */
    private void configurarTramos(PersonalizacionVistas vistas, PersonalizacionEstado estado,
                                  PersonalizacionAcciones acciones) {
        if (vistas.chipGroupQuitar == null) return;
        Context ctx = acciones.contexto();
        if (ctx == null) return;
        String[] tramos = {
            ctx.getString(R.string.tramo_volumen_1),
            ctx.getString(R.string.tramo_volumen_2),
            ctx.getString(R.string.tramo_volumen_3)
        };
        for (int i = 0; i < tramos.length; i++) {
            final int targetQty = (i == 0) ? 5 : (i == 1 ? 25 : 60);
            LightChipView chip = PersonalizacionChips.crear(acciones.contexto(), tramos[i],
                    PersonalizacionChips.Estilo.AZUL);
            if (chip == null) continue;
            chip.setTag(targetQty);
            vistas.chipGroupQuitar.addView(chip);
            chip.setOnClickListener(v -> {
                estado.setQuantity(targetQty);
                estado.setDecimalQuantity(targetQty);
                acciones.actualizarPrecio();
            });
        }
    }

    /** Tarjeta 2: embalaje y seguro como opciones con precio. */
    private void configurarEmbalaje(PersonalizacionVistas vistas, PersonalizacionAcciones acciones) {
        if (vistas.chipGroupAnadir == null) return;
        Context ctx = acciones.contexto();
        if (ctx == null) return;
        Object[][] embalajes = {
            {ctx.getString(R.string.embalaje_palet), 0.0},
            {ctx.getString(R.string.embalaje_isotermico), 5.0},
            {ctx.getString(R.string.embalaje_seguro), 10.0}
        };
        for (Object[] e : embalajes) {
            String name = (String) e[0];
            double price = (double) e[1];
            IngredientOption opt = new IngredientOption(name, price, IngredientOption.Type.ANADIR);
            LightChipView chip = PersonalizacionChips.crear(acciones.contexto(), name,
                    PersonalizacionChips.Estilo.VERDE);
            if (chip == null) continue;
            chip.setTag(opt);
            vistas.chipGroupAnadir.addView(chip);
            chip.setOnCheckedChangeListener((bv, isChecked) -> acciones.actualizarPrecio());
        }
    }

    /** Tarjeta 3: instrucciones de albarán, sin efecto en el precio. */
    private void configurarNotas(PersonalizacionVistas vistas, PersonalizacionAcciones acciones) {
        if (vistas.chipGroupSalsas == null) return;
        Context ctx = acciones.contexto();
        if (ctx == null) return;
        String[] notas = {ctx.getString(R.string.nota_muelle), ctx.getString(R.string.nota_albaran_valorado),
                ctx.getString(R.string.nota_copia_compras)};
        for (String n : notas) {
            LightChipView chip = PersonalizacionChips.crear(acciones.contexto(), n,
                    PersonalizacionChips.Estilo.AMBAR);
            if (chip == null) continue;
            chip.setTag(n);
            vistas.chipGroupSalsas.addView(chip);
        }
    }

    /** Stepper de 5 en 5 unidades, sin bajar de 5. */
    private void configurarStepper(PersonalizacionVistas vistas, PersonalizacionEstado estado,
                                   PersonalizacionAcciones acciones) {
        if (vistas.btnLess != null) vistas.btnLess.setOnClickListener(v -> {
            if (estado.getQuantity() > PASO_UNIDADES) {
                estado.setQuantity(estado.getQuantity() - PASO_UNIDADES);
                estado.setDecimalQuantity(estado.getQuantity());
                acciones.actualizarPrecio();
            }
        });
        if (vistas.btnMore != null) vistas.btnMore.setOnClickListener(v -> {
            estado.setQuantity(estado.getQuantity() + PASO_UNIDADES);
            estado.setDecimalQuantity(estado.getQuantity());
            acciones.actualizarPrecio();
        });
    }
}
