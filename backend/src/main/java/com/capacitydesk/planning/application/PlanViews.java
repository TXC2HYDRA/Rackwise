package com.capacitydesk.planning.application;

import com.capacitydesk.planning.domain.PlacementEngine;
import com.capacitydesk.planning.domain.PlanStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlanViews {

    private PlanViews() {
    }

    public record AllocationView(UUID rackGroupId, String rackGroupCode, String dataCenterCode, int serverCount, long rackGroupVersion) {}

    public record PlanView(UUID id, UUID requestId, int planNumber, PlanStatus status,
                           PlacementEngine.Feasibility feasibility, int requestedServers, int allocatedServers,
                           String summary, List<AllocationView> allocations,
                           List<PlacementEngine.GroupEvaluation> evaluation,
                           String contentHash, UUID createdBy, Instant createdAt, Instant appliedAt, String failureReason) {}
}
