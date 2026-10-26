package com.softmyc.tpv.presentation;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import com.softmyc.tpv.R;
import com.softmyc.tpv.domain.CustomizedOrderItem;
import com.softmyc.tpv.domain.IngredientOption;
import com.softmyc.tpv.domain.NetWeight;
import com.softmyc.tpv.domain.Producto;
import com.softmyc.tpv.domain.pricing.PrevisualizacionPrecio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Diálogo polimórfico universal de personalización comercial (Fase 2).
 *
 * Elige el {@link PersonalizacionModo} según el sector del producto: gastronomía, frutería/granel, taller/servicios
 * o mayorista. Este diálogo solo gestiona el ciclo de vida, la cantidad mostrada y la confirmación; el contenido de
 * cada modo vive en sus clases {@code PersonalizacionModo*}.
 */
public class PersonalizacionBottomSheetDialog extends DialogFragment {

    private static final String TAG = "PersonalizacionDialog";
    private static final double TAMANO_ANCHO = 0.85;
    private static final double TAMANO_ALTO = 0.88;
    private static final int UNIDADES_VOLUMEN_INICIALES = 10;

    private final Producto producto;
    private final List<IngredientOption> availableExtras;
    private final List<IngredientOption> availableSauces;
    private final OnOrderItemCreatedListener listener;
    private final PersonalizacionEstado estado;
    private final PersonalizacionAcciones acciones = new PersonalizacionAcciones() {
        @Override
        public Context contexto() {
            return getContext();
        }

        @Override
        public void actualizarPrecio() {
            updateQuantityAndPrice();
        }
    };

    /** Vacías hasta que {@link #onCreateView} infla el layout. */
    private PersonalizacionVistas vistas = new PersonalizacionVistas();

    public interface OnOrderItemCreatedListener {
        void onItemCreated(CustomizedOrderItem item);
    }

    public PersonalizacionBottomSheetDialog(
            Producto producto,
            List<IngredientOption> availableExtras,
            List<IngredientOption> availableSauces,
            OnOrderItemCreatedListener listener
    ) {
        this.producto = producto != null ? producto : new Producto();
        this.availableExtras = availableExtras != null ? availableExtras : new ArrayList<>();
        this.availableSauces = availableSauces != null ? availableSauces : new ArrayList<>();
        this.listener = listener;
        this.estado = estadoInicial(this.producto);
    }

    /**
     * Cantidad inicial según el sector. Peso y tiempo tienen prioridad: solo los volúmenes sin esos atributos
     * empiezan en {@link #UNIDADES_VOLUMEN_INICIALES}; el resto empieza en 1.
     */
    private static PersonalizacionEstado estadoInicial(Producto producto) {
        boolean volumenPuro = !producto.isWeightBased() && !producto.isTimeBased() && producto.isVolumeTiered();
        if (volumenPuro) {
            return new PersonalizacionEstado(UNIDADES_VOLUMEN_INICIALES, UNIDADES_VOLUMEN_INICIALES);
        }
        return new PersonalizacionEstado(1, 1.0);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        try {
            if (dialog.getWindow() != null) {
                dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            dialog.setCanceledOnTouchOutside(false);
            setCancelable(false);
        } catch (Exception e) {
            Log.e(TAG, "Error en onCreateDialog", e);
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            Dialog dialog = getDialog();
            if (dialog != null && dialog.getWindow() != null) {
                dialog.setCanceledOnTouchOutside(false);
                setCancelable(false);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                if (getResources() != null && getResources().getDisplayMetrics() != null) {
                    DisplayMetrics metrics = getResources().getDisplayMetrics();
                    int targetWidth = (int) (metrics.widthPixels * TAMANO_ANCHO);
                    int targetHeight = (int) (metrics.heightPixels * TAMANO_ALTO);
                    dialog.getWindow().setLayout(targetWidth, targetHeight);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al configurar dimensiones en onStart", e);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_personalizar_producto, container, false);

        try {
            vistas = new PersonalizacionVistas(view);
            View btnClose = view.findViewById(R.id.btnCloseModal);
            activarScrollAnidado(view);

            if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());

            // 1. CONFIGURACIÓN POLIMÓRFICA SEGÚN SECTOR COMERCIAL
            modoPara(producto).configurar(producto, vistas, estado, acciones);

            // 2. BOTÓN DE CONFIRMACIÓN AL CARRITO
            if (vistas.btnConfirmAdd != null) {
                vistas.btnConfirmAdd.setOnClickListener(v -> confirmarYAgregar());
            }

            updateQuantityAndPrice();

        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar el modal de personalización", e);
        }

        return view;
    }

    private void activarScrollAnidado(View view) {
        int[] scrollIds = {R.id.scrollQuitar, R.id.scrollAnadir, R.id.scrollSalsas};
        for (int id : scrollIds) {
            NestedScrollView scroll = view.findViewById(id);
            if (scroll != null) scroll.setNestedScrollingEnabled(true);
        }
    }

    private PersonalizacionModo modoPara(Producto producto) {
        if (producto.isWeightBased()) return new PersonalizacionModoFruteria();
        if (producto.isTimeBased()) return new PersonalizacionModoTaller();
        if (producto.isVolumeTiered()) return new PersonalizacionModoMayorista();
        return new PersonalizacionModoGastronomia(availableExtras, availableSauces);
    }

    private void confirmarYAgregar() {
        if (producto.isWeightBased() && !NetWeight.isValid(estado.getDecimalQuantity(), estado.getTareKg())) {
            Toast.makeText(requireContext(), getString(R.string.el_peso_debe_superar_la_tara_del_envase), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            CustomizedOrderItem orderItem = construirItem();
            if (listener != null) listener.onItemCreated(orderItem);
            dismiss();
        } catch (Exception e) {
            Log.e(TAG, "Error al confirmar item personalizado", e);
            dismiss();
        }
    }

    private CustomizedOrderItem construirItem() {
        return PersonalizacionItemBuilder.construir(producto, estado.getQuantity(), estado.getDecimalQuantity(),
                estado.getTareKg(), vistas.chipGroupQuitar, vistas.chipGroupAnadir, vistas.chipGroupSalsas);
    }

    /** Actualiza cantidad y precio mostrados con el mismo motor que usa el carrito (NEG-14). */
    private void updateQuantityAndPrice() {
        try {
            PrevisualizacionPrecio.Resultado preview = PrevisualizacionPrecio.calcular(construirItem());
            if (producto.isWeightBased()) {
                mostrarPreviewPeso(preview);
            } else if (producto.isTimeBased()) {
                PersonalizacionVistas.setTexto(vistas.txtQuantity,
                        String.format(Locale.getDefault(), "%.1f h", estado.getDecimalQuantity()));
                PersonalizacionVistas.setTexto(vistas.txtTotalEstimado,
                        getString(R.string.total_estimado_fmt, preview.total));
            } else if (producto.isVolumeTiered()) {
                PersonalizacionVistas.setTexto(vistas.txtQuantity, String.valueOf(estado.getQuantity()));
                PersonalizacionVistas.setTexto(vistas.txtTotalEstimado,
                        getString(R.string.tarifa_total_fmt, preview.precioUnitario, preview.total));
            } else {
                PersonalizacionVistas.setTexto(vistas.txtQuantity, String.valueOf(estado.getQuantity()));
                PersonalizacionVistas.setTexto(vistas.txtTotalEstimado,
                        getString(R.string.total_estimado_fmt, preview.total));
            }
            if (vistas.btnConfirmAdd != null) {
                vistas.btnConfirmAdd.setText(getString(R.string.anadir_al_carrito));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en updateQuantityAndPrice", e);
        }
    }

    private void mostrarPreviewPeso(PrevisualizacionPrecio.Resultado preview) {
        if (vistas.btnConfirmAdd != null) vistas.btnConfirmAdd.setEnabled(preview.pesoValido);
        PersonalizacionVistas.setTexto(vistas.txtQuantity,
                String.format(Locale.getDefault(), "%.3f kg", estado.getDecimalQuantity()));
        PersonalizacionVistas.setTexto(vistas.txtTotalEstimado, preview.pesoValido
                ? getString(R.string.neto_total_fmt, preview.netoKg, preview.total)
                : getString(R.string.el_peso_debe_superar_la_tara_del_envase));
    }
}
