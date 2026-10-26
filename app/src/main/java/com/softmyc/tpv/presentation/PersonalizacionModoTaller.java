package com.softmyc.tpv.presentation;

import android.content.Context;
import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.IngredientOption;
import com.softmyc.tpv.domain.Producto;

import java.util.Locale;

/** MODO 3: TALLER MECÁNICO / SERVICIOS. Horas de mano de obra, suplementos y checklist técnico. */
final class PersonalizacionModoTaller implements PersonalizacionModo {
    private static final double[] HORAS_RAPIDAS = {0.5, 1.0, 1.5, 2.0, 3.0, 4.0};
    private static final double PASO_HORAS = 0.5;

    @Override
    public void configurar(Producto producto, PersonalizacionVistas vistas, PersonalizacionEstado estado,
                           PersonalizacionAcciones acciones) {
        configurarCabecera(producto, vistas);
        PersonalizacionVistas.setTexto(vistas.txtTituloTarjeta1, R.string.horas_de_trabajo);
        PersonalizacionVistas.setTexto(vistas.txtTituloTarjeta2, R.string.suplementos_y_recargos);
        PersonalizacionVistas.setTexto(vistas.txtTituloTarjeta3, R.string.control_de_calidad);
        configurarHorasRapidas(vistas, estado, acciones);
        configurarSuplementos(vistas, acciones);
        configurarChecklist(vistas, acciones);
        configurarStepper(vistas, estado, acciones);
    }

    private void configurarCabecera(Producto producto, PersonalizacionVistas vistas) {
        if (vistas.txtModalTitle != null) {
            vistas.txtModalTitle.setText("🔧 #" + producto.getCodigo() + ". " + producto.getNombre());
        }
        PersonalizacionVistas.setTexto(vistas.txtDescripcionBase,
                R.string.mano_de_obra_tecnica_y_servicios_profesi);
        PersonalizacionVistas.setTexto(vistas.txtPrecioBase,
                vistas.txtPrecioBase == null ? "" : vistas.txtPrecioBase.getContext().getString(R.string.precio_base_taller, producto.getPrecio()));
    }

    /** Tarjeta 1: horas predefinidas. */
    private void configurarHorasRapidas(PersonalizacionVistas vistas, PersonalizacionEstado estado,
                                        PersonalizacionAcciones acciones) {
        if (vistas.chipGroupQuitar == null) return;
        for (double h : HORAS_RAPIDAS) {
            String label = String.format(Locale.getDefault(), "%.1f h", h);
            LightChipView chip = PersonalizacionChips.crear(acciones.contexto(), label,
                    PersonalizacionChips.Estilo.AZUL);
            if (chip == null) continue;
            chip.setTag(h);
            vistas.chipGroupQuitar.addView(chip);
            chip.setOnClickListener(v -> {
                estado.setDecimalQuantity(h);
                acciones.actualizarPrecio();
            });
        }
    }

    /** Tarjeta 2: suplementos (urgencia, desplazamiento, nocturno) como opciones con precio. */
    private void configurarSuplementos(PersonalizacionVistas vistas, PersonalizacionAcciones acciones) {
        if (vistas.chipGroupAnadir == null) return;
        Context ctx = acciones.contexto();
        if (ctx == null) return;
        Object[][] sups = {
            {ctx.getString(R.string.suplemento_urgente), 0.0},
            {ctx.getString(R.string.suplemento_desplazamiento), 15.0},
            {ctx.getString(R.string.suplemento_nocturno), 0.0}
        };
        for (Object[] s : sups) {
            String name = (String) s[0];
            double price = (double) s[1];
            IngredientOption opt = new IngredientOption(name, price, IngredientOption.Type.ANADIR);
            LightChipView chip = PersonalizacionChips.crear(acciones.contexto(), name,
                    PersonalizacionChips.Estilo.ROJO);
            if (chip == null) continue;
            chip.setTag(opt);
            vistas.chipGroupAnadir.addView(chip);
            chip.setOnCheckedChangeListener((bv, isChecked) -> acciones.actualizarPrecio());
        }
    }

    /** Tarjeta 3: checklist técnico, sin efecto en el precio. */
    private void configurarChecklist(PersonalizacionVistas vistas, PersonalizacionAcciones acciones) {
        if (vistas.chipGroupSalsas == null) return;
        Context ctx = acciones.contexto();
        if (ctx == null) return;
        String[] checks = {ctx.getString(R.string.check_revision), ctx.getString(R.string.check_informe),
                ctx.getString(R.string.check_prueba), ctx.getString(R.string.check_reciclaje)};
        for (String c : checks) {
            LightChipView chip = PersonalizacionChips.crear(acciones.contexto(), c,
                    PersonalizacionChips.Estilo.VERDE);
            if (chip == null) continue;
            chip.setTag(c);
            vistas.chipGroupSalsas.addView(chip);
        }
    }

    /** Stepper de media hora. */
    private void configurarStepper(PersonalizacionVistas vistas, PersonalizacionEstado estado,
                                   PersonalizacionAcciones acciones) {
        if (vistas.btnLess != null) vistas.btnLess.setOnClickListener(v -> {
            if (estado.getDecimalQuantity() > PASO_HORAS) {
                estado.setDecimalQuantity(Math.max(PASO_HORAS, estado.getDecimalQuantity() - PASO_HORAS));
                acciones.actualizarPrecio();
            }
        });
        if (vistas.btnMore != null) vistas.btnMore.setOnClickListener(v -> {
            estado.setDecimalQuantity(estado.getDecimalQuantity() + PASO_HORAS);
            acciones.actualizarPrecio();
        });
    }
}
