package org.subboost.android.core;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WifiCallingAnalyzerTest {
    @Test
    public void reportsUdpEnabledNodeAsReadyButKeepsPortProbeExplicit() {
        Map<String, Object> node = node("ss");
        node.put("udp", true);

        WifiCallingAnalyzer.Analysis result = WifiCallingAnalyzer.analyze(node);

        assertEquals(WifiCallingAnalyzer.Status.READY, result.status);
        assertTrue(result.message.contains("500/4500"));
        assertTrue(result.message.contains("实测"));
    }

    @Test
    public void rejectsProtocolsOrSettingsWithoutUdp() {
        assertEquals(WifiCallingAnalyzer.Status.UNSUPPORTED,
                WifiCallingAnalyzer.analyze(node("http")).status);
        Map<String, Object> disabled = node("vmess");
        disabled.put("udp", false);
        assertEquals(WifiCallingAnalyzer.Status.UNSUPPORTED,
                WifiCallingAnalyzer.analyze(disabled).status);
    }

    @Test
    public void recognizesNativeUdpAndKeepsMissingUdpUnknown() {
        assertEquals(WifiCallingAnalyzer.Status.READY,
                WifiCallingAnalyzer.analyze(node("hysteria2")).status);
        assertEquals(WifiCallingAnalyzer.Status.UNKNOWN,
                WifiCallingAnalyzer.analyze(node("socks5")).status);
    }

    private static Map<String, Object> node(String type) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", "测试节点");
        node.put("type", type);
        return node;
    }
}
