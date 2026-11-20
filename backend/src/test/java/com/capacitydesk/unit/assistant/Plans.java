package com.capacitydesk.unit.assistant;

import com.capacitydesk.planning.application.PlanViews.AllocationView;
import com.capacitydesk.planning.application.PlanViews.PlanView;
import com.capacitydesk.planning.domain.PlacementEngine;
import com.capacitydesk.planning.domain.PlacementEngine.Demand;
import com.capacitydesk.planning.domain.PlacementEngine.RackGroupState;
import com.capacitydesk.planning.domain.PlanStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Builds PlanViews by running the REAL placement engine, so fixtures cannot drift from production numbers. */
final class Plans {

    private Plans() {
    }

    static RackGroupState group(String code, int freeSlots, long freeWatts) {
        return new RackGroupState(UUID.nameUUIDFromBytes(code.getBytes()), code, "DC-A", freeSlots, freeWatts, 0);
    }

    static PlanView plan(int servers, int watts, String preferred, boolean spill, PlanStatus status, RackGroupState... groups) {
        UUID pref = preferred == null ? null : UUID.nameUUIDFromBytes(preferred.getBytes());
        var r = new PlacementEngine().place(new Demand(servers, watts, pref, spill), List.of(groups));
        return new PlanView(UUID.randomUUID(), UUID.randomUUID(), 1, status, r.feasibility(), r.requested(), r.allocated(),
                r.summary(), r.allocations().stream().map(a -> new AllocationView(a.rackGroupId(), a.rackGroupCode(), a.dataCenterCode(), a.servers(), a.rackGroupVersion())).toList(),
                r.evaluations(), "h", UUID.randomUUID(), Instant.now(), null, null);
    }

    static PlanView plan(int servers, int watts, String preferred, boolean spill, RackGroupState... groups) {
        return plan(servers, watts, preferred, spill, PlanStatus.PROPOSED, groups);
    }
}
