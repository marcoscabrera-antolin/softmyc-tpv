package com.softmyc.tpv.data.printer;

import com.softmyc.tpv.domain.port.AjustesImpresora;
import com.softmyc.tpv.data.SecurePreferencesHelper;

import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrinterHelper {

    // S7: Executor controlado; evita crear hilos sin límite si el usuario pulsa imprimir varias veces
    private static final ExecutorService printerExecutor = Executors.newSingleThreadExecutor();

    private final String ip;
    private final int port;

    private int fontStyle = 0;

    /**
     * Crea un asistente de impresión configurado para comunicarse con una
     * impresora térmica mediante una conexión TCP/IP.
     *
     * @param ip   Dirección IP de la impresora.
     * @param port Puerto de comunicación utilizado por la impresora.
     */
    public PrinterHelper(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    /**
     * Crea un asistente de impresión con estilo de fuente configurado.
     *
     * @param ip        Dirección IP de la impresora.
     * @param port       Puerto de comunicación.
     * @param fontStyle  Tamaño de letra (0: Normal, 1: Mediano/Doble alto, 2: Grande/Doble alto y ancho).
     */
    public PrinterHelper(String ip, int port, int fontStyle) {
        this.ip = ip;
        this.port = port;
        this.fontStyle = fontStyle;
    }


    /**
     * Envía un texto a la impresora térmica utilizando un socket TCP.
     *
     * El proceso se ejecuta en un hilo independiente para evitar bloquear
     * la interfaz. El método realiza:
     *
     *  - Apertura de conexión con la impresora.
     *  - Envío del contenido en codificación ISO-8859-1.
     *  - Alimentación de líneas adicionales tras el texto.
     *  - Ejecución del comando ESC/POS para cortar el papel.
     *  - Cierre seguro del socket y del flujo de salida.
     *
     * Si ocurre algún error durante la comunicación, se captura y muestra
     * mediante un stack trace.
     *
     * @param content Texto completo que se desea imprimir.
     */
    public static byte[] getLogoEscPosBytes(android.content.Context ctx) {
        try {
            android.graphics.drawable.Drawable drawable = ctx.getResources().getDrawable(com.softmyc.tpv.R.drawable.logosoftmyc, null);
            if (drawable == null) return new byte[0];

            int width = 200; 
            int height = 185;
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);

            return convertBitmapToEscPosBytes(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private static byte[] convertBitmapToEscPosBytes(android.graphics.Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int widthBytes = (width + 7) / 8;

        // Detectar si el bitmap tiene transparencias reales
        boolean hasTransparency = false;
        for (int y = 0; y < height; y += 5) {
            for (int x = 0; x < width; x += 5) {
                if (((bitmap.getPixel(x, y) >> 24) & 0xFF) < 200) {
                    hasTransparency = true;
                    break;
                }
            }
            if (hasTransparency) break;
        }

        // Detectar si el fondo es oscuro leyendo las 4 esquinas (solo relevante si no hay transparencia)
        boolean isDarkBackground = false;
        if (!hasTransparency) {
            int corner1 = bitmap.getPixel(0, 0);
            int corner2 = bitmap.getPixel(width - 1, 0);
            int corner3 = bitmap.getPixel(0, height - 1);
            int corner4 = bitmap.getPixel(width - 1, height - 1);
            
            int b1 = (((corner1 >> 16) & 0xFF) + ((corner1 >> 8) & 0xFF) + (corner1 & 0xFF)) / 3;
            int b2 = (((corner2 >> 16) & 0xFF) + ((corner2 >> 8) & 0xFF) + (corner2 & 0xFF)) / 3;
            int b3 = (((corner3 >> 16) & 0xFF) + ((corner3 >> 8) & 0xFF) + (corner3 & 0xFF)) / 3;
            int b4 = (((corner4 >> 16) & 0xFF) + ((corner4 >> 8) & 0xFF) + (corner4 & 0xFF)) / 3;
            
            isDarkBackground = (b1 + b2 + b3 + b4) / 4 < 120;
        }

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            // Comando GS v 0: 1d 76 30 00
            out.write(new byte[]{0x1D, 0x76, 0x30, 0x00});
            // Ancho en bytes: xL y xH
            out.write((byte) (widthBytes & 0xFF));
            out.write((byte) ((widthBytes >> 8) & 0xFF));
            // Alto en píxeles: yL y yH
            out.write((byte) (height & 0xFF));
            out.write((byte) ((height >> 8) & 0xFF));

            // Escribir bits de píxeles
            for (int y = 0; y < height; y++) {
                for (int xByte = 0; xByte < widthBytes; xByte++) {
                    int val = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = xByte * 8 + bit;
                        if (x < width) {
                            int pixel = bitmap.getPixel(x, y);
                            int alpha = (pixel >> 24) & 0xFF;
                            int red   = (pixel >> 16) & 0xFF;
                            int green = (pixel >> 8) & 0xFF;
                            int blue  = pixel & 0xFF;
                            int brightness = (red + green + blue) / 3;

                            boolean shouldPrint = false;
                            if (hasTransparency) {
                                // Para imágenes con transparencia, imprimimos todo lo que no sea transparente y no sea blanco puro
                                shouldPrint = (alpha > 50) && (brightness < 240);
                            } else {
                                if (alpha > 50) {
                                    if (isDarkBackground) {
                                        // Para fondos oscuros, si el logo es naranja, usamos detección de cromatismo (R - B > 30)
                                        shouldPrint = (red - blue > 30) && (red > 80);
                                    } else {
                                        // Para fondos claros o transparentes, imprimimos lo que sea oscuro
                                        shouldPrint = (brightness < 160);
                                    }
                                }
                            }

                            if (shouldPrint) {
                                val |= (1 << (7 - bit));
                            }
                        }
                    }
                    out.write((byte) val);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    public static void imprimirTicketCocina(android.content.Context context, String content) {
        try {
            android.content.SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(context);
            String ip = prefs.getString("impresora_ip", "192.168.0.20");
            int port = prefs.getInt("impresora_puerto", AjustesImpresora.PUERTO_POR_DEFECTO);
            int fontStyle = prefs.getInt("tamanio_letra", 1);
            PrinterHelper printer = new PrinterHelper(ip, port, fontStyle);
            printer.print(context, "=== TICKET DE COCINA (WEB) ===\n\n" + content + "\n\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void print(final String content) {
        print(null, content);
    }

    /** Resultado asíncrono de una impresión (se invoca desde el hilo de la impresora). */
    public interface ResultListener {
        void onResult(boolean ok, String error);
    }

    public void print(final android.content.Context context, final String content) {
        print(context, content, null);
    }

    public void print(final android.content.Context context, final String content, final ResultListener listener) {
        printerExecutor.execute(() -> {
            try (Socket socket = new Socket()) {
                // S1: Timeout estricto de conexión y lectura para evitar congelación del ejecutor
                socket.connect(new java.net.InetSocketAddress(ip, port), 3000);
                socket.setSoTimeout(3000);

                try (OutputStream out = socket.getOutputStream()) {
                    // 1. Inicializar impresora (ESC @)
                    out.write(new byte[]{0x1B, 0x40});

                    // 1.5. Imprimir Logo si existe el contexto
                    if (context != null) {
                        byte[] logo = getLogoEscPosBytes(context);
                        if (logo != null && logo.length > 0) {
                            // Centrar imagen (ESC a 1)
                            out.write(new byte[]{0x1B, 0x61, 0x01});
                            out.write(logo);
                            out.write("\n".getBytes());
                            // Volver a alinear a la izquierda (ESC a 0)
                            out.write(new byte[]{0x1B, 0x61, 0x00});
                        }
                    }

                    // 2. Establecer tamaño de la letra (GS !)
                    // 0x00 = Normal, 0x01 = Doble altura, 0x11 = Doble altura + Doble ancho
                    byte sizeByte = 0x00;
                    if (fontStyle == 1) {
                        sizeByte = 0x01;
                    } else if (fontStyle == 2) {
                        sizeByte = 0x11;
                    }
                    out.write(new byte[]{0x1D, 0x21, sizeByte});

                    // Enviar contenido (reemplazando cualquier '€' por 'EUR' para evitar el caracter '?' en ISO-8859-1)
                    String safeContent = (content != null) ? content.replace("€", "EUR") : "";
                    out.write(safeContent.getBytes("ISO-8859-1"));

                    // Feed de líneas
                    out.write("\n\n\n".getBytes());

                    // Paper cut
                    out.write(new byte[]{0x1D, 0x56, 0x01});

                    out.flush();
                }
                if (listener != null) listener.onResult(true, null);
            } catch (Exception e) {
                android.util.Log.e("PrinterHelper", "Error al imprimir ticket: " + e.getMessage(), e);
                if (listener != null) listener.onResult(false, e.getMessage());
            }
        });
    }
}
