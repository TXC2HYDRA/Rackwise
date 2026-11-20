package com.capacitydesk.planning.domain;

import java.util.Set;

public enum PlanStatus {
    PROPOSED, APPROVED, REJECTED, APPLIED, FAILED, STALE;

    public Set<PlanStatus> next() {
        return switch (this) {
            case PROPOSED -> Set.of(APPROVED, REJECTED, STALE);
            case APPROVED -> Set.of(APPLIED, FAILED, STALE);
            case REJECTED, APPLIED, FAILED, STALE -> Set.of();
        };
    }
}
