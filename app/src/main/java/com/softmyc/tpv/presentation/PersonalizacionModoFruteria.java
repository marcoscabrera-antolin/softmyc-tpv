package com.softmyc.tpv.presentation;

import android.content.Context;
import android.view.View;

import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.Producto;

import java.util.Locale;

/** MODO 2: FRUTERÍA Y GRANEL. Peso en kg, deducción de tara de envase y notas de calidad. */
final class PersonalizacionModoFruteria implements PersonalizacionModo {
    private static final double[] PESOS_RAPIDOS = {0.250, 0.500, 1.000, 1.500, 2.000, 3.000};
    private static final double PASO_KG = 0.100;

    @Override
    public void configurar(Producto producto, PersonalizacionVistas vistas, PersonalizacionEstado estado,
                           PersonalizacionAcciones acciones) {
        configurarCabecera(producto, vistas);
        PersonalizacionVistas.setTexto(vistas.txtTituloTarjeta1, R.string.peso_bruto_bascula);
        PersonalizacionVistas.setTexto(vistas.txtTituloTarjeta2, R.string.deduccion_de_tara);
        PersonalizacionVistas.setTexto(vistas.txtTituloTarjeta3, R.string.seleccion_y_notas);
        configurarPesosRapidos(vistas, estado, acciones);
        configurarTaras(vistas, estado, acciones);
        configurarNotas(vistas, acciones);
        configurarStepper(vistas, estado, acciones);
    }

    private void configurarCabecera(Producto producto, PersonalizacionVistas vistas) {
        if (vistas.txtModalTitle != null) {
            vistas.txtModalTitle.setText("🍎 #" + producto.getCodigo() + ". " + producto.getNombre());
        }
        PersonalizacionVistas.setTexto(vistas.txtDescripcionBase,
                R.string.venta_al_peso_continuo_con_bascula_y_ded);
        PersonalizacionVistas.setTexto(vistas.txtPrecioBase,
                vistas.txtPrecioBase == null ? "" : vistas.txtPrecioBase.getContext().getString(R.string.precio_base_fruteria, producto.getPrecio()));
    }

    /** Tarjeta 1: pesos predefinidos rápidos. */
    private void configurarPesosRapidos(PersonalizacionVistas vistas, PersonalizacionEstado estado,
                                        PersonalizacionAcciones acciones) {
        if (vistas.chipGroupQuitar == null) return;
        for (double p : PESOS_RAPIDOS) {
            String label = String.format(Locale.getDefault(), "%.3f kg", p);
            LightChipView chip = PersonalizacionChips.crear(acciones.contexto(), label,
                    PersonalizacionChips.Estilo.AZUL);
            if (chip == null) continue;
            chip.setTag(p);
            vistas.chipGroupQuitar.addView(chip);
            chip.setOnClickListener(v -> {
                estado.setDecimalQuantity(p);
                acciones.actualizarPrecio();
            });
        }
    }

    /** Tarjeta 2: tara del envase; solo una puede estar marcada. */
    private void configurarTaras(PersonalizacionVistas vistas, PersonalizacionEstado estado,
                                 PersonalizacionAcciones acciones) {
        if (vistas.chipGroupAnadir == null) return;
        Context ctx = acciones.contexto();
        if (ctx == null) return;
        Object[][] taras = {
            {ctx.getString(R.string.tara_sin), 0.000, true},
            {ctx.getString(R.string.tara_bolsa_papel), 0.050, false},
            {ctx.getString(R.string.tara_bandeja), 0.120, false},
            {ctx.getString(R.string.tara_caja_madera), 0.350, false}
        };
        for (Object[] t : taras) {
            String label = (String) t[0];
            double val = (double) t[1];
            boolean initialCheck = (boolean) t[2];
            LightChipView chip = PersonalizacionChips.crear(acciones.contexto(), label,
                    PersonalizacionChips.Estilo.AMBAR);
            if (chip == null) continue;
            chip.setTag(val);
            chip.setChecked(initialCheck);
            vistas.chipGroupAnadir.addView(chip);
            chip.setOnCheckedChangeListener((bv, isChecked) -> {
                if (isChecked) {
                    estado.setTareKg(val);
                    desmarcarOtros(vistas, bv);
                } else if (PersonalizacionChips.chipMarcado(vistas.chipGroupAnadir) == null) {
                    estado.setTareKg(0.0);
                }
                acciones.actualizarPrecio();
            });
        }
    }

    private static void desmarcarOtros(PersonalizacionVistas vistas, View marcado) {
        for (int i = 0; i < vistas.chipGroupAnadir.getChildCount(); i++) {
            View other = vistas.chipGroupAnadir.getChildAt(i);
            if (other != marcado && other instanceof LightChipView) ((LightChipView) other).setChecked(false);
        }
    }

    /** Tarjeta 3: notas cualitativas, sin efecto en el precio. */
    private void configurarNotas(PersonalizacionVistas vistas, PersonalizacionAcciones acciones) {
        if (vistas.chipGroupSalsas == null) return;
        Context ctx = acciones.contexto();
        if (ctx == null) return;
        String[] notas = {ctx.getString(R.string.nota_maduracion), ctx.getString(R.string.nota_cocinar_zumo), ctx.getString(R.string.nota_calibre), ctx.getString(R.string.nota_bolsa_bio)};
        for (String n : notas) {
            LightChipView chip = PersonalizacionChips.crear(acciones.contexto(), n,
                    PersonalizacionChips.Estilo.VERDE);
            if (chip == null) continue;
            chip.setTag(n);
            vistas.chipGroupSalsas.addView(chip);
        }
    }

    /** Stepper de 0.100 kg. */
    private void configurarStepper(PersonalizacionVistas vistas, PersonalizacionEstado estado,
                                   PersonalizacionAcciones acciones) {
        if (vistas.btnLess != null) vistas.btnLess.setOnClickListener(v -> {
            double actual = estado.getDecimalQuantity();
            if (actual > PASO_KG) {
                estado.setDecimalQuantity(Math.max(PASO_KG, Math.round((actual - PASO_KG) * 1000.0) / 1000.0));
                acciones.actualizarPrecio();
            }
        });
        if (vistas.btnMore != null) vistas.btnMore.setOnClickListener(v -> {
            estado.setDecimalQuantity(Math.round((estado.getDecimalQuantity() + PASO_KG) * 1000.0) / 1000.0);
            acciones.actualizarPrecio();
        });
    }
}
