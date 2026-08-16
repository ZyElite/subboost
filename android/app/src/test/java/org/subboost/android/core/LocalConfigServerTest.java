package org.subboost.android.core;

import org.junit.After;
import org.junit.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalConfigServerTest {
    @After
    public void stopServer() {
        LocalConfigServer.get().stop();
    }

    @Test
    public void servesYamlOnlyWithTheAccessTokenAndSupportsUpdates() throws Exception {
        String token = LocalConfigServer.newToken();
        LocalConfigServer.get().start(0, "mixed-port: 7897\n", token);
        int port = LocalConfigServer.get().port();

        HttpURLConnection forbidden = connection("http://127.0.0.1:" + port + "/config.yaml?token=wrong");
        assertEquals(403, forbidden.getResponseCode());
        forbidden.disconnect();

        HttpURLConnection allowed = connection("http://127.0.0.1:" + port + "/config.yaml?token=" + token);
        assertEquals(200, allowed.getResponseCode());
        assertEquals("*", allowed.getHeaderField("Access-Control-Allow-Origin"));
        assertEquals("mixed-port: 7897\n", read(allowed.getInputStream()));
        allowed.disconnect();

        LocalConfigServer.get().update("mixed-port: 7898\n");
        HttpURLConnection updated = connection("http://127.0.0.1:" + port + "/config.yaml?token=" + token);
        assertEquals("mixed-port: 7898\n", read(updated.getInputStream()));
        updated.disconnect();
    }

    @Test
    public void generatedTokensAreUrlSafeAndNonTrivial() {
        String first = LocalConfigServer.newToken();
        String second = LocalConfigServer.newToken();
        assertTrue(first.matches("[A-Za-z0-9_-]{20,}"));
        assertTrue(!first.equals(second));
    }

    private HttpURLConnection connection(String value) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(value).openConnection();
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);
        return connection;
    }

    private String read(InputStream input) throws Exception {
        try (InputStream stream = input) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
