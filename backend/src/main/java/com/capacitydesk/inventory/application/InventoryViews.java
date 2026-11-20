package com.capacitydesk.inventory.application;

import com.capacitydesk.inventory.domain.ServerStatus;
import java.util.List;
import java.util.UUID;

public final class InventoryViews {

    private InventoryViews() {
    }

    public record DataCenterView(UUID id, String code, String name) {}

    public record RackGroupCapacity(UUID id, UUID dataCenterId, String dataCenterCode, String code, String name,
                                    int slotCapacity, int slotsUsed, int slotsAvailable,
                                    long powerCapacityWatts, long powerUsedWatts, long powerAvailableWatts,
                                    long version) {}

    public record CapacitySummary(long totalSlots, long usedSlots, long availableSlots,
                                  long totalPowerWatts, long usedPowerWatts, long availablePowerWatts,
                                  List<RackGroupCapacity> rackGroups) {}

    public record ServerView(UUID id, String hostname, UUID rackGroupId, String rackGroupCode, String dataCenterCode,
                             int cpuCores, int ramGb, int storageTb, int powerWatts, ServerStatus status) {}

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
}
