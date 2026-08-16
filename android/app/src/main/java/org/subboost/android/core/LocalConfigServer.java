package org.subboost.android.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** Small token-protected HTTP server for importing the generated YAML over the LAN. */
public final class LocalConfigServer {
    private static final LocalConfigServer INSTANCE = new LocalConfigServer();
    private final AtomicReference<String> yaml = new AtomicReference<>("");
    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;
    private ExecutorService clientExecutor;
    private String token = "";

    public static LocalConfigServer get() { return INSTANCE; }

    public synchronized void start(int port, String content, String accessToken) throws IOException {
        if (blank(content)) throw new IllegalArgumentException("没有可供导入的配置内容");
        if (blank(accessToken)) throw new IllegalArgumentException("访问令牌不能为空");
        if (serverSocket != null && !serverSocket.isClosed()) {
            if (serverSocket.getLocalPort() != port && port != 0) stop();
            else { yaml.set(content); token = accessToken; return; }
        }
        ServerSocket socket = new ServerSocket(port, 20, InetAddress.getByName("0.0.0.0"));
        socket.setReuseAddress(true);
        serverSocket = socket;
        yaml.set(content);
        token = accessToken;
        acceptExecutor = Executors.newSingleThreadExecutor();
        clientExecutor = Executors.newFixedThreadPool(3);
        acceptExecutor.execute(this::acceptLoop);
    }

    public synchronized void update(String content) {
        if (!blank(content)) yaml.set(content);
    }

    public synchronized void stop() {
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) { }
        }
        serverSocket = null;
        if (acceptExecutor != null) acceptExecutor.shutdownNow();
        if (clientExecutor != null) clientExecutor.shutdownNow();
        acceptExecutor = null;
        clientExecutor = null;
        yaml.set("");
        token = "";
    }

    public synchronized boolean isRunning() {
        return serverSocket != null && !serverSocket.isClosed();
    }

    public synchronized int port() {
        return isRunning() ? serverSocket.getLocalPort() : -1;
    }

    public synchronized String link(String host) {
        if (!isRunning()) return "";
        return "http://" + host + ":" + port() + "/config.yaml?token=" + token;
    }

    private void acceptLoop() {
        while (true) {
            ServerSocket current;
            synchronized (this) { current = serverSocket; }
            if (current == null || current.isClosed()) return;
            try {
                Socket client = current.accept();
                client.setSoTimeout(5_000);
                ExecutorService pool;
                synchronized (this) { pool = clientExecutor; }
                if (pool != null) pool.execute(() -> handle(client));
                else client.close();
            } catch (SocketException error) {
                return;
            } catch (IOException ignored) { }
        }
    }

    private void handle(Socket socket) {
        try (Socket client = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.length() > 4096) { write(writer, 400, "text/plain; charset=utf-8", "Bad Request\n", false); return; }
            String[] request = requestLine.split(" ");
            if (request.length < 2) { write(writer, 400, "text/plain; charset=utf-8", "Bad Request\n", false); return; }
            String method = request[0].toUpperCase(Locale.ROOT);
            String target = request[1];
            String header;
            int headerBytes = 0;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {
                headerBytes += header.length();
                if (headerBytes > 16 * 1024) { write(writer, 431, "text/plain; charset=utf-8", "Headers too large\n", false); return; }
            }
            if (method.equals("OPTIONS")) { write(writer, 204, "text/plain", "", false); return; }
            if (!method.equals("GET") && !method.equals("HEAD")) { write(writer, 405, "text/plain; charset=utf-8", "Method Not Allowed\n", false); return; }
            int query = target.indexOf('?');
            String path = query < 0 ? target : target.substring(0, query);
            String supplied = query < 0 ? "" : queryValue(target.substring(query + 1), "token");
            String expected;
            synchronized (this) { expected = token; }
            if (!path.equals("/config.yaml")) { write(writer, 404, "text/plain; charset=utf-8", "Not Found\n", false); return; }
            if (!secureEquals(expected, supplied)) { write(writer, 403, "text/plain; charset=utf-8", "Forbidden\n", false); return; }
            write(writer, 200, "application/yaml; charset=utf-8", yaml.get(), method.equals("HEAD"));
        } catch (IOException ignored) { }
    }

    private void write(BufferedWriter writer, int status, String type, String body, boolean head) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        writer.write("HTTP/1.1 " + status + " " + reason(status) + "\r\n");
        writer.write("Content-Type: " + type + "\r\n");
        writer.write("Content-Length: " + bytes.length + "\r\n");
        writer.write("Cache-Control: no-store\r\n");
        writer.write("Access-Control-Allow-Origin: *\r\n");
        writer.write("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n");
        writer.write("Connection: close\r\n\r\n");
        if (!head) writer.write(body);
        writer.flush();
    }

    private String queryValue(String query, String key) {
        for (String item : query.split("&")) {
            String[] pair = item.split("=", 2);
            try {
                String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name());
                if (name.equals(key)) return pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name()) : "";
            } catch (Exception ignored) { }
        }
        return "";
    }

    private boolean secureEquals(String expected, String supplied) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private String reason(int status) {
        return switch (status) {
            case 200 -> "OK"; case 204 -> "No Content"; case 400 -> "Bad Request";
            case 403 -> "Forbidden"; case 404 -> "Not Found"; case 405 -> "Method Not Allowed";
            case 431 -> "Request Header Fields Too Large"; default -> "Error";
        };
    }

    public static String newToken() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String findLanIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface network : Collections.list(interfaces)) {
                if (!network.isUp() || network.isLoopback()) continue;
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) { }
        return null;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private LocalConfigServer() { }
}
