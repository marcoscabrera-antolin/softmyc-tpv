package com.softmyc.tpv.data.auth;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Servidor HTTP/1.1 real y mínimo (sobre ServerSocket, sin dependencias) para probar el cliente de red con OkHttp de
 * verdad. Cierra la conexión tras cada respuesta.
 */
public final class FakeApiServer implements AutoCloseable {

    public static final class Request {
        public final String method;
        public final String path;
        /** Cadena de consulta sin el '?' (vacía si no la hay). */
        public final String query;
        public final String body;
        public final Map<String, String> headers;

        Request(String method, String path, String query, String body, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.body = body;
            this.headers = headers;
        }
    }

    public static final class Reply {
        public final int status;
        public final String body;

        public Reply(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private final ServerSocket socket;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Map<String, Function<Request, Reply>> routes = new ConcurrentHashMap<>();
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean closed;

    public FakeApiServer() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        pool.execute(this::acceptLoop);
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + socket.getLocalPort();
    }

    public void on(String path, Function<Request, Reply> handler) {
        routes.put(path, handler);
    }

    public void on(String path, int status, String body) {
        routes.put(path, r -> new Reply(status, body));
    }

    public List<Request> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    public int countOf(String path) {
        int n = 0;
        for (Request r : requests()) if (r.path.equals(path)) n++;
        return n;
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket client = socket.accept();
                pool.execute(() -> serve(client));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket client) {
        try (Socket c = client) {
            c.setSoTimeout(5_000);
            InputStream in = new BufferedInputStream(c.getInputStream());
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int i = line.indexOf(':');
                if (i > 0) headers.put(line.substring(0, i).trim().toLowerCase(), line.substring(i + 1).trim());
            }
            int length = headers.containsKey("content-length") ? Integer.parseInt(headers.get("content-length")) : 0;
            byte[] body = new byte[length];
            int read = 0;
            while (read < length) {
                int n = in.read(body, read, length - read);
                if (n < 0) break;
                read += n;
            }
            int queryStart = parts[1].indexOf('?');
            String path = queryStart >= 0 ? parts[1].substring(0, queryStart) : parts[1];
            String query = queryStart >= 0 ? parts[1].substring(queryStart + 1) : "";
            Request req = new Request(parts[0], path, query, new String(body, StandardCharsets.UTF_8), headers);
            requests.add(req);
            Function<Request, Reply> handler = routes.get(path);
            Reply reply = handler != null ? handler.apply(req) : new Reply(404, "{\"success\":false}");
            byte[] out = reply.body.getBytes(StandardCharsets.UTF_8);
            OutputStream os = c.getOutputStream();
            os.write(("HTTP/1.1 " + reply.status + " X\r\nContent-Type: application/json\r\nContent-Length: " + out.length
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            os.write(out);
            os.flush();
        } catch (IOException ignored) {
            // conexión cortada por el cliente en el test
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
        }
        if (b < 0 && buf.size() == 0) return null;
        return buf.toString(StandardCharsets.UTF_8.name());
    }

    @Override
    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // nada que hacer
        }
        pool.shutdownNow();
    }
}
