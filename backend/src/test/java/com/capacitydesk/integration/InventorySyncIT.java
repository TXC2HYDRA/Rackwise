package com.capacitydesk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.capacitydesk.identity.domain.Role;
import com.capacitydesk.inventory.domain.ServerStatus;
import com.capacitydesk.inventorysync.ExternalInventorySource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;

@Import(InventorySyncIT.StubConfig.class)
class InventorySyncIT extends IntegrationTestBase {

    /** Scripted external system: tests set what it returns, how long it takes, or that it fails. */
    static class StubSource implements ExternalInventorySource {
        volatile List<ExternalServer> data = List.of();
        volatile boolean fail;
        volatile long delayMs;

        @Override public String name() { return "stub"; }

        @Override public List<ExternalServer> fetch() throws Exception {
            if (delayMs > 0) Thread.sleep(delayMs);
            if (fail) throw new java.io.IOException("cmdb down");
            return data;
        }
    }

    @TestConfiguration
    static class StubConfig {
        @Bean @Primary StubSource stubSource() { return new StubSource(); }
    }

    @Autowired StubSource stub;
    TestUser admin, bob, alice;

    @BeforeEach
    void setUp() {
        admin = user(Role.ADMIN);
        bob = user(Role.REVIEWER);
        alice = user(Role.REQUESTER);
        stub.fail = false;
        stub.delayMs = 0;
        stub.data = List.of();
    }

    List<ExternalInventorySource.ExternalServer> feed(Fixture f, int count, ServerStatus status) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> new ExternalInventorySource.ExternalServer(
                "%s-ext-%03d".formatted(f.dcCode().toLowerCase(), i), f.dcCode(), "X", 32, 256, 8, 500, status)).toList();
    }

    JsonNode sync() {
        Api.Result r = api.post("/admin/inventory-sync", admin.token(), null);
        assertThat(r.status()).as("sync: %s", r.body()).isEqualTo(200);
        return r.body();
    }

    @Test
    void createsServersThenIsIdempotent() {
        Fixture f = fixture(100, 50_000, 10);
        stub.data = feed(f, 4, ServerStatus.ACTIVE);
        JsonNode first = sync();
        assertThat(first.path("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(first.path("created").asInt()).isEqualTo(4);
        assertThat(used(admin, f)).isEqualTo(14);

        JsonNode second = sync();
        assertThat(second.path("created").asInt()).isZero();
        assertThat(second.path("unchanged").asInt()).isEqualTo(4);
        assertThat(used(admin, f)).isEqualTo(14);
    }

    @Test
    void alignsStatusAndFreesCapacityWhenDecommissioned() {
        Fixture f = fixture(100, 50_000, 0);
        stub.data = feed(f, 3, ServerStatus.ACTIVE);
        sync();
        stub.data = feed(f, 3, ServerStatus.DECOMMISSIONED);
        JsonNode run = sync();
        assertThat(run.path("updated").asInt()).isEqualTo(3);
        assertThat(used(admin, f)).isZero();
    }

    @Test
    void neverExceedsCapacityAndReportsWhatWasSkipped() {
        Fixture f = fixture(10, 50_000, 8); // 2 slots free
        stub.data = feed(f, 5, ServerStatus.ACTIVE);
        JsonNode run = sync();
        assertThat(run.path("created").asInt()).isEqualTo(2);
        assertThat(run.path("skipped").asInt()).isEqualTo(3);
        assertThat(run.path("skipDetails").toString()).contains(f.dcCode().toLowerCase() + "-ext-003");
        assertThat(used(admin, f)).isEqualTo(10);
    }

    @Test
    void unknownRackGroupIsSkippedNotCreated() {
        Fixture f = fixture(100, 50_000, 0);
        stub.data = List.of(new ExternalInventorySource.ExternalServer("zz-" + unique(), f.dcCode(), "NOPE", 32, 256, 8, 500, ServerStatus.ACTIVE));
        JsonNode run = sync();
        assertThat(run.path("created").asInt()).isZero();
        assertThat(run.path("skipDetails").toString()).contains("UNKNOWN_RACK_GROUP");
    }

    @Test
    void sourceFailureRecordsFailedRunAndLeavesInventoryAlone() {
        Fixture f = fixture(100, 50_000, 5);
        stub.fail = true;
        JsonNode run = sync();
        assertThat(run.path("status").asString()).isEqualTo("FAILED");
        assertThat(run.path("error").asString()).contains("source unavailable");
        assertThat(used(admin, f)).isEqualTo(5);
        assertThat(api.get("/audit-events?action=INVENTORY_SYNC_FAILED&targetType=SYNC_RUN", admin.token()).body().path("content").toString())
                .contains(run.path("id").asString());
    }

    @Test
    void syncStalesAnApprovedPlanSoItCannotOverCommit() {
        Fixture f = fixture(100, 50_000, 45);
        JsonNode plan = plan(alice, createRequest(alice, f, 40));
        assertThat(approve(bob, plan).status()).isEqualTo(200);
        stub.data = feed(f, 2, ServerStatus.ACTIVE); // inventory changes after approval
        sync();
        Api.Result applied = apply(bob, plan);
        assertThat(applied.status()).isEqualTo(409);
        assertThat(applied.code()).isEqualTo("STALE_INVENTORY");
        assertThat(used(admin, f)).isEqualTo(47);
    }

    @Test
    void onlyOneRunAtATime() throws Exception {
        Fixture f = fixture(100, 50_000, 0);
        stub.data = feed(f, 2, ServerStatus.ACTIVE);
        stub.delayMs = 1200;
        List<Integer> statuses = new ArrayList<>();
        parallel(2, () -> api.post("/admin/inventory-sync", admin.token(), null)).forEach(r -> statuses.add(r.status()));
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    }

    @Test
    void adminOnlyAndAuditedAndListed() {
        assertThat(api.post("/admin/inventory-sync", bob.token(), null).status()).isEqualTo(403);
        assertThat(api.post("/admin/inventory-sync", alice.token(), null).status()).isEqualTo(403);
        assertThat(api.post("/admin/inventory-sync", null, null).status()).isEqualTo(401);
        assertThat(api.get("/admin/inventory-sync/runs", bob.token()).status()).isEqualTo(403);
        JsonNode run = sync();
        assertThat(api.get("/admin/inventory-sync/runs", admin.token()).body().toString()).contains(run.path("id").asString());
        assertThat(api.get("/audit-events?action=INVENTORY_SYNC_COMPLETED&targetType=SYNC_RUN", admin.token()).body().path("content").toString())
                .contains(run.path("id").asString());
    }
}
