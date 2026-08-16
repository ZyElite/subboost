package org.subboost.android.core;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SubscriptionParserTest {
    private final SubscriptionParser parser = new SubscriptionParser();

    @Test
    public void parsesClashYamlAndKeepsExtraFields() {
        ParseResult result = parser.parse("""
                proxies:
                  - name: Hong Kong
                    type: ss
                    server: hk.example.com
                    port: 443
                    cipher: aes-128-gcm
                    password: secret
                    udp: true
                """);

        assertEquals(1, result.nodes().size());
        assertTrue(result.errors().isEmpty());
        assertEquals("Hong Kong", result.nodes().get(0).get("name"));
        assertEquals(true, result.nodes().get(0).get("udp"));
    }

    @Test
    public void decodesBase64SubscriptionAndMakesNamesUnique() {
        String links = "vless://first@example.com:443?security=tls&type=ws&host=edge.example.com&path=%2Fws#Node\n"
                + "trojan://password@example.net:443?sni=example.net#Node";
        String encoded = Base64.getEncoder().withoutPadding().encodeToString(links.getBytes(StandardCharsets.UTF_8));

        ParseResult result = parser.parse(encoded);

        assertEquals(2, result.nodes().size());
        assertEquals("Node", result.nodes().get(0).get("name"));
        assertEquals("Node (2)", result.nodes().get(1).get("name"));
        assertEquals("ws", result.nodes().get(0).get("network"));
    }

    @Test
    public void parsesVmessJson() {
        String json = "{\"v\":\"2\",\"ps\":\"Tokyo\",\"add\":\"jp.example.com\","
                + "\"port\":\"443\",\"id\":\"00000000-0000-0000-0000-000000000001\","
                + "\"aid\":\"0\",\"net\":\"ws\",\"host\":\"cdn.example.com\",\"path\":\"/ws\",\"tls\":\"tls\"}";
        String link = "vmess://" + Base64.getEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        ParseResult result = parser.parse(link);

        assertEquals(1, result.nodes().size());
        assertEquals("Tokyo", result.nodes().get(0).get("name"));
        assertEquals(true, result.nodes().get(0).get("tls"));
    }

    @Test
    public void reportsBadLinesWithoutDiscardingGoodNodes() {
        ParseResult result = parser.parse("not-a-link\nss://YWVzLTEyOC1nY206cGFzcw@example.com:8388#Good");

        assertEquals(1, result.nodes().size());
        assertEquals(1, result.errors().size());
    }
}
