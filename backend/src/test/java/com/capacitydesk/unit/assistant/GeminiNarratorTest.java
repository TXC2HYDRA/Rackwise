package com.capacitydesk.unit.assistant;

import static com.capacitydesk.unit.assistant.Plans.group;
import static com.capacitydesk.unit.assistant.Plans.plan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capacitydesk.assistant.AssistantProperties;
import com.capacitydesk.assistant.GeminiNarrator;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Exercises the real HTTP adapter against a local fake. This does NOT prove behaviour against the live Gemini service. */
class GeminiNarratorTest {

    static final com.capacitydesk.planning.application.PlanViews.PlanView PLAN = plan(60, 500, "X", false, group("X", 55, 27_500), group("Y", 10, 5_000));
    HttpServer server;
    final AtomicReference<String> lastBody = new AtomicReference<>();
    final AtomicReference<String> lastKey = new AtomicReference<>();
    volatile int status = 200;
    volatile String reply = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" Only 55 of 60 fit. \"}]}}]}";
    volatile long delayMs = 0;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastKey.set(ex.getRequestHeaders().getFirst("x-goog-api-key"));
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { }
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            try {
                ex.sendResponseHeaders(status, out.length);
                ex.getResponseBody().write(out);
            } catch (IOException ignored) { /* client gave up (timeout test) */ }
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    GeminiNarrator narrator(Duration timeout) {
        var props = new AssistantProperties(new AssistantProperties.Llm(true, "secret-key", "http://localhost:" + server.getAddress().getPort(), "m", timeout, 0));
        return new GeminiNarrator(JsonMapper.builder().build(), props);
    }

    @Test
    void returnsTrimmedTextAndSendsKeyInHeaderNotBody() throws Exception {
        assertThat(narrator(Duration.ofSeconds(2)).narrate(PLAN)).isEqualTo("Only 55 of 60 fit.");
        assertThat(lastKey.get()).isEqualTo("secret-key");
        assertThat(lastBody.get()).doesNotContain("secret-key");
    }

    @Test
    void requestCarriesOnlyPlanFiguresAndMarksDataAsUntrusted() throws Exception {
        narrator(Duration.ofSeconds(2)).narrate(PLAN);
        String body = lastBody.get();
        assertThat(body).contains("untrusted data", "DATA:", "\\\"requestedServers\\\":60", "\\\"code\\\":\\\"X\\\"");
        assertThat(body).doesNotContain("justification", "requester", "username");
    }

    @Test
    void httpErrorThrows() {
        status = 503;
        assertThatThrownBy(() -> narrator(Duration.ofSeconds(2)).narrate(PLAN)).isInstanceOf(IOException.class).hasMessageContaining("503");
    }

    @Test
    void malformedOrEmptyBodyThrows() {
        reply = "{\"candidates\":[]}";
        assertThatThrownBy(() -> narrator(Duration.ofSeconds(2)).narrate(PLAN)).isInstanceOf(IOException.class).hasMessageContaining("no text");
        reply = "not json";
        assertThatThrownBy(() -> narrator(Duration.ofSeconds(2)).narrate(PLAN)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void slowServerTimesOut() {
        delayMs = 1500;
        assertThatThrownBy(() -> narrator(Duration.ofMillis(300)).narrate(PLAN)).isInstanceOf(java.net.http.HttpTimeoutException.class);
    }

    @Test
    void missingApiKeyFailsFast() {
        var props = new AssistantProperties(new AssistantProperties.Llm(true, "", "http://x", "m", Duration.ofSeconds(1), 0));
        assertThatThrownBy(() -> new GeminiNarrator(JsonMapper.builder().build(), props)).isInstanceOf(IllegalStateException.class);
    }
}
