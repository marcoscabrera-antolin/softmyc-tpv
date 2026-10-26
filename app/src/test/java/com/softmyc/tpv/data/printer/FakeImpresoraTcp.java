package com.softmyc.tpv.data.printer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Impresora TCP falsa (puerto 9100 simulado) en 127.0.0.1. Acepta una conexión, lee todos los bytes hasta que el
 * cliente cierra el socket y los entrega para comprobar el protocolo ESC/POS de {@link PrinterHelper}.
 */
final class FakeImpresoraTcp implements AutoCloseable {

    private static final long ESPERA_SEGUNDOS = 5;

    private final ServerSocket servidor;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private final CompletableFuture<byte[]> recibido = new CompletableFuture<>();

    FakeImpresoraTcp() throws IOException {
        servidor = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
        hilo.execute(this::aceptarUna);
    }

    int puerto() {
        return servidor.getLocalPort();
    }

    /** Bytes que la impresora ha recibido en la conexión (espera a que el cliente la cierre). */
    byte[] bytesRecibidos() throws Exception {
        return recibido.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
    }

    private void aceptarUna() {
        try (Socket cliente = servidor.accept()) {
            cliente.setSoTimeout((int) TimeUnit.SECONDS.toMillis(ESPERA_SEGUNDOS));
            recibido.complete(leerHastaCierre(cliente.getInputStream()));
        } catch (IOException e) {
            recibido.completeExceptionally(e);
        }
    }

    private static byte[] leerHastaCierre(InputStream entrada) throws IOException {
        ByteArrayOutputStream acumulado = new ByteArrayOutputStream();
        byte[] trozo = new byte[1024];
        int leidos;
        while ((leidos = entrada.read(trozo)) != -1) {
            acumulado.write(trozo, 0, leidos);
        }
        return acumulado.toByteArray();
    }

    @Override
    public void close() throws IOException {
        servidor.close();
        hilo.shutdownNow();
    }
}
