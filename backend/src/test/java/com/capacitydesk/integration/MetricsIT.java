package com.capacitydesk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.capacitydesk.identity.domain.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "capacitydesk.metrics.scrape-token=test-scrape-token-0123456789")
class MetricsIT extends IntegrationTestBase {

    static final String SCRAPE = "test-scrape-token-0123456789";

    String scrape(String token) {
        Api.Result r = api.call("GET", "http://localhost:" + port + "/actuator/prometheus", token, null, Map.of());
        assertThat(r.status()).isEqualTo(200);
        return r.body().path("raw").asString();
    }

    @Test
    void scrapeTokenReadsPrometheusOnly() {
        assertThat(scrape(SCRAPE)).contains("jvm_memory_used_bytes");
        String base = "http://localhost:" + port;
        assertThat(api.call("GET", base + "/actuator/prometheus", null, null, Map.of()).status()).isEqualTo(401);
        assertThat(api.call("GET", base + "/actuator/prometheus", "wrong", null, Map.of()).status()).isEqualTo(401);
        assertThat(api.call("GET", base + "/actuator/env", SCRAPE, null, Map.of()).status()).isEqualTo(401);
        assertThat(api.call("GET", base + "/api/v1/inventory/capacity", SCRAPE, null, Map.of()).status()).isEqualTo(401);
        assertThat(api.call("GET", base + "/actuator/prometheus/x", SCRAPE, null, Map.of()).status()).isIn(401, 403, 404);
    }

    @Test
    void ordinaryUsersCannotScrape() {
        TestUser reviewer = user(Role.REVIEWER);
        Api.Result r = api.call("GET", "http://localhost:" + port + "/actuator/prometheus", reviewer.token(), null, Map.of());
        assertThat(r.status()).isEqualTo(403);
    }

    @Test
    void planLifecycleAndCapacityAreMeasured() {
        TestUser alice = user(Role.REQUESTER), bob = user(Role.REVIEWER);
        Fixture f = fixture(100, 50_000, 45);
        var plan = plan(alice, createRequest(alice, f, 10));
        approve(bob, plan);
        assertThat(apply(bob, plan).status()).isEqualTo(200);
        TestUser mallory = user(Role.REVIEWER, Role.REQUESTER);
        var own = plan(mallory, createRequest(mallory, f, 1));
        assertThat(approve(mallory, own).status()).isEqualTo(403); // self-approval denied

        String text = scrape(SCRAPE);
        assertThat(text).containsPattern("rackwise_audit_events_total\\{action=\"PLAN_GENERATED\",result=\"SUCCESS\"\\} [1-9]");
        assertThat(text).containsPattern("rackwise_audit_events_total\\{action=\"PLAN_APPROVED\",result=\"SUCCESS\"\\} [1-9]");
        assertThat(text).containsPattern("rackwise_audit_events_total\\{action=\"PLAN_APPLIED\",result=\"SUCCESS\"\\} [1-9]");
        assertThat(text).containsPattern("rackwise_audit_events_total\\{action=\"PLAN_APPROVED\",result=\"DENIED\"\\} [1-9]");
        assertThat(text).contains("rackwise_capacity_slots_used", "rackwise_capacity_slots_limit", "rackwise_capacity_power_used_watts", "rackwise_capacity_power_limit_watts");
        assertThat(text).contains("http_server_requests_seconds_bucket");
    }

    @Test
    void assistantAdviceIsCounted() {
        TestUser alice = user(Role.REQUESTER);
        Fixture f = fixture(100, 50_000, 45);
        var plan = plan(alice, createRequest(alice, f, 10));
        api.get("/plans/" + plan.path("id").asString() + "/advice", alice.token());
        assertThat(scrape(SCRAPE)).containsPattern("assistant_advice_total\\{source=\"LOCAL\"\\} [1-9]");
    }
}
