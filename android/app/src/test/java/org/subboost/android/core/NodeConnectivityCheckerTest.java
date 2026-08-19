package org.subboost.android.core;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.ServerSocket;

import static org.junit.Assert.assertEquals;

public class NodeConnectivityCheckerTest {
    @Test
    public void removesNodesWithoutAValidEndpoint() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", "无效节点");
        node.put("type", "vless");
        node.put("server", "");
        node.put("port", 443);

        NodeConnectivityChecker.Result result = new NodeConnectivityChecker().check(List.of(node), "https://www.gstatic.com/generate_204");

        assertEquals(0, result.available.size());
        assertEquals(List.of("无效节点"), result.removed);
    }

    @Test
    public void keepsUdpOnlyNodesWithoutPretendingToProbeThem() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", "Hysteria2 节点");
        node.put("type", "hysteria2");
        node.put("server", "198.51.100.1");
        node.put("port", 443);

        NodeConnectivityChecker.Result result = new NodeConnectivityChecker().check(List.of(node), "https://www.gstatic.com/generate_204");

        assertEquals(1, result.available.size());
        assertEquals(List.of("Hysteria2 节点"), result.skipped);
        assertEquals(0, result.removed.size());
    }

    @Test
    public void keepsAnEndpointThatAcceptsTcpConnections() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", "本地测试节点");
            node.put("type", "ss");
            node.put("server", "127.0.0.1");
            node.put("port", server.getLocalPort());

            NodeConnectivityChecker.Result result = new NodeConnectivityChecker().check(
                    List.of(node), "https://www.gstatic.com/generate_204", 500);

            assertEquals(1, result.available.size());
            assertEquals(0, result.removed.size());
        }
    }
}
