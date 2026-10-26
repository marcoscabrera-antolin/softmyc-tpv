package com.softmyc.tpv.presentation;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.softmyc.tpv.R;

import java.util.ArrayList;
import java.util.List;

public class AyudaActivity extends BaseActivity {

    private ViewPager2 viewPagerHelp;
    private LinearLayout layoutDots;
    private Button btnAtras;
    private Button btnSiguiente;
    private ImageButton btnCerrar;

    private List<HelpSlide> slides;
    private TextView[] dots;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ayuda);

        setupBackButton();

        viewPagerHelp = findViewById(R.id.viewPagerHelp);
        layoutDots = findViewById(R.id.layoutDots);
        btnAtras = findViewById(R.id.btnHelpAtras);
        btnSiguiente = findViewById(R.id.btnHelpSiguiente);
        btnCerrar = findViewById(R.id.btnCerrarAyuda);

        btnCerrar.setOnClickListener(v -> finish());

        inicializarSlides();

        HelpAdapter adapter = new HelpAdapter(slides);
        viewPagerHelp.setAdapter(adapter);

        agregarDotsIndicadores(0);

        viewPagerHelp.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                agregarDotsIndicadores(position);

                // Controlar botones de navegación inferior
                if (position == 0) {
                    btnAtras.setVisibility(View.INVISIBLE);
                } else {
                    btnAtras.setVisibility(View.VISIBLE);
                }

                if (position == slides.size() - 1) {
                    btnSiguiente.setText(getString(R.string.entendido));
                } else {
                    btnSiguiente.setText(getString(R.string.siguiente));
                }
            }
        });

        btnAtras.setOnClickListener(v -> {
            int current = viewPagerHelp.getCurrentItem();
            if (current > 0) {
                viewPagerHelp.setCurrentItem(current - 1, true);
            }
        });

        btnSiguiente.setOnClickListener(v -> {
            int current = viewPagerHelp.getCurrentItem();
            if (current < slides.size() - 1) {
                viewPagerHelp.setCurrentItem(current + 1, true);
            } else {
                finish(); // Al hacer clic en "Entendido" en el último paso, cerramos el asistente
            }
        });
    }

    private void inicializarSlides() {
        slides = new ArrayList<>();
        slides.add(new HelpSlide(
                "1. Ajustes del Sistema",
                "Ingresa a Configuración para definir la dirección IP de tu impresora local y establecer tus datos fiscales (Nombre de marca, NIF y Dirección) para que figuren en la cabecera del ticket.",
                R.drawable.ic_settings_sliders,
                Color.parseColor("#F43F5E"), // Rose
                "Impresora: 192.168.0.20",
                "SoftMYC TPV - Ajustes de Red"
        ));
        slides.add(new HelpSlide(
                "2. Crear un Bocadillo o Plato",
                "Para añadir bocadillos, hamburguesas u otros platos principales, agrégalos en tu archivo CSV con la siguiente estructura:\nCódigo, Nombre, Precio, Categoría y la lista de Ingredientes separados por guiones.\n\nEjemplo: 2, Serranito, 4.50, bocadillos, lomo-pimiento-jamon",
                R.drawable.ic_inventory_box,
                Color.parseColor("#FF9F1C"), // Orange/Amber
                "Serranito - 4.50 EUR (bocadillos)",
                "Ingredientes: lomo, pimiento, jamón"
        ));
        slides.add(new HelpSlide(
                "3. Crear Salsas y Extras",
                "Complementa la carta agregando ingredientes extra (categoría 'extra') o salsas seleccionables (categoría 'salsa') en tu CSV:\n- Salsas: 100, Mayonesa, 0.00, salsa\n- Extras: 101, Queso Extra, 0.50, extra",
                R.drawable.ic_settings_sliders,
                Color.parseColor("#10B981"), // Emerald
                "Mayonesa (salsa) - 0.00 EUR",
                "Queso Extra (extra) - 0.50 EUR"
        ));
        slides.add(new HelpSlide(
                "4. Importar la Carta al TPV",
                "Guarda tu archivo CSV. Luego ve a Ajustes del TPV y pulsa 'Importar CSV'. Selecciona el archivo para actualizar la carta local y sincronizarla de inmediato con el servidor.",
                R.drawable.ic_inventory_box,
                Color.parseColor("#38BDF8"), // Blue
                "=== IMPORTACIÓN COMPLETADA ===",
                "Carta cargada y sincronizada correctamente"
        ));
        slides.add(new HelpSlide(
                "5. Tomar un Pedido",
                "En 'Hacer Pedido', selecciona los platos principales. Podrás retirar ingredientes a gusto del cliente pulsándolos, o añadir salsas y extras desde las listas recomendadas.",
                R.drawable.ic_shopping_cart,
                Color.parseColor("#E11D48"), // Rose-dark
                "1 x Burguer Especial (+Queso, -Cebolla)",
                "Subtotal: 6.00 EUR"
        ));
        slides.add(new HelpSlide(
                "6. Registrar e Imprimir",
                "Instaura la hora de recogida si es oportuno y pulsa 'Imprimir'. El pedido se enviará directo a la impresora y la venta se guardará en el historial de ventas del dispositivo y se sincronizará con el servidor.",
                R.drawable.ic_trending_up,
                Color.parseColor("#059669"), // Emerald dark
                "¡VENTA REGISTRADA CON ÉXITO!",
                "Imprimiendo ticket de comanda..."
        ));
    }

    private void agregarDotsIndicadores(int posicionActiva) {
        layoutDots.removeAllViews();
        dots = new TextView[slides.size()];

        for (int i = 0; i < dots.length; i++) {
            dots[i] = new TextView(this);
            dots[i].setText("•");
            dots[i].setTextSize(35);
            if (i == posicionActiva) {
                dots[i].setTextColor(Color.parseColor("#38BDF8")); // Indicador activo (celeste)
            } else {
                dots[i].setTextColor(Color.parseColor("#475569")); // Apagado
            }
            // Agregamos márgenes entre puntos
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            layoutDots.addView(dots[i], params);
        }
    }

    private static class HelpSlide {
        final String title;
        final String description;
        final int iconRes;
        final int tintColor;
        final String text1;
        final String text2;

        HelpSlide(String title, String description, int iconRes, int tintColor, String text1, String text2) {
            this.title = title;
            this.description = description;
            this.iconRes = iconRes;
            this.tintColor = tintColor;
            this.text1 = text1;
            this.text2 = text2;
        }
    }

    private class HelpAdapter extends RecyclerView.Adapter<HelpAdapter.ViewHolder> {
        private final List<HelpSlide> slides;

        HelpAdapter(List<HelpSlide> slides) {
            this.slides = slides;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ayuda_slide, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HelpSlide slide = slides.get(position);
            holder.tvTitle.setText(slide.title);
            holder.tvDescription.setText(slide.description);
            holder.imgIllustration.setImageResource(slide.iconRes);
            holder.imgIllustration.setColorFilter(slide.tintColor);
            holder.tvMock1.setText(slide.text1);
            holder.tvMock2.setText(slide.text2);
        }

        @Override
        public int getItemCount() {
            return slides.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvDescription, tvMock1, tvMock2;
            ImageView imgIllustration;

            ViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvSlideTitle);
                tvDescription = itemView.findViewById(R.id.tvSlideDescription);
                imgIllustration = itemView.findViewById(R.id.imgSlideIllustration);
                tvMock1 = itemView.findViewById(R.id.tvMockupText1);
                tvMock2 = itemView.findViewById(R.id.tvMockupText2);
            }
        }
    }
}
