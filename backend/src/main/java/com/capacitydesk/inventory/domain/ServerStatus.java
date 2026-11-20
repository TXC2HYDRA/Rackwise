package com.capacitydesk.inventory.domain;

public enum ServerStatus {
    ACTIVE, MAINTENANCE, DECOMMISSIONED;

    /** Occupies a rack slot and draws power. */
    public boolean occupiesCapacity() {
        return this != DECOMMISSIONED;
    }
}
