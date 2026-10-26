package com.softmyc.tpv.presentation;

import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.softmyc.tpv.R;

/** Referencias a las vistas del diálogo de personalización. Todas son null hasta que se infla la vista. */
final class PersonalizacionVistas {
    TextView txtModalTitle;
    TextView txtDescripcionBase;
    TextView txtPrecioBase;
    View btnLess;
    View btnMore;
    TextView txtQuantity;
    TextView txtTotalEstimado;
    MaterialButton btnConfirmAdd;
    ChipGroup chipGroupQuitar;
    ChipGroup chipGroupAnadir;
    ChipGroup chipGroupSalsas;
    TextView txtTituloTarjeta1;
    TextView txtTituloTarjeta2;
    TextView txtTituloTarjeta3;

    /** Vistas vacías: se usan antes de que el diálogo infle su layout. */
    PersonalizacionVistas() {
    }

    PersonalizacionVistas(View raiz) {
        txtModalTitle = raiz.findViewById(R.id.txtModalTitle);
        txtDescripcionBase = raiz.findViewById(R.id.txtDescripcionBase);
        txtPrecioBase = raiz.findViewById(R.id.txtPrecioBase);
        btnLess = raiz.findViewById(R.id.btnLessQuantity);
        btnMore = raiz.findViewById(R.id.btnMoreQuantity);
        txtQuantity = raiz.findViewById(R.id.txtQuantity);
        txtTotalEstimado = raiz.findViewById(R.id.txtTotalEstimado);
        btnConfirmAdd = raiz.findViewById(R.id.btnConfirmAdd);
        chipGroupQuitar = raiz.findViewById(R.id.chipGroupQuitar);
        chipGroupAnadir = raiz.findViewById(R.id.chipGroupAnadir);
        chipGroupSalsas = raiz.findViewById(R.id.chipGroupSalsas);
        txtTituloTarjeta1 = raiz.findViewById(R.id.txtTituloTarjeta1);
        txtTituloTarjeta2 = raiz.findViewById(R.id.txtTituloTarjeta2);
        txtTituloTarjeta3 = raiz.findViewById(R.id.txtTituloTarjeta3);
    }

    static void setTexto(TextView vista, String texto) {
        if (vista != null) vista.setText(texto);
    }

    static void setTexto(TextView vista, int recursoTexto) {
        if (vista != null) vista.setText(vista.getContext().getString(recursoTexto));
    }
}
