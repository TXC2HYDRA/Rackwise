package com.capacitydesk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.capacitydesk.identity.domain.Role;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The central acceptance scenario (40 servers into Rack Group X) and its negative cases. */
class WorkflowIT extends IntegrationTestBase {

    TestUser alice, bob, carol, admin;
    Fixture x; // 100 slots, 50 kW, 45 existing servers => 55 free slots, 27.5 kW free

    @BeforeEach
    void setUp() {
        alice = user(Role.REQUESTER);
        bob = user(Role.REVIEWER);
        carol = user(Role.REVIEWER);
        admin = user(Role.ADMIN);
        x = fixture(100, 50_000, 45);
    }

    @Test
    void infrastructureExpansionEndToEnd() {
        assertThat(used(alice, x)).isEqualTo(45);

        JsonNode request = createRequest(alice, x, 40);
        assertThat(request.path("status").asString()).isEqualTo("SUBMITTED");

        JsonNode plan = plan(alice, request);
        assertThat(plan.path("feasibility").asString()).isEqualTo("FEASIBLE");
        assertThat(plan.path("allocatedServers").asInt()).isEqualTo(40);
        assertThat(plan.path("status").asString()).isEqualTo("PROPOSED");
        assertThat(plan.path("allocations").get(0).path("serverCount").asInt()).isEqualTo(40);
        assertThat(api.get("/capacity-requests/" + request.path("id").asString(), alice.token()).body().path("status").asString()).isEqualTo("PLANNED");

        assertThat(api.get("/approvals/pending?size=100", bob.token()).body().toString()).contains(plan.path("id").asString());

        Api.Result approved = approve(bob, plan);
        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.body().path("status").asString()).isEqualTo("APPROVED");

        Api.Result applied = apply(bob, plan);
        assertThat(applied.status()).isEqualTo(200);
        assertThat(applied.body().path("serversCreated").asInt()).isEqualTo(40);
        assertThat(applied.body().path("plan").path("status").asString()).isEqualTo("APPLIED");

        JsonNode rg = rackGroup(alice, x);
        assertThat(rg.path("slotsUsed").asInt()).isEqualTo(85);
        assertThat(rg.path("powerUsedWatts").asInt()).isEqualTo(42_500);
        assertThat(jdbc.queryForObject("select count(*) from servers where plan_id = ?::uuid", Integer.class, plan.path("id").asString())).isEqualTo(40);
        assertThat(api.get("/capacity-requests/" + request.path("id").asString(), alice.token()).body().path("status").asString()).isEqualTo("FULFILLED");

        // audit trail for the plan lifecycle, with correlation ids
        JsonNode events = api.get("/audit-events?targetType=PLAN&targetId=" + plan.path("id").asString(), admin.token()).body().path("content");
        assertThat(events.toString()).contains("PLAN_GENERATED", "PLAN_APPROVED", "PLAN_APPLIED");
        for (JsonNode e : events) {
            assertThat(e.path("correlationId").asString()).isNotBlank();
        }
        assertThat(api.get("/approvals/history?size=100", bob.token()).body().toString()).contains(plan.path("id").asString());
    }

    @Test
    void unapprovedPlanCannotBeApplied() {
        JsonNode plan = plan(alice, createRequest(alice, x, 10));
        Api.Result r = apply(bob, plan);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("PLAN_NOT_APPROVED");
        assertThat(used(alice, x)).isEqualTo(45);
    }

    @Test
    void rejectedPlanCannotBeApprovedOrApplied() {
        JsonNode request = createRequest(alice, x, 10);
        JsonNode plan = plan(alice, request);
        assertThat(api.post("/plans/" + plan.path("id").asString() + "/reject", bob.token(), Map.of()).status()).isEqualTo(400);
        assertThat(api.post("/plans/" + plan.path("id").asString() + "/reject", bob.token(), Map.of("comment", "no budget")).status()).isEqualTo(200);
        assertThat(approve(carol, plan).code()).isEqualTo("PLAN_NOT_PROPOSED");
        assertThat(apply(bob, plan).code()).isEqualTo("PLAN_NOT_APPROVED");
        assertThat(used(alice, x)).isEqualTo(45);
        assertThat(api.get("/capacity-requests/" + request.path("id").asString(), alice.token()).body().path("status").asString()).isEqualTo("REJECTED");
    }

    @Test
    void selfApprovalIsForbiddenEvenForReviewersAndIsAudited() {
        TestUser both = user(Role.REQUESTER, Role.REVIEWER);
        JsonNode plan = plan(both, createRequest(both, x, 10));
        Api.Result r = approve(both, plan);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("SELF_APPROVAL_FORBIDDEN");
        assertThat(api.get("/plans/" + plan.path("id").asString(), both.token()).body().path("status").asString()).isEqualTo("PROPOSED");
        assertThat(api.post("/plans/" + plan.path("id").asString() + "/reject", both.token(), Map.of("comment", "mine")).status()).isEqualTo(403);
        JsonNode events = api.get("/audit-events?targetId=" + plan.path("id").asString(), admin.token()).body().path("content");
        assertThat(events.toString()).contains("DENIED");
        assertThat(jdbc.queryForObject("select count(*) from approvals where plan_id = ?::uuid", Integer.class, plan.path("id").asString())).isZero();
    }

    @Test
    void requesterWithoutReviewerRoleCannotApprove() {
        JsonNode plan = plan(alice, createRequest(alice, x, 10));
        assertThat(approve(alice, plan).status()).isEqualTo(403);
        assertThat(apply(alice, plan).status()).isEqualTo(403);
    }

    @Test
    void secondApprovalAndWrongHashAreRejected() {
        JsonNode plan = plan(alice, createRequest(alice, x, 10));
        Api.Result wrong = api.post("/plans/" + plan.path("id").asString() + "/approve", bob.token(), Map.of("contentHash", "0".repeat(64)));
        assertThat(wrong.status()).isEqualTo(409);
        assertThat(wrong.code()).isEqualTo("PLAN_CONTENT_MISMATCH");
        assertThat(api.post("/plans/" + plan.path("id").asString() + "/approve", bob.token(), Map.of("contentHash", "short")).status()).isEqualTo(400);
        assertThat(approve(bob, plan).status()).isEqualTo(200);
        assertThat(approve(carol, plan).status()).isEqualTo(409);
    }

    @Test
    void duplicateApplyDoesNotAllocateTwice() {
        JsonNode plan = plan(alice, createRequest(alice, x, 40));
        approve(bob, plan);
        assertThat(apply(bob, plan).status()).isEqualTo(200);
        Api.Result again = apply(bob, plan);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("ALREADY_APPLIED");
        assertThat(used(alice, x)).isEqualTo(85);
    }

    @Test
    void partialPlansAreLabelledPartialAndNotApprovable() {
        JsonNode plan = plan(alice, createRequest(alice, x, 70));
        assertThat(plan.path("feasibility").asString()).isEqualTo("PARTIAL");
        assertThat(plan.path("allocatedServers").asInt()).isEqualTo(55);
        Api.Result r = approve(bob, plan);
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.code()).isEqualTo("PLAN_NOT_APPROVABLE");
        assertThat(api.get("/plans/" + plan.path("id").asString(), bob.token()).body().path("status").asString()).isEqualTo("PROPOSED");
    }

    @Test
    void insufficientCapacityIsNeverSilentlyOverAllocated() {
        JsonNode first = plan(alice, createRequest(alice, x, 40));
        approve(bob, first);
        apply(bob, first);
        JsonNode second = plan(alice, createRequest(alice, x, 40)); // only 15 slots left
        assertThat(second.path("feasibility").asString()).isEqualTo("PARTIAL");
        assertThat(second.path("allocatedServers").asInt()).isEqualTo(15);
        Fixture full = fixture(2, 5_000, 2);
        JsonNode none = plan(alice, createRequest(alice, full, 1));
        assertThat(none.path("feasibility").asString()).isEqualTo("INFEASIBLE");
        assertThat(none.path("allocations").size()).isZero();
    }

    @Test
    void inventoryChangedAfterPlanningMakesPlanStaleAtExecution() {
        JsonNode planA = plan(alice, createRequest(alice, x, 40));
        TestUser alice2 = user(Role.REQUESTER);
        JsonNode requestB = createRequest(alice2, x, 40);
        JsonNode planB = plan(alice2, requestB);
        approve(bob, planA);
        approve(bob, planB);
        assertThat(apply(bob, planA).status()).isEqualTo(200);

        Api.Result stale = apply(bob, planB);
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.code()).isEqualTo("STALE_INVENTORY");
        assertThat(api.get("/plans/" + planB.path("id").asString(), bob.token()).body().path("status").asString()).isEqualTo("STALE");
        assertThat(api.get("/capacity-requests/" + requestB.path("id").asString(), alice2.token()).body().path("status").asString()).isEqualTo("PLANNED");
        assertThat(used(alice, x)).isEqualTo(85);

        JsonNode replanned = plan(alice2, requestB); // new proposal reflects the new reality (15 left)
        assertThat(replanned.path("feasibility").asString()).isEqualTo("PARTIAL");
    }

    @Test
    void generatingANewPlanMarksOpenPlansStale() {
        JsonNode request = createRequest(alice, x, 10);
        JsonNode first = plan(alice, request);
        JsonNode second = plan(alice, request);
        assertThat(api.get("/plans/" + first.path("id").asString(), alice.token()).body().path("status").asString()).isEqualTo("STALE");
        assertThat(second.path("planNumber").asInt()).isEqualTo(2);
        assertThat(approve(bob, first).status()).isEqualTo(409);
        assertThat(approve(bob, second).status()).isEqualTo(200);
    }

    @Test
    void invalidRequestsAreRejectedWithoutCreatingAnything() {
        int before = totalRequests(alice);
        Map<String, Object> ok = requestBody(x, 5, false);
        for (Map.Entry<String, Object> bad : Map.<String, Object>of("serverCount", 0, "cpuCores", 1000, "powerWatts", 5).entrySet()) {
            Map<String, Object> body = new HashMap<>(ok);
            body.put(bad.getKey(), bad.getValue());
            assertThat(api.post("/capacity-requests", alice.token(), body).status()).as(bad.getKey()).isEqualTo(400);
        }
        Map<String, Object> negative = new HashMap<>(ok);
        negative.put("serverCount", -4);
        assertThat(api.post("/capacity-requests", alice.token(), negative).status()).isEqualTo(400);
        assertThat(api.post("/capacity-requests", alice.token(), Map.of("serverCount", 5)).status()).isEqualTo(400);

        Map<String, Object> blank = new HashMap<>(ok);
        blank.put("justification", " ");
        assertThat(api.post("/capacity-requests", alice.token(), blank).status()).isEqualTo(400);

        Map<String, Object> unknownDc = new HashMap<>(ok);
        unknownDc.put("dataCenterId", UUID.randomUUID().toString());
        assertThat(api.post("/capacity-requests", alice.token(), unknownDc).code()).isEqualTo("UNKNOWN_DATA_CENTER");

        Fixture other = fixture(10, 10_000, 0);
        Map<String, Object> wrongRack = new HashMap<>(ok);
        wrongRack.put("preferredRackGroupId", other.rgId().toString()); // exists, but not in x's data center
        assertThat(api.post("/capacity-requests", alice.token(), wrongRack).code()).isEqualTo("UNKNOWN_RACK_GROUP");

        assertThat(totalRequests(alice)).isEqualTo(before);
    }

    @Test
    void duplicateSubmissionWithSameIdempotencyKeyReturnsTheSameRequest() {
        String key = "idem-" + unique();
        Api.Result first = api.call("POST", "/capacity-requests", alice.token(), requestBody(x, 5, false), Map.of("Idempotency-Key", key));
        Api.Result second = api.call("POST", "/capacity-requests", alice.token(), requestBody(x, 5, false), Map.of("Idempotency-Key", key));
        assertThat(first.status()).isEqualTo(201);
        assertThat(second.body().path("id").asString()).isEqualTo(first.body().path("id").asString());
        assertThat(totalRequests(alice)).isEqualTo(1);
        assertThat(api.call("POST", "/capacity-requests", alice.token(), requestBody(x, 5, false), Map.of("Idempotency-Key", "x")).status()).isEqualTo(400);
    }

    @Test
    void requestVisibilityRules() {
        JsonNode request = createRequest(alice, x, 5);
        TestUser stranger = user(Role.REQUESTER);
        String path = "/capacity-requests/" + request.path("id").asString();
        assertThat(api.get(path, stranger.token()).status()).isEqualTo(404);
        assertThat(api.get(path, alice.token()).status()).isEqualTo(200);
        assertThat(api.get(path, bob.token()).status()).isEqualTo(200);
        assertThat(api.post(path + "/plans", stranger.token(), null).status()).isEqualTo(404);
        assertThat(api.post(path + "/plans", bob.token(), null).status()).isEqualTo(404); // reviewers review, they do not plan for others
        assertThat(totalRequests(stranger)).isZero();
        JsonNode plan = plan(alice, request);
        assertThat(api.get("/plans/" + plan.path("id").asString(), stranger.token()).status()).isEqualTo(404);
    }

    @Test
    void applyingAnUnknownPlanIs404() {
        assertThat(apply(bob, new tools.jackson.databind.ObjectMapper().createObjectNode().put("id", UUID.randomUUID().toString())).status()).isEqualTo(404);
    }
}
