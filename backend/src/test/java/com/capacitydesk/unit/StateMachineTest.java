package com.capacitydesk.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capacitydesk.capacityrequest.domain.CapacityRequest;
import com.capacitydesk.capacityrequest.domain.Priority;
import com.capacitydesk.capacityrequest.domain.RequestStatus;
import com.capacitydesk.common.web.DomainException;
import com.capacitydesk.planning.domain.PlacementEngine;
import com.capacitydesk.planning.domain.PlacementPlan;
import com.capacitydesk.planning.domain.PlanStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StateMachineTest {

    private static PlacementPlan plan(int servers) {
        UUID rg = UUID.randomUUID();
        var result = new PlacementEngine.Result(PlacementEngine.Feasibility.FEASIBLE, servers, servers,
                List.of(new PlacementEngine.Allocation(rg, "X", "DC-A", servers, 3)), List.of(), "ok");
        return new PlacementPlan(UUID.randomUUID(), 1, result, "[]", UUID.randomUUID());
    }

    @Test
    void legalPlanTransitions() {
        PlacementPlan p = plan(5);
        assertThat(p.getStatus()).isEqualTo(PlanStatus.PROPOSED);
        p.transitionTo(PlanStatus.APPROVED);
        p.markApplied(UUID.randomUUID());
        assertThat(p.getStatus()).isEqualTo(PlanStatus.APPLIED);
        assertThat(p.getAppliedAt()).isNotNull();
    }

    @Test
    void approvedPlanCanGoStaleOrFailed() {
        PlacementPlan a = plan(5);
        a.transitionTo(PlanStatus.APPROVED);
        a.transitionTo(PlanStatus.STALE);
        PlacementPlan b = plan(5);
        b.transitionTo(PlanStatus.APPROVED);
        b.markFailed("no capacity");
        assertThat(b.getFailureReason()).isEqualTo("no capacity");
    }

    @ParameterizedTest
    @EnumSource(value = PlanStatus.class, names = {"REJECTED", "APPLIED", "FAILED", "STALE"})
    void terminalPlanStatesHaveNoExit(PlanStatus terminal) {
        assertThat(terminal.next()).isEmpty();
    }

    @Test
    void proposedPlanCannotBeAppliedDirectly() {
        assertThatThrownBy(() -> plan(5).markApplied(UUID.randomUUID()))
                .isInstanceOf(DomainException.class).hasMessageContaining("PROPOSED to APPLIED");
    }

    @Test
    void rejectedPlanCannotBeApproved() {
        PlacementPlan p = plan(5);
        p.transitionTo(PlanStatus.REJECTED);
        assertThatThrownBy(() -> p.transitionTo(PlanStatus.APPROVED)).isInstanceOf(DomainException.class);
    }

    @Test
    void appliedPlanCannotBeAppliedAgain() {
        PlacementPlan p = plan(5);
        p.transitionTo(PlanStatus.APPROVED);
        p.markApplied(UUID.randomUUID());
        assertThatThrownBy(() -> p.markApplied(UUID.randomUUID())).isInstanceOf(DomainException.class);
    }

    @Test
    void contentHashIsStableForSameContentAndChangesWhenAllocationChanges() {
        UUID req = UUID.randomUUID();
        UUID rg = UUID.randomUUID();
        var r1 = new PlacementEngine.Result(PlacementEngine.Feasibility.FEASIBLE, 5, 5,
                List.of(new PlacementEngine.Allocation(rg, "X", "DC-A", 5, 1)), List.of(), "s");
        var r2 = new PlacementEngine.Result(PlacementEngine.Feasibility.FEASIBLE, 5, 5,
                List.of(new PlacementEngine.Allocation(rg, "X", "DC-A", 5, 1)), List.of(), "different summary text");
        var r3 = new PlacementEngine.Result(PlacementEngine.Feasibility.FEASIBLE, 5, 5,
                List.of(new PlacementEngine.Allocation(rg, "X", "DC-A", 5, 2)), List.of(), "s");
        String h1 = new PlacementPlan(req, 1, r1, "[]", UUID.randomUUID()).getContentHash();
        assertThat(h1).hasSize(64).isEqualTo(new PlacementPlan(req, 2, r2, "[]", UUID.randomUUID()).getContentHash());
        assertThat(new PlacementPlan(req, 1, r3, "[]", UUID.randomUUID()).getContentHash()).isNotEqualTo(h1);
    }

    @Test
    void requestLifecycleAndIllegalMoves() {
        CapacityRequest r = new CapacityRequest(UUID.randomUUID(), UUID.randomUUID(), null, false, 4, 32, 256, 8, 500, "why", Priority.HIGH, null);
        assertThat(r.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThatThrownBy(() -> r.transitionTo(RequestStatus.FULFILLED)).isInstanceOf(DomainException.class);
        r.transitionTo(RequestStatus.PLANNED);
        r.transitionTo(RequestStatus.APPROVED);
        r.transitionTo(RequestStatus.FULFILLED);
        assertThat(RequestStatus.FULFILLED.next()).isEmpty();
        assertThatThrownBy(() -> r.transitionTo(RequestStatus.PLANNED)).isInstanceOf(DomainException.class);
    }
}
