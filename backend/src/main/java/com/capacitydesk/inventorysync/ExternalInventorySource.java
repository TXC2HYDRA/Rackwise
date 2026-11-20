package com.capacitydesk.inventorysync;

import com.capacitydesk.inventory.domain.ServerStatus;
import java.util.List;

/** Port to an external system of record (CMDB, asset database). Only a mock implementation exists here. */
public interface ExternalInventorySource {

    record ExternalServer(String hostname, String dataCenterCode, String rackGroupCode, int cpuCores, int ramGb,
                          int storageTb, int powerWatts, ServerStatus status) {}

    String name();

    List<ExternalServer> fetch() throws Exception;
}
