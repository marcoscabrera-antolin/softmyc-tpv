package com.softmyc.tpv.presentation;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Comprueba que la barra inferior de MainActivity no deja los botones fuera de pantalla:
 * en vertical la barra debe poder desplazarse en horizontal para alcanzar btnAyuda.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainLayoutTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final int ANCHO_VERTICAL_PX = 1200;
    private static final int ALTO_VERTICAL_PX = 1920;

    @Test
    public void barraInferiorEsDesplazableYContieneBtnAyuda() {
        View raiz = inflarActivityMain();

        View barra = raiz.findViewById(R.id.bottomBar);
        View btnAyuda = raiz.findViewById(R.id.btnAyuda);

        assertNotNull(barra);
        assertTrue("La barra inferior debe ser un HorizontalScrollView", barra instanceof HorizontalScrollView);
        assertTrue("btnAyuda debe estar dentro de la barra desplazable", estaDentro(btnAyuda, barra));
    }

    @Test
    public void botonAyudaSeMideDentroDeLaBarraEnVertical() {
        View raiz = inflarActivityMain();
        raiz.measure(
                View.MeasureSpec.makeMeasureSpec(ANCHO_VERTICAL_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ALTO_VERTICAL_PX, View.MeasureSpec.EXACTLY));
        raiz.layout(0, 0, ANCHO_VERTICAL_PX, ALTO_VERTICAL_PX);

        View barra = raiz.findViewById(R.id.bottomBar);
        View btnAyuda = raiz.findViewById(R.id.btnAyuda);

        assertTrue("btnAyuda debe tener tamaño medido", btnAyuda.getMeasuredWidth() > 0);
        assertTrue("la barra debe ocupar el ancho de la pantalla", barra.getMeasuredWidth() <= ANCHO_VERTICAL_PX);
    }

    private View inflarActivityMain() {
        FrameLayout contenedor = new FrameLayout(ApplicationProvider.getApplicationContext());
        return LayoutInflater.from(new android.view.ContextThemeWrapper(
                        ApplicationProvider.getApplicationContext(), R.style.Theme_MyApplication))
                .inflate(R.layout.activity_main, contenedor, false);
    }

    private static boolean estaDentro(View hijo, View ancestro) {
        for (View actual = hijo; actual != null; actual = (View) actual.getParent()) {
            if (actual == ancestro) {
                return true;
            }
            if (!(actual.getParent() instanceof ViewGroup)) {
                return false;
            }
        }
        return false;
    }
}
