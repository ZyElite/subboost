package org.subboost.android.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Static WiFi Calling prerequisites analysis; it does not probe a proxy exit. */
public final class WifiCallingAnalyzer {
    private static final Set<String> NO_UDP_PROTOCOLS = new HashSet<>(Arrays.asList("http", "https", "ssh"));
    private static final Set<String> NATIVE_UDP_PROTOCOLS = new HashSet<>(Arrays.asList(
            "hysteria", "hysteria2", "tuic", "wireguard"));

    public static Analysis analyze(Map<String, Object> node) {
        String type = String.valueOf(node.getOrDefault("type", "")).trim().toLowerCase(Locale.ROOT);
        if (NO_UDP_PROTOCOLS.contains(type)) {
            return new Analysis(Status.UNSUPPORTED, "不支持：" + type.toUpperCase(Locale.ROOT) + " 不能转发 UDP");
        }
        Object udp = node.get("udp");
        if (udp != null && !truthy(udp)) {
            return new Analysis(Status.UNSUPPORTED, "不支持：节点已关闭 UDP");
        }
        if (NATIVE_UDP_PROTOCOLS.contains(type) || truthy(udp)) {
            return new Analysis(Status.READY, "可尝试：UDP 可用，需实测 500/4500");
        }
        return new Analysis(Status.UNKNOWN, "待确认：未声明 UDP，需开放 500/4500");
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String text = String.valueOf(value).trim();
        return text.equalsIgnoreCase("true") || text.equals("1") || text.equalsIgnoreCase("yes");
    }

    public enum Status { READY, UNKNOWN, UNSUPPORTED }

    public static final class Analysis {
        public final Status status;
        public final String message;

        private Analysis(Status status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private WifiCallingAnalyzer() { }
}
