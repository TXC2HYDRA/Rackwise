package com.capacitydesk.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capacitydesk.identity.domain.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class InventoryIT extends IntegrationTestBase {

    private Map<String, Object> server(Fixture f, String host, int watts) {
        return Map.of("hostname", host, "rackGroupId", f.rgId().toString(), "cpuCores", 32, "ramGb", 256, "storageTb", 8, "powerWatts", watts);
    }

    @Test
    void capacitySummaryReflectsSlotsAndPower() {
        TestUser admin = user(Role.ADMIN);
        Fixture f = fixture(10, 5_000, 3);
        var rg = rackGroup(admin, f);
        assertThat(rg.path("slotsUsed").asInt()).isEqualTo(3);
        assertThat(rg.path("slotsAvailable").asInt()).isEqualTo(7);
        assertThat(rg.path("powerUsedWatts").asInt()).isEqualTo(1_500);
        assertThat(rg.path("powerAvailableWatts").asInt()).isEqualTo(3_500);
        var summary = api.get("/inventory/capacity?dataCenterId=" + f.dcId(), admin.token()).body();
        assertThat(summary.path("totalSlots").asInt()).isEqualTo(10);
        assertThat(summary.path("availableSlots").asInt()).isEqualTo(7);
        assertThat(summary.path("rackGroups").size()).isEqualTo(1);
    }

    @Test
    void requesterCanReadButNotWrite() {
        TestUser req = user(Role.REQUESTER);
        Fixture f = fixture(10, 5_000, 1);
        assertThat(api.get("/inventory/datacenters", req.token()).status()).isEqualTo(200);
        assertThat(api.get("/inventory/rack-groups?dataCenterId=" + f.dcId(), req.token()).body().size()).isEqualTo(1);
        assertThat(api.post("/inventory/servers", req.token(), server(f, "h-" + unique(), 500)).status()).isEqualTo(403);
        assertThat(used(req, f)).isEqualTo(1);
    }

    @Test
    void addServerBeyondSlotsOrPowerIsRejectedWithoutChangingState() {
        TestUser admin = user(Role.ADMIN);
        Fixture f = fixture(2, 1_200, 2);
        Api.Result noSlot = api.post("/inventory/servers", admin.token(), server(f, "h-" + unique(), 100));
        assertThat(noSlot.status()).isEqualTo(422);
        assertThat(noSlot.code()).isEqualTo("NO_SLOT_CAPACITY");

        Fixture g = fixture(10, 1_200, 2); // 1000 W used, 200 W left
        Api.Result noPower = api.post("/inventory/servers", admin.token(), server(g, "h-" + unique(), 300));
        assertThat(noPower.status()).isEqualTo(422);
        assertThat(noPower.code()).isEqualTo("NO_POWER_CAPACITY");
        assertThat(used(admin, f)).isEqualTo(2);
        assertThat(used(admin, g)).isEqualTo(2);
        assertThat(api.post("/inventory/servers", admin.token(), server(g, "h-" + unique(), 200)).status()).isEqualTo(201);
    }

    @Test
    void duplicateHostnameAndDuplicateCodesConflict() {
        TestUser admin = user(Role.ADMIN);
        Fixture f = fixture(10, 5_000, 0);
        String host = "dup-" + unique();
        assertThat(api.post("/inventory/servers", admin.token(), server(f, host, 500)).status()).isEqualTo(201);
        assertThat(api.post("/inventory/servers", admin.token(), server(f, host, 500)).status()).isEqualTo(409);
        assertThat(api.post("/inventory/datacenters", admin.token(), Map.of("code", f.dcCode(), "name", "again")).status()).isEqualTo(409);
        assertThat(used(admin, f)).isEqualTo(1);
    }

    @Test
    void limitsCannotDropBelowUsageAndDecommissionFreesCapacity() {
        TestUser admin = user(Role.ADMIN);
        Fixture f = fixture(4, 4_000, 4);
        assertThat(api.put("/inventory/rack-groups/" + f.rgId() + "/limits", admin.token(), Map.of("slotCapacity", 3, "powerCapacityWatts", 4_000)).code())
                .isEqualTo("LIMIT_BELOW_USAGE");
        assertThat(rackGroup(admin, f).path("slotCapacity").asInt()).isEqualTo(4);

        String id = api.get("/inventory/servers?rackGroupId=" + f.rgId(), admin.token()).body().path("content").get(0).path("id").asString();
        assertThat(api.put("/inventory/servers/" + id + "/status", admin.token(), Map.of("status", "DECOMMISSIONED")).status()).isEqualTo(200);
        assertThat(used(admin, f)).isEqualTo(3);
        assertThat(api.post("/inventory/servers", admin.token(), server(f, "fill-" + unique(), 500)).status()).isEqualTo(201);
        // rack is full again, so the decommissioned server cannot come back
        assertThat(api.put("/inventory/servers/" + id + "/status", admin.token(), Map.of("status", "ACTIVE")).code()).isEqualTo("NO_SLOT_CAPACITY");
    }

    @Test
    void inventoryVersionIncreasesOnEveryCapacityChange() {
        TestUser admin = user(Role.ADMIN);
        Fixture f = fixture(10, 10_000, 0);
        long v0 = rackGroup(admin, f).path("version").asLong();
        api.post("/inventory/servers", admin.token(), server(f, "v-" + unique(), 500));
        long v1 = rackGroup(admin, f).path("version").asLong();
        assertThat(v1).isGreaterThan(v0);
        api.put("/inventory/rack-groups/" + f.rgId() + "/limits", admin.token(), Map.of("slotCapacity", 12, "powerCapacityWatts", 10_000));
        assertThat(rackGroup(admin, f).path("version").asLong()).isGreaterThan(v1);
    }

    @Test
    void paginationFilteringAndValidation() {
        TestUser req = user(Role.REQUESTER);
        Fixture f = fixture(20, 20_000, 5);
        var page = api.get("/inventory/servers?rackGroupId=" + f.rgId() + "&size=2&page=1", req.token()).body();
        assertThat(page.path("content").size()).isEqualTo(2);
        assertThat(page.path("totalElements").asInt()).isEqualTo(5);
        assertThat(page.path("totalPages").asInt()).isEqualTo(3);
        assertThat(api.get("/inventory/servers?rackGroupId=" + f.rgId() + "&status=DECOMMISSIONED", req.token()).body().path("totalElements").asInt()).isZero();
        assertThat(api.get("/inventory/servers?size=1000", req.token()).status()).isEqualTo(400);
        assertThat(api.get("/inventory/servers?status=BOGUS", req.token()).status()).isEqualTo(400);
    }

    @Test
    void validationRejectsBadServersAndUnknownRackGroups() {
        TestUser admin = user(Role.ADMIN);
        Fixture f = fixture(10, 5_000, 0);
        Api.Result bad = api.post("/inventory/servers", admin.token(), Map.of("hostname", "BAD HOST", "rackGroupId", f.rgId().toString(), "cpuCores", 0, "ramGb", 1, "storageTb", 1, "powerWatts", 1));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().path("details").size()).isGreaterThanOrEqualTo(2);
        Api.Result unknown = api.post("/inventory/servers", admin.token(), Map.of("hostname", "ok-" + unique(), "rackGroupId", java.util.UUID.randomUUID().toString(), "cpuCores", 8, "ramGb", 32, "storageTb", 1, "powerWatts", 100));
        assertThat(unknown.status()).isEqualTo(422);
    }

    @Test
    void databaseEnforcesIntegrityIndependentlyOfTheApplication() {
        Fixture f = fixture(10, 5_000, 0);
        assertThatThrownBy(() -> jdbc.update("insert into rack_groups (id, data_center_id, code, name, slot_capacity, power_capacity_watts, created_at, updated_at) values (gen_random_uuid(), ?, 'Q', 'q', 0, 100, now(), now())", f.dcId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into servers (id, hostname, rack_group_id, cpu_cores, ram_gb, storage_tb, power_watts, status, created_at, updated_at) values (gen_random_uuid(), 'x-" + unique() + "', gen_random_uuid(), 1, 1, 1, 1, 'ACTIVE', now(), now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into servers (id, hostname, rack_group_id, cpu_cores, ram_gb, storage_tb, power_watts, status, created_at, updated_at) values (gen_random_uuid(), 'y-" + unique() + "', ?, 1, 1, 1, 1, 'EXPLODED', now(), now())", f.rgId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
