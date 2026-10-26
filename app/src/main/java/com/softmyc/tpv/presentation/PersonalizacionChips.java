package com.softmyc.tpv.presentation;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import com.google.android.material.chip.ChipGroup;

/** Creación y consulta de los chips de personalización con los estilos de color de la aplicación. */
final class PersonalizacionChips {

    /** Paleta de chips: colores de fondo, trazo y texto cuando el chip está activo. */
    enum Estilo {
        ROJO("#7F1D1D", "#EF4444", "#FEE2E2"),
        AZUL("#1E3A8A", "#3B82F6", "#DBEAFE"),
        VERDE("#064E3B", "#10B981", "#D1FAE5"),
        AMBAR("#78350F", "#F59E0B", "#FEF3C7");

        final int fondoActivo;
        final int trazoActivo;
        final int textoActivo;

        Estilo(String fondo, String trazo, String texto) {
            this.fondoActivo = Color.parseColor(fondo);
            this.trazoActivo = Color.parseColor(trazo);
            this.textoActivo = Color.parseColor(texto);
        }
    }

    private PersonalizacionChips() {
    }

    /** Null si no hay contexto (el diálogo ya no está adjunto). */
    static LightChipView crear(Context ctx, String texto, Estilo estilo) {
        if (ctx == null) return null;
        return new LightChipView(ctx, texto, estilo.fondoActivo, estilo.trazoActivo, estilo.textoActivo);
    }

    /** Primer chip marcado del grupo, o null si no hay ninguno. */
    static View chipMarcado(ChipGroup grupo) {
        if (grupo == null) return null;
        for (int i = 0; i < grupo.getChildCount(); i++) {
            View child = grupo.getChildAt(i);
            if (child instanceof LightChipView && ((LightChipView) child).isChecked()) {
                return child;
            }
        }
        return null;
    }

    /** Número de chips marcados del grupo. */
    static int contarMarcados(ChipGroup grupo) {
        int count = 0;
        if (grupo != null) {
            for (int i = 0; i < grupo.getChildCount(); i++) {
                View child = grupo.getChildAt(i);
                if (child instanceof LightChipView && ((LightChipView) child).isChecked()) {
                    count++;
                }
            }
        }
        return count;
    }
}
