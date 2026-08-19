package org.subboost.android.core;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Checks node endpoints before a configuration is generated. */
public final class NodeConnectivityChecker {
    public static final int DEFAULT_TIMEOUT_MILLIS = 5_000;

    public Result check(List<Map<String, Object>> nodes, String testUrl) {
        return check(nodes, testUrl, DEFAULT_TIMEOUT_MILLIS);
    }

    public Result check(List<Map<String, Object>> nodes, String testUrl, int timeoutMillis) {
        List<Map<String, Object>> available = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<Map<String, Object>> input = nodes == null ? Collections.<Map<String, Object>>emptyList() : nodes;
        for (Map<String, Object> node : input) {
            Check check = checkNode(node, testUrl, timeoutMillis);
            String name = String.valueOf(node.getOrDefault("name", "未命名节点"));
            if (check == Check.AVAILABLE) available.add(new LinkedHashMap<>(node));
            else if (check == Check.SKIPPED) {
                available.add(new LinkedHashMap<>(node));
                skipped.add(name);
            } else removed.add(name);
        }
        return new Result(available, removed, skipped);
    }

    private Check checkNode(Map<String, Object> node, String testUrl, int timeoutMillis) {
        String host = text(node.get("server"));
        int port = intValue(node.get("port"));
        if (host.isEmpty() || port < 1 || port > 65535) return Check.REMOVED;
        String type = text(node.get("type")).toLowerCase(Locale.ROOT);
        String effectiveTestUrl = text(testUrl);
        if (type.equals("hysteria2") || type.equals("tuic")) return Check.SKIPPED;
        try {
            if ((type.equals("http") || type.equals("https") || type.equals("socks5"))
                    && !effectiveTestUrl.isEmpty()) return probeThroughProxy(node, type, effectiveTestUrl, timeoutMillis)
                    ? Check.AVAILABLE : Check.REMOVED;
            return probeEndpoint(host, port, timeoutMillis) ? Check.AVAILABLE : Check.REMOVED;
        } catch (IOException | RuntimeException error) {
            return Check.REMOVED;
        }
    }

    private boolean probeThroughProxy(Map<String, Object> node, String type, String testUrl, int timeoutMillis)
            throws IOException {
        Proxy.Type proxyType = type.equals("socks5") ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        Proxy proxy = new Proxy(proxyType, new InetSocketAddress(text(node.get("server")), intValue(node.get("port"))));
        HttpURLConnection connection = (HttpURLConnection) new URL(testUrl).openConnection(proxy);
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "SubBoost-Android/1.0");
        String username = text(node.get("username"));
        String password = text(node.get("password"));
        if (proxyType == Proxy.Type.HTTP && !username.isEmpty()) {
            String credentials = username + ":" + password;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Proxy-Authorization", "Basic " + encoded);
        }
        try {
            int code = connection.getResponseCode();
            return code >= 200 && code < 400;
        } finally {
            connection.disconnect();
        }
    }

    private boolean probeEndpoint(String host, int port, int timeoutMillis) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private static int intValue(Object value) {
        try { return Integer.parseInt(text(value)); }
        catch (RuntimeException error) { return 0; }
    }

    private enum Check { AVAILABLE, REMOVED, SKIPPED }

    public static final class Result {
        public final List<Map<String, Object>> available;
        public final List<String> removed;
        public final List<String> skipped;

        Result(List<Map<String, Object>> available, List<String> removed, List<String> skipped) {
            this.available = available;
            this.removed = removed;
            this.skipped = skipped;
        }
    }

    public NodeConnectivityChecker() { }
}
