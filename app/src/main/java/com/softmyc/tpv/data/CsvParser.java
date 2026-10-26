package com.softmyc.tpv.data;

import com.softmyc.tpv.data.local.ProductoLocal;
import com.softmyc.tpv.data.local.ExtraLocal;
import com.softmyc.tpv.data.local.SalsaLocal;
import com.softmyc.tpv.util.CategoryUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CsvParser {

    public static class ParseResult {
        public final List<ProductoLocal> productos = new ArrayList<>();
        public final List<ExtraLocal> extras = new ArrayList<>();
        public final List<SalsaLocal> salsas = new ArrayList<>();
        public final List<String> logs = new ArrayList<>();
    }

    private static final Pattern PRODUCT_PATTERN = Pattern.compile("^(\\d+)\\)\\s*([^:]+):\\s*(.*?)\\.{2,}\\s*([\\d,.]+)\\s*€?", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTRA_PATTERN = Pattern.compile("^([^.]+)\\.{2,}\\s*([\\d,.]+)\\s*€?", Pattern.CASE_INSENSITIVE);

    public static ParseResult parse(InputStream inputStream, String categoriaDefecto) {
        ParseResult result = new ParseResult();
        if (inputStream == null) {
            result.logs.add("Error: Stream de entrada nulo.");
            return result;
        }
        try (InputStream is = inputStream;
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            int lineNum = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("CODIGO") || line.startsWith("codigo")) continue;

                // 1. Intentar patrón de producto: 1) NUBE: Pollo, Jamón... 4,00€
                Matcher mProd = PRODUCT_PATTERN.matcher(line);
                if (mProd.find()) {
                    try {
                        int codigo = Integer.parseInt(mProd.group(1).trim());
                        String nombre = mProd.group(2).trim();
                        String ingRaw = mProd.group(3).trim();
                        double precio = Double.parseDouble(mProd.group(4).replace(",", ".").trim());

                        List<String> ingredientes = new ArrayList<>();
                        if (!ingRaw.isEmpty()) {
                            for (String s : ingRaw.split(",")) {
                                if (!s.trim().isEmpty()) ingredientes.add(s.trim());
                            }
                        }
                        result.productos.add(new ProductoLocal(codigo, nombre, precio, CategoryUtils.capitalizeCategory(categoriaDefecto), ingredientes));
                        continue;
                    } catch (Exception ignored) {}
                }

                // 2. Intentar patrón de extra: Pollo....0,00€
                Matcher mExtra = EXTRA_PATTERN.matcher(line);
                if (mExtra.find()) {
                    try {
                        String nombre = mExtra.group(1).replace(",", "").trim();
                        double precio = Double.parseDouble(mExtra.group(2).replace(",", ".").trim());
                        if (!nombre.isEmpty()) result.extras.add(new ExtraLocal(nombre, precio));
                        continue;
                    } catch (Exception ignored) {}
                }

                // 3. Fallback salsa o línea simple sin precio (no debe contener delimitadores estructurados)
                if (!line.contains(".") && !line.contains(",") && !line.contains(";") && !line.contains("|")) {
                    result.salsas.add(new SalsaLocal(line));
                    continue;
                }

                // 4. Fallback delimitador coma/punto y coma tradicional
                String[] token = line.split("[,;|]");
                if (token.length >= 4) {
                    try {
                        int codigo = Integer.parseInt(token[0].trim());
                        String nombre = token[1].trim();
                        double precio = Double.parseDouble(token[2].trim().replace("$", "").replace("€", "").replace(",", "."));
                        String categoria = token[3].trim();
                        List<String> ingredientes = new ArrayList<>();
                        if (token.length > 4) {
                            for (String s : token[4].replace("\"", "").split("[-+]")) {
                                if (!s.trim().isEmpty()) ingredientes.add(s.trim());
                            }
                        }
                        if ("extra".equalsIgnoreCase(categoria)) {
                            result.extras.add(new ExtraLocal(nombre, precio));
                        } else if ("salsa".equalsIgnoreCase(categoria)) {
                            result.salsas.add(new SalsaLocal(nombre));
                        } else {
                            result.productos.add(new ProductoLocal(codigo, nombre, precio, CategoryUtils.capitalizeCategory(categoria), ingredientes));
                        }
                    } catch (Exception e) {
                        result.logs.add("Línea " + lineNum + ": Falla de conversión");
                    }
                }
            }
        } catch (Exception e) {
            result.logs.add("Error crítico de lectura: " + e.getMessage());
        }
        return result;
    }

    public static ParseResult parse(InputStream inputStream) {
        return parse(inputStream, "Otros");
    }
}
