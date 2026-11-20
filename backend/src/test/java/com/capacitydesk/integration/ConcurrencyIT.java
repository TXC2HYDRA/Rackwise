package com.capacitydesk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.capacitydesk.identity.domain.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Real HTTP, real PostgreSQL, real threads. Capacity must never be exceeded. */
class ConcurrencyIT extends IntegrationTestBase {

    @Test
    void competingApprovedPlansForTheSameRackGroupApplyExactlyOnce() throws Exception {
        TestUser reviewer = user(Role.REVIEWER);
        Fixture x = fixture(100, 50_000, 45); // 55 free; six plans of 40 were each feasible when computed
        List<JsonNode> plans = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            TestUser requester = user(Role.REQUESTER);
            JsonNode plan = plan(requester, createRequest(requester, x, 40));
            assertThat(plan.path("feasibility").asString()).isEqualTo("FEASIBLE");
            assertThat(approve(reviewer, plan).status()).isEqualTo(200);
            plans.add(plan);
        }
        List<Callable<Api.Result>> tasks = plans.stream().<Callable<Api.Result>>map(p -> () -> apply(reviewer, p)).toList();
        List<Api.Result> results = parallel(tasks);

        long ok = results.stream().filter(r -> r.status() == 200).count();
        long stale = results.stream().filter(r -> r.status() == 409 && "STALE_INVENTORY".equals(r.code())).count();
        assertThat(ok).as("results: %s", results).isEqualTo(1);
        assertThat(stale).isEqualTo(5);
        assertThat(used(reviewer, x)).isEqualTo(85).isLessThanOrEqualTo(100);
        assertThat(jdbc.queryForObject("select count(*) from servers s join rack_groups r on r.id = s.rack_group_id where r.id = ?::uuid and s.plan_id is not null",
                Integer.class, x.rgId().toString())).isEqualTo(40);
        assertThat(jdbc.queryForObject("select count(*) from placement_plans p join capacity_requests c on c.id = p.request_id where c.data_center_id = ?::uuid and p.status = 'STALE'",
                Integer.class, x.dcId().toString())).isEqualTo(5);
    }

    @Test
    void sameApprovedPlanAppliedConcurrentlyCreatesServersOnce() throws Exception {
        TestUser requester = user(Role.REQUESTER);
        TestUser reviewer = user(Role.REVIEWER);
        Fixture x = fixture(100, 50_000, 45);
        JsonNode plan = plan(requester, createRequest(requester, x, 40));
        approve(reviewer, plan);

        List<Api.Result> results = parallel(8, () -> apply(reviewer, plan));
        assertThat(results.stream().filter(r -> r.status() == 200).count()).as("results: %s", results).isEqualTo(1);
        assertThat(results.stream().filter(r -> r.status() == 409).count()).isEqualTo(7);
        assertThat(used(reviewer, x)).isEqualTo(85);
    }

    @Test
    void twoReviewersDecidingTheSamePlanConcurrentlyYieldOneDecision() throws Exception {
        TestUser requester = user(Role.REQUESTER);
        Fixture x = fixture(100, 50_000, 0);
        JsonNode plan = plan(requester, createRequest(requester, x, 10));
        TestUser r1 = user(Role.REVIEWER);
        TestUser r2 = user(Role.REVIEWER);
        List<Callable<Api.Result>> tasks = List.of(() -> approve(r1, plan), () -> approve(r2, plan), () -> approve(r1, plan));
        List<Api.Result> results = parallel(tasks);
        assertThat(results.stream().filter(r -> r.status() == 200).count()).as("results: %s", results).isEqualTo(1);
        assertThat(results.stream().filter(r -> r.status() == 409).count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from approvals where plan_id = ?::uuid", Integer.class, plan.path("id").asString())).isEqualTo(1);
    }

    @Test
    void concurrentAdminServerAdditionsNeverExceedSlots() throws Exception {
        TestUser admin = user(Role.ADMIN);
        Fixture x = fixture(5, 50_000, 0);
        List<Callable<Api.Result>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String host = "race-" + unique();
            tasks.add(() -> api.post("/inventory/servers", admin.token(), java.util.Map.of("hostname", host, "rackGroupId", x.rgId().toString(),
                    "cpuCores", 8, "ramGb", 32, "storageTb", 1, "powerWatts", 500)));
        }
        List<Api.Result> results = parallel(tasks);
        assertThat(results.stream().filter(r -> r.status() == 201).count()).isEqualTo(5);
        assertThat(results.stream().filter(r -> r.status() == 422).count()).isEqualTo(7);
        assertThat(used(admin, x)).isEqualTo(5);
    }
}
