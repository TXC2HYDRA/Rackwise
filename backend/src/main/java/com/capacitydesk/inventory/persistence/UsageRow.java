package com.capacitydesk.inventory.persistence;

import java.util.UUID;

public record UsageRow(UUID rackGroupId, long servers, long powerWatts) {
}
