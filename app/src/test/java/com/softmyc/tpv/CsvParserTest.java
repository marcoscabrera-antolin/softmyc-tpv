package com.softmyc.tpv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.softmyc.tpv.data.CsvParser;
import com.softmyc.tpv.data.CsvParser.ParseResult;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class CsvParserTest {

    @Test
    public void testParseValidProducts() {
        String csvData = "CODIGO;NOMBRE;PRECIO;CATEGORIA;INGREDIENTES\n" +
                "101;Bocadillo Jamon;4.50;bocadillos;Jamon - Tomate - Aceite\n" +
                "102;Tostada Mantequilla;1.80;tostadas;Mantequilla\n";

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        ParseResult result = CsvParser.parse(stream);

        // Verificar productos cargados
        assertEquals(2, result.productos.size());
        assertTrue(result.extras.isEmpty());
        assertTrue(result.salsas.isEmpty());

        assertEquals(101, result.productos.get(0).codigo);
        assertEquals("Bocadillo Jamon", result.productos.get(0).nombre);
        assertEquals(4.50, result.productos.get(0).precio, 0.001);
        assertEquals("Bocadillos", result.productos.get(0).categoria);
        assertEquals(3, result.productos.get(0).ingredientes.size());
        assertEquals("Jamon", result.productos.get(0).ingredientes.get(0));
    }

    @Test
    public void testParseExtrasAndSalsas() {
        String csvData = "CODIGO,NOMBRE,PRECIO,CATEGORIA,INGREDIENTES\n" +
                "0,Queso Extra,0.60,extra,\n" +
                "0,Mayonesa,0.00,salsa,\n";

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        ParseResult result = CsvParser.parse(stream);

        // Verificar extras y salsas
        assertEquals(0, result.productos.size());
        assertEquals(1, result.extras.size());
        assertEquals(1, result.salsas.size());

        assertEquals("Queso Extra", result.extras.get(0).nombre);
        assertEquals(0.60, result.extras.get(0).precio, 0.001);
        assertEquals("Mayonesa", result.salsas.get(0).nombre);
    }

    @Test
    public void testParseMalformatedLine() {
        String csvData = "CODIGO;NOMBRE;PRECIO;CATEGORIA;INGREDIENTES\n" +
                "no_valido;Invalido;precio;bocadillos\n" + 
                "103;Bocadillo Bacon;3.50;bocadillos;Bacon\n";

        InputStream stream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        ParseResult result = CsvParser.parse(stream);

        // Se descarta la primera y se procesa correctamente la segunda
        assertEquals(1, result.productos.size());
        assertEquals(103, result.productos.get(0).codigo);
        assertEquals("Bocadillo Bacon", result.productos.get(0).nombre);
        assertEquals(1, result.logs.size());
        assertTrue(result.logs.get(0).contains("Línea 2: Falla de conversión"));
    }
}
