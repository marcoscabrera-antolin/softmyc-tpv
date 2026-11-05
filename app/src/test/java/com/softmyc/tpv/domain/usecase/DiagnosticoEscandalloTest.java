package com.softmyc.tpv.domain.usecase;

import static com.softmyc.tpv.domain.usecase.EscandalloDePrueba.nube;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.domain.escandallo.Ingrediente;
import com.softmyc.tpv.domain.escandallo.Recetario;
import com.softmyc.tpv.domain.escandallo.UnidadIngrediente;
import com.softmyc.tpv.domain.repository.ProductoCatalogo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

public class DiagnosticoEscandalloTest {

    @Test
    public void detecta_productos_sin_receta_ingredientes_sin_casar_extras_y_precios() {
        ProductoCatalogo nube = new ProductoCatalogo(1, "Nube", 5.5, "Bocadillos",
                Arrays.asList("Pollo", "Jamón", "Salsa Nube", "Aceite de Oliva"), "UD", -1);
        ProductoCatalogo tigger = new ProductoCatalogo(9, "Tigger", 4.5, "Bocadillos",
                Collections.singletonList("Roquefort"), "UD", -1);

        DiagnosticoEscandallo d = DiagnosticoEscandallo.de(Arrays.asList(nube, tigger),
                Arrays.asList("Queso", "Aguacate"), nube(null));

        assertEquals(Collections.singletonList("9 Tigger"), d.productosSinReceta);
        assertEquals(Collections.singletonList("Nube: Aceite de Oliva"), d.ingredientesSinCasar);
        assertEquals(Collections.singletonList("Aguacate"), d.extrasSinConsumo);
        assertTrue(d.ingredientesSinPrecio.isEmpty());
        assertFalse(d.completo());
    }

    @Test
    public void escandallo_completo() {
        ProductoCatalogo nube = new ProductoCatalogo(1, "Nube", 5.5, "Bocadillos",
                Arrays.asList("pollo", "JAMON", " "), "UD", -1);

        DiagnosticoEscandallo d = DiagnosticoEscandallo.de(Collections.singletonList(nube),
                Collections.singletonList("queso"), nube(null));

        assertTrue(d.completo());
    }

    @Test
    public void ingrediente_sin_precio_y_producto_sin_lista_de_carta() {
        Recetario r = new Recetario(
                Collections.singletonList(new Ingrediente(1, "Roquefort", UnidadIngrediente.G, null, null)),
                Collections.singletonList(new com.softmyc.tpv.domain.escandallo.LineaReceta(9, 1, 30, null)),
                Collections.emptyList(), new HashMap<>());
        ProductoCatalogo sinLista = new ProductoCatalogo(9, "Tigger", 4.5, "B", null, "UD", -1);

        DiagnosticoEscandallo d = DiagnosticoEscandallo.de(Collections.singletonList(sinLista), null, r);

        assertEquals(Collections.singletonList("Roquefort"), d.ingredientesSinPrecio);
        assertTrue(d.ingredientesSinCasar.isEmpty());
    }
}
