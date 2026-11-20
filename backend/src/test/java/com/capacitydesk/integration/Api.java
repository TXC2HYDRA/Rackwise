package com.capacitydesk.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Minimal real-HTTP client so tests exercise the full filter chain and can run truly concurrently. */
final class Api {

    record Result(int status, JsonNode body) {
        String code() {
            return body.path("code").asString();
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json;
    private final String base;

    Api(int port, JsonMapper json) {
        this.base = "http://localhost:" + port + "/api/v1";
        this.json = json;
    }

    Result get(String path, String token) {
        return call("GET", path, token, null, Map.of());
    }

    Result post(String path, String token, Object body) {
        return call("POST", path, token, body, Map.of());
    }

    Result put(String path, String token, Object body) {
        return call("PUT", path, token, body, Map.of());
    }

    Result call(String method, String path, String token, Object body, Map<String, String> headers) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(path.startsWith("http") ? path : base + path));
            if (token != null) b.header("Authorization", "Bearer " + token);
            headers.forEach(b::header);
            HttpRequest.BodyPublisher pub;
            if (body == null) {
                pub = HttpRequest.BodyPublishers.noBody();
            } else {
                b.header("Content-Type", "application/json");
                pub = HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : json.writeValueAsString(body));
            }
            HttpResponse<String> r = http.send(b.method(method, pub).build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node;
            try {
                node = r.body() == null || r.body().isBlank() ? json.createObjectNode() : json.readTree(r.body());
            } catch (RuntimeException notJson) { // e.g. Prometheus text exposition
                node = json.createObjectNode().put("raw", r.body());
            }
            return new Result(r.statusCode(), node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
