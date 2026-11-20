package com.capacitydesk.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capacitydesk.identity.domain.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class AuditIT extends IntegrationTestBase {

    @Test
    void inventoryWritesAreAuditedWithActorAndBeforeAfterState() {
        TestUser admin = user(Role.ADMIN);
        Fixture f = fixture(10, 5_000, 0);
        String corr = "corr-" + unique();
        api.call("PUT", "/inventory/rack-groups/" + f.rgId() + "/limits", admin.token(), Map.of("slotCapacity", 12, "powerCapacityWatts", 6_000), Map.of("X-Correlation-Id", corr));
        JsonNode e = api.get("/audit-events?action=RACK_GROUP_LIMITS_CHANGED&targetId=" + f.rgId(), admin.token()).body().path("content").get(0);
        assertThat(e.path("actorId").asString()).isEqualTo(admin.id().toString());
        assertThat(e.path("correlationId").asString()).isEqualTo(corr);
        assertThat(e.path("before").asString()).contains("\"slots\":10");
        assertThat(e.path("after").asString()).contains("\"slots\":12");
        assertThat(e.path("result").asString()).isEqualTo("SUCCESS");
    }

    @Test
    void correlationIdIsEchoedAndMalformedOnesAreReplaced() {
        TestUser u = user(Role.REQUESTER);
        var ok = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/api/v1/auth/me"))
                .header("Authorization", "Bearer " + u.token()).header("X-Correlation-Id", "abc-12345678").build();
        var bad = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/api/v1/auth/me"))
                .header("Authorization", "Bearer " + u.token()).header("X-Correlation-Id", "x y\"<script>").build();
        try {
            var client = java.net.http.HttpClient.newHttpClient();
            assertThat(client.send(ok, java.net.http.HttpResponse.BodyHandlers.discarding()).headers().firstValue("X-Correlation-Id")).contains("abc-12345678");
            String replaced = client.send(bad, java.net.http.HttpResponse.BodyHandlers.discarding()).headers().firstValue("X-Correlation-Id").orElseThrow();
            assertThat(replaced).matches("^[0-9a-f-]{36}$");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void auditRowsCannotBeUpdatedOrDeleted() {
        TestUser admin = user(Role.ADMIN);
        fixture(10, 5_000, 0);
        assertThatThrownBy(() -> jdbc.update("update audit_events set result = 'FAILURE'")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_events")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("truncate audit_events")).hasMessageContaining("append-only");
        assertThat(api.get("/audit-events", admin.token()).status()).isEqualTo(200);
    }

    @Test
    void reviewersSeeOnlyRequestAndPlanEventsRequestersNothing() {
        TestUser admin = user(Role.ADMIN);
        TestUser reviewer = user(Role.REVIEWER);
        TestUser requester = user(Role.REQUESTER);
        Fixture f = fixture(10, 5_000, 0);
        plan(requester, createRequest(requester, f, 2));
        JsonNode adminView = api.get("/audit-events?size=100", admin.token()).body();
        JsonNode reviewerView = api.get("/audit-events?size=100", reviewer.token()).body();
        assertThat(adminView.toString()).contains("RACK_GROUP_CREATED", "LOGIN");
        assertThat(reviewerView.toString()).contains("PLAN_GENERATED").doesNotContain("RACK_GROUP_CREATED").doesNotContain("\"LOGIN\"");
        assertThat(api.get("/audit-events", requester.token()).status()).isEqualTo(403);
        assertThat(api.get("/audit-events?size=1000", admin.token()).status()).isEqualTo(400);
        assertThat(api.get("/audit-events?action=NOPE", admin.token()).status()).isEqualTo(400);
    }
}
