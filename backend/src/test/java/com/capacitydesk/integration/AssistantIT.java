package com.capacitydesk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.capacitydesk.identity.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class AssistantIT extends IntegrationTestBase {

    TestUser alice, mallory, bob;
    Fixture x;

    @BeforeEach
    void setUp() {
        alice = user(Role.REQUESTER);
        mallory = user(Role.REQUESTER);
        bob = user(Role.REVIEWER);
        x = fixture(100, 50_000, 45); // 55 free slots, 27.5 kW free
    }

    @Test
    void ownerGetsLocalAdviceForPartialPlan() {
        JsonNode plan = plan(alice, createRequest(alice, x, 60));
        Api.Result r = api.get("/plans/" + plan.path("id").asString() + "/advice", alice.token());
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().path("source").asString()).isEqualTo("LOCAL");
        assertThat(r.body().path("narrative").asString()).contains("Only 55 of 60");
        assertThat(r.body().path("suggestions").toString()).contains("REDUCE_REQUEST");
        assertThat(r.body().path("disclaimer").asString()).containsIgnoringCase("advisory");
    }

    @Test
    void reviewerCanReadAdvice() {
        JsonNode plan = plan(alice, createRequest(alice, x, 10));
        assertThat(api.get("/plans/" + plan.path("id").asString() + "/advice", bob.token()).status()).isEqualTo(200);
    }

    @Test
    void otherRequesterGets404AndAnonymousGets401() {
        JsonNode plan = plan(alice, createRequest(alice, x, 10));
        String path = "/plans/" + plan.path("id").asString() + "/advice";
        assertThat(api.get(path, mallory.token()).status()).isEqualTo(404);
        assertThat(api.get(path, null).status()).isEqualTo(401);
    }

    @Test
    void askingForAdviceDoesNotChangePlanOrInventory() {
        JsonNode plan = plan(alice, createRequest(alice, x, 10));
        String id = plan.path("id").asString();
        api.get("/plans/" + id + "/advice", alice.token());
        JsonNode after = api.get("/plans/" + id, alice.token()).body();
        assertThat(after.path("status").asString()).isEqualTo("PROPOSED");
        assertThat(after.path("contentHash").asString()).isEqualTo(plan.path("contentHash").asString());
        assertThat(used(alice, x)).isEqualTo(45);
    }

    @Test
    void unknownPlanIs404() {
        assertThat(api.get("/plans/" + java.util.UUID.randomUUID() + "/advice", alice.token()).status()).isEqualTo(404);
    }
}
