package org.subboost.android.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Offline subscription parser for the formats most commonly consumed by Mihomo. */
public final class SubscriptionParser {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();
    private static final Set<String> SUPPORTED_SCHEMES = new HashSet<>(Arrays.asList(
            "ss", "ssr", "vmess", "vless", "trojan", "hysteria2", "hy2", "tuic",
            "http", "https", "socks", "socks5"));

    public ParseResult parse(String rawContent) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String content = stripBom(rawContent == null ? "" : rawContent).trim();
        if (content.isEmpty()) return new ParseResult(nodes, errors);

        List<Map<String, Object>> yamlNodes = parseClashYaml(content, errors);
        if (yamlNodes != null) {
            return new ParseResult(uniqueNames(yamlNodes), errors);
        }

        String candidate = content;
        if (!containsSupportedLink(candidate)) {
            String decoded = tryBase64(candidate.replaceAll("\\s+", ""));
            if (decoded != null && containsSupportedLink(decoded)) candidate = decoded;
        }

        int lineNumber = 0;
        for (String rawLine : candidate.split("\\R")) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                nodes.add(parseLink(line));
            } catch (RuntimeException error) {
                errors.add("第 " + lineNumber + " 行：" + readableMessage(error));
            }
        }
        if (nodes.isEmpty() && errors.isEmpty()) errors.add("未识别到支持的节点或 Clash YAML");
        return new ParseResult(uniqueNames(nodes), errors);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseClashYaml(String content, List<String> errors) {
        if (!content.contains("proxies:") && !content.contains("proxy-providers:")) return null;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(20);
            options.setCodePointLimit(8 * 1024 * 1024);
            Object loaded = new Yaml(new SafeConstructor(options)).load(content);
            if (!(loaded instanceof Map<?, ?> root)) return null;
            Object proxies = root.get("proxies");
            if (!(proxies instanceof List<?> list)) return null;

            List<Map<String, Object>> result = new ArrayList<>();
            int index = 0;
            for (Object value : list) {
                index++;
                if (!(value instanceof Map<?, ?> input)) {
                    errors.add("YAML proxies 第 " + index + " 项不是对象，已忽略");
                    continue;
                }
                Map<String, Object> node = stringKeyMap(input);
                if (isValidNode(node)) result.add(node);
                else errors.add("YAML proxies 第 " + index + " 项缺少 name/type/server/port，已忽略");
            }
            return result;
        } catch (RuntimeException error) {
            errors.add("YAML 解析失败：" + readableMessage(error));
            return null;
        }
    }

    private Map<String, Object> parseLink(String link) {
        int schemeEnd = link.indexOf("://");
        if (schemeEnd < 1) throw new IllegalArgumentException("不是节点链接");
        String scheme = link.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_SCHEMES.contains(scheme)) throw new IllegalArgumentException("暂不支持 " + scheme + " 协议");
        return switch (scheme) {
            case "ss" -> parseSs(link);
            case "ssr" -> parseSsr(link);
            case "vmess" -> parseVmess(link);
            case "vless" -> parseVless(link);
            case "trojan" -> parseTrojan(link);
            case "hysteria2", "hy2" -> parseHysteria2(link);
            case "tuic" -> parseTuic(link);
            case "http", "https", "socks", "socks5" -> parseSimpleProxy(link, scheme);
            default -> throw new IllegalArgumentException("暂不支持的协议");
        };
    }

    private Map<String, Object> parseSs(String link) {
        String raw = link.substring(5);
        String name = fragmentName(raw, "SS 节点");
        raw = withoutFragment(raw);
        String query = queryPart(raw);
        raw = withoutQuery(raw).replaceAll("/+$", "");

        String methodPassword;
        String endpoint;
        int at = raw.lastIndexOf('@');
        if (at >= 0) {
            String user = decodePercent(raw.substring(0, at));
            methodPassword = user.contains(":") ? user : requireBase64(user, "SS 用户信息无法解码");
            endpoint = raw.substring(at + 1);
        } else {
            String decoded = requireBase64(decodePercent(raw), "SS 链接无法解码");
            int decodedAt = decoded.lastIndexOf('@');
            if (decodedAt < 0) throw new IllegalArgumentException("SS 链接缺少服务器地址");
            methodPassword = decoded.substring(0, decodedAt);
            endpoint = decoded.substring(decodedAt + 1);
        }
        int colon = methodPassword.indexOf(':');
        if (colon < 1) throw new IllegalArgumentException("SS 链接缺少加密方式或密码");
        HostPort hp = parseHostPort(endpoint, 0);

        Map<String, Object> node = baseNode(name, "ss", hp);
        node.put("cipher", methodPassword.substring(0, colon));
        node.put("password", methodPassword.substring(colon + 1));
        Map<String, String> params = parseQuery(query);
        String pluginValue = params.get("plugin");
        if (!isBlank(pluginValue)) {
            String[] pieces = pluginValue.split(";");
            String plugin = pieces[0].trim();
            Map<String, Object> opts = new LinkedHashMap<>();
            for (int i = 1; i < pieces.length; i++) {
                int eq = pieces[i].indexOf('=');
                if (eq > 0) opts.put(pieces[i].substring(0, eq), pieces[i].substring(eq + 1));
            }
            if (plugin.equals("obfs-local") || plugin.equals("simple-obfs")) {
                plugin = "obfs";
                renameKey(opts, "obfs", "mode");
                renameKey(opts, "obfs-host", "host");
            }
            node.put("plugin", plugin);
            if (!opts.isEmpty()) node.put("plugin-opts", opts);
        }
        node.put("udp", true);
        return node;
    }

    private Map<String, Object> parseSsr(String link) {
        String decoded = requireBase64(link.substring(6), "SSR 链接无法解码");
        String[] sections = decoded.split("/\\?", 2);
        String[] fields = sections[0].split(":", 6);
        if (fields.length != 6) throw new IllegalArgumentException("SSR 基础字段不完整");
        Map<String, String> params = sections.length == 2 ? parseQuery(sections[1]) : Collections.emptyMap();
        HostPort hp = new HostPort(fields[0], validPort(fields[1]));
        String remarks = params.containsKey("remarks") ? tryBase64(params.get("remarks")) : null;
        Map<String, Object> node = baseNode(nonBlank(remarks, "SSR 节点"), "ssr", hp);
        node.put("protocol", fields[2]);
        node.put("cipher", fields[3]);
        node.put("obfs", fields[4]);
        node.put("password", requireBase64(fields[5], "SSR 密码无法解码"));
        putDecoded(node, "protocol-param", params.get("protoparam"));
        putDecoded(node, "obfs-param", params.get("obfsparam"));
        node.put("udp", true);
        return node;
    }

    private Map<String, Object> parseVmess(String link) {
        String payload = withoutFragment(link.substring(8));
        String json = requireBase64(payload, "VMess 链接无法解码");
        Map<String, Object> input;
        try {
            input = GSON.fromJson(json, MAP_TYPE);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("VMess JSON 无效");
        }
        String server = text(input, "add");
        int port = validPort(text(input, "port"));
        String uuid = text(input, "id");
        if (isBlank(server) || isBlank(uuid)) throw new IllegalArgumentException("VMess 缺少服务器或 UUID");
        Map<String, Object> node = baseNode(nonBlank(text(input, "ps"), "VMess 节点"), "vmess", new HostPort(server, port));
        node.put("uuid", uuid);
        node.put("alterId", intValue(input.get("aid"), 0));
        node.put("cipher", nonBlank(text(input, "scy"), "auto"));
        node.put("udp", true);
        String network = nonBlank(text(input, "net"), "tcp");
        node.put("network", network);
        boolean tls = !isBlank(text(input, "tls")) && !text(input, "tls").equalsIgnoreCase("none");
        if (tls) node.put("tls", true);
        putIfText(node, "servername", nonBlank(text(input, "sni"), text(input, "host")));
        putIfText(node, "client-fingerprint", text(input, "fp"));
        putList(node, "alpn", text(input, "alpn"));
        String path = text(input, "path");
        String host = text(input, "host");
        if (network.equals("ws")) node.put("ws-opts", transportOptions(path, host, false));
        if (network.equals("grpc")) node.put("grpc-opts", Collections.singletonMap("grpc-service-name", path));
        return node;
    }

    private Map<String, Object> parseVless(String link) {
        ParsedUri uri = parsedUri(link);
        Map<String, String> p = uri.params;
        Map<String, Object> node = baseNode(uri.name("VLESS 节点"), "vless", uri.hostPort);
        node.put("uuid", uri.username);
        node.put("udp", true);
        String security = p.getOrDefault("security", "");
        if (!isBlank(security) && !security.equals("none")) node.put("tls", true);
        putIfText(node, "servername", first(p, "sni", "serverName", "peer"));
        putIfText(node, "client-fingerprint", first(p, "fp", "fingerprint"));
        putIfText(node, "flow", p.get("flow"));
        putIfText(node, "packet-encoding", first(p, "packetEncoding", "packet-encoding"));
        putList(node, "alpn", p.get("alpn"));
        if (bool(p.get("allowInsecure")) || bool(p.get("insecure"))) node.put("skip-cert-verify", true);
        String network = nonBlank(first(p, "type", "network"), "tcp");
        node.put("network", network);
        addTransport(node, network, p);
        if (security.equalsIgnoreCase("reality")) {
            Map<String, Object> reality = new LinkedHashMap<>();
            putIfText(reality, "public-key", first(p, "pbk", "publicKey"));
            putIfText(reality, "short-id", first(p, "sid", "shortId"));
            if (!reality.isEmpty()) node.put("reality-opts", reality);
        }
        return node;
    }

    private Map<String, Object> parseTrojan(String link) {
        ParsedUri uri = parsedUri(link);
        Map<String, String> p = uri.params;
        Map<String, Object> node = baseNode(uri.name("Trojan 节点"), "trojan", uri.hostPort);
        node.put("password", uri.username);
        node.put("udp", true);
        putIfText(node, "sni", first(p, "sni", "peer", "serverName"));
        putList(node, "alpn", p.get("alpn"));
        if (bool(first(p, "allowInsecure", "insecure"))) node.put("skip-cert-verify", true);
        String network = nonBlank(first(p, "type", "network"), "tcp");
        if (!network.equals("tcp")) {
            node.put("network", network);
            addTransport(node, network, p);
        }
        return node;
    }

    private Map<String, Object> parseHysteria2(String link) {
        ParsedUri uri = parsedUri(link);
        Map<String, String> p = uri.params;
        Map<String, Object> node = baseNode(uri.name("Hysteria2 节点"), "hysteria2", uri.hostPort);
        node.put("password", uri.username);
        putIfText(node, "sni", first(p, "sni", "peer"));
        putList(node, "alpn", p.get("alpn"));
        if (bool(first(p, "insecure", "allowInsecure"))) node.put("skip-cert-verify", true);
        putIfText(node, "obfs", p.get("obfs"));
        putIfText(node, "obfs-password", first(p, "obfs-password", "obfsPassword"));
        return node;
    }

    private Map<String, Object> parseTuic(String link) {
        ParsedUri uri = parsedUri(link);
        Map<String, String> p = uri.params;
        String[] credentials = uri.username.split(":", 2);
        if (credentials.length < 2) throw new IllegalArgumentException("TUIC 缺少 UUID 或密码");
        Map<String, Object> node = baseNode(uri.name("TUIC 节点"), "tuic", uri.hostPort);
        node.put("uuid", credentials[0]);
        node.put("password", credentials[1]);
        putIfText(node, "sni", first(p, "sni", "peer"));
        putList(node, "alpn", p.get("alpn"));
        putIfText(node, "congestion-controller", first(p, "congestion_control", "congestion-controller"));
        putIfText(node, "udp-relay-mode", first(p, "udp_relay_mode", "udp-relay-mode"));
        if (bool(first(p, "allow_insecure", "insecure"))) node.put("skip-cert-verify", true);
        return node;
    }

    private Map<String, Object> parseSimpleProxy(String link, String scheme) {
        ParsedUri uri = parsedUri(link);
        String type = scheme.startsWith("socks") ? "socks5" : scheme;
        Map<String, Object> node = baseNode(uri.name(type.toUpperCase(Locale.ROOT) + " 节点"), type, uri.hostPort);
        String[] credentials = uri.username.split(":", 2);
        if (!isBlank(credentials[0])) node.put("username", credentials[0]);
        if (credentials.length > 1) node.put("password", credentials[1]);
        if (scheme.equals("https")) node.put("tls", true);
        return node;
    }

    private ParsedUri parsedUri(String link) {
        try {
            int schemeEnd = link.indexOf("://");
            URI uri = URI.create("https" + link.substring(schemeEnd));
            String userInfo = uri.getRawUserInfo() == null ? "" : decodePercent(uri.getRawUserInfo());
            String host = uri.getHost();
            if (isBlank(host)) throw new IllegalArgumentException("节点缺少服务器地址");
            int port = uri.getPort() > 0 ? uri.getPort() : 443;
            return new ParsedUri(userInfo, new HostPort(host, port), parseQuery(uri.getRawQuery()),
                    uri.getRawFragment() == null ? "" : decodePercent(uri.getRawFragment()));
        } catch (IllegalArgumentException error) {
            if (error.getMessage() != null && error.getMessage().startsWith("节点")) throw error;
            throw new IllegalArgumentException("链接格式无效");
        }
    }

    private void addTransport(Map<String, Object> node, String network, Map<String, String> p) {
        String path = first(p, "path", "serviceName", "service_name");
        String host = first(p, "host", "authority");
        if (network.equals("ws")) node.put("ws-opts", transportOptions(path, host, false));
        else if (network.equals("grpc")) node.put("grpc-opts", Collections.singletonMap("grpc-service-name", nonBlank(path, "")));
        else if (network.equals("h2") || network.equals("http")) node.put("h2-opts", transportOptions(path, host, true));
    }

    private Map<String, Object> transportOptions(String path, String host, boolean hostAsList) {
        Map<String, Object> opts = new LinkedHashMap<>();
        if (!isBlank(path)) opts.put("path", path);
        if (!isBlank(host)) {
            if (hostAsList) opts.put("host", Collections.singletonList(host));
            else opts.put("headers", Collections.singletonMap("Host", host));
        }
        return opts;
    }

    private static Map<String, Object> baseNode(String name, String type, HostPort hp) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", nonBlank(name, type.toUpperCase(Locale.ROOT) + " 节点"));
        node.put("type", type);
        node.put("server", hp.host);
        node.put("port", hp.port);
        return node;
    }

    private static List<Map<String, Object>> uniqueNames(List<Map<String, Object>> nodes) {
        Set<String> used = new LinkedHashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : nodes) {
            Map<String, Object> node = new LinkedHashMap<>(source);
            String base = nonBlank(String.valueOf(node.getOrDefault("name", "")), "未命名节点");
            String name = base;
            int suffix = 2;
            while (!used.add(name)) name = base + " (" + suffix++ + ")";
            node.put("name", name);
            result.add(node);
        }
        return result;
    }

    private static boolean isValidNode(Map<String, Object> node) {
        return !isBlank(text(node, "name")) && !isBlank(text(node, "type"))
                && !isBlank(text(node, "server")) && intValue(node.get("port"), 0) > 0;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> input) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getKey() != null) out.put(String.valueOf(entry.getKey()), normalizeYamlValue(entry.getValue()));
        }
        return out;
    }

    private static Object normalizeYamlValue(Object value) {
        if (value instanceof Map<?, ?> map) return stringKeyMap(map);
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) result.add(normalizeYamlValue(item));
            return result;
        }
        return value;
    }

    private static HostPort parseHostPort(String raw, int defaultPort) {
        String value = raw.replaceAll("/+$", "");
        String host;
        String portText;
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 0) throw new IllegalArgumentException("IPv6 地址格式无效");
            host = value.substring(1, end);
            portText = value.substring(end + 1).replaceFirst("^:", "");
        } else {
            int colon = value.lastIndexOf(':');
            if (colon < 1) {
                if (defaultPort <= 0) throw new IllegalArgumentException("节点缺少端口");
                return new HostPort(value, defaultPort);
            }
            host = value.substring(0, colon);
            portText = value.substring(colon + 1);
        }
        return new HostPort(host, validPort(portText));
    }

    private static int validPort(String value) {
        try {
            int port = Integer.parseInt(value.replace(".0", ""));
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("端口无效");
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (isBlank(rawQuery)) return values;
        for (String item : rawQuery.split("&")) {
            String[] pair = item.split("=", 2);
            values.put(decodePercent(pair[0]), pair.length == 2 ? decodePercent(pair[1]) : "");
        }
        return values;
    }

    private static String queryPart(String raw) {
        int query = raw.indexOf('?');
        return query < 0 ? "" : raw.substring(query + 1);
    }

    private static String withoutQuery(String raw) {
        int query = raw.indexOf('?');
        return query < 0 ? raw : raw.substring(0, query);
    }

    private static String fragmentName(String raw, String fallback) {
        int hash = raw.indexOf('#');
        return hash < 0 ? fallback : nonBlank(decodePercent(raw.substring(hash + 1)), fallback);
    }

    private static String withoutFragment(String raw) {
        int hash = raw.indexOf('#');
        return hash < 0 ? raw : raw.substring(0, hash);
    }

    private static boolean containsSupportedLink(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        for (String scheme : SUPPORTED_SCHEMES) if (lower.contains(scheme + "://")) return true;
        return false;
    }

    private static String requireBase64(String value, String message) {
        String decoded = tryBase64(value);
        if (decoded == null) throw new IllegalArgumentException(message);
        return decoded;
    }

    private static String tryBase64(String value) {
        if (isBlank(value)) return null;
        String normalized = value.trim().replace('-', '+').replace('_', '/').replaceAll("\\s+", "");
        int padding = (4 - normalized.length() % 4) % 4;
        if (padding == 1) normalized += "=";
        else if (padding == 2) normalized += "==";
        else if (padding == 3) normalized += "===";
        try {
            return new String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static String decodePercent(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            return value;
        }
    }

    private static void putDecoded(Map<String, Object> node, String key, String encoded) {
        String value = tryBase64(encoded);
        if (!isBlank(value)) node.put(key, value);
    }

    private static void renameKey(Map<String, Object> map, String from, String to) {
        if (map.containsKey(from)) map.put(to, map.remove(from));
    }

    private static void putIfText(Map<String, Object> map, String key, String value) {
        if (!isBlank(value)) map.put(key, value);
    }

    private static void putList(Map<String, Object> map, String key, String value) {
        if (isBlank(value)) return;
        List<String> values = new ArrayList<>();
        for (String item : value.split(",")) if (!item.trim().isEmpty()) values.add(item.trim());
        if (!values.isEmpty()) map.put(key, values);
    }

    private static String first(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String value = map.get(key);
            if (!isBlank(value)) return value;
        }
        return "";
    }

    private static boolean bool(String value) {
        if (value == null) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
    }

    private static String text(Map<String, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) return "";
        if (value instanceof Double number && number % 1 == 0) return String.valueOf(number.intValue());
        return String.valueOf(value).trim();
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException error) { return fallback; }
    }

    private static String nonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private static String readableMessage(Throwable error) {
        String value = error.getMessage();
        if (isBlank(value)) return error.getClass().getSimpleName();
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline);
    }

    private record HostPort(String host, int port) { }

    private record ParsedUri(String username, HostPort hostPort, Map<String, String> params, String fragment) {
        String name(String fallback) { return nonBlank(fragment, fallback); }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
