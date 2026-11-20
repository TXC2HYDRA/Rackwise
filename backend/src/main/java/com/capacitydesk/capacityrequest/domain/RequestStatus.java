package com.capacitydesk.capacityrequest.domain;

import java.util.Set;

public enum RequestStatus {
    SUBMITTED, PLANNED, APPROVED, REJECTED, FULFILLED;

    public Set<RequestStatus> next() {
        return switch (this) {
            case SUBMITTED -> Set.of(PLANNED);
            case PLANNED -> Set.of(PLANNED, APPROVED, REJECTED);
            case APPROVED -> Set.of(PLANNED, FULFILLED);
            case REJECTED, FULFILLED -> Set.of();
        };
    }
}
