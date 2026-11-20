package com.capacitydesk.capacityrequest.domain;

import com.capacitydesk.common.persistence.TimestampedEntity;
import com.capacitydesk.common.web.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "capacity_requests")
public class CapacityRequest extends TimestampedEntity {

    @Id
    private UUID id;
    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;
    @Column(name = "data_center_id", nullable = false, updatable = false)
    private UUID dataCenterId;
    @Column(name = "preferred_rack_group_id", updatable = false)
    private UUID preferredRackGroupId;
    @Column(name = "allow_other_rack_groups", nullable = false, updatable = false)
    private boolean allowOtherRackGroups;
    @Column(name = "server_count", nullable = false, updatable = false)
    private int serverCount;
    @Column(name = "cpu_cores", nullable = false, updatable = false)
    private int cpuCores;
    @Column(name = "ram_gb", nullable = false, updatable = false)
    private int ramGb;
    @Column(name = "storage_tb", nullable = false, updatable = false)
    private int storageTb;
    @Column(name = "power_watts", nullable = false, updatable = false)
    private int powerWatts;
    @Column(nullable = false, updatable = false, length = 1000)
    private String justification;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Priority priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.SUBMITTED;
    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;

    protected CapacityRequest() {
    }

    public CapacityRequest(UUID requesterId, UUID dataCenterId, UUID preferredRackGroupId, boolean allowOtherRackGroups,
                           int serverCount, int cpuCores, int ramGb, int storageTb, int powerWatts,
                           String justification, Priority priority, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.requesterId = requesterId;
        this.dataCenterId = dataCenterId;
        this.preferredRackGroupId = preferredRackGroupId;
        this.allowOtherRackGroups = allowOtherRackGroups;
        this.serverCount = serverCount;
        this.cpuCores = cpuCores;
        this.ramGb = ramGb;
        this.storageTb = storageTb;
        this.powerWatts = powerWatts;
        this.justification = justification;
        this.priority = priority;
        this.idempotencyKey = idempotencyKey;
    }

    public void transitionTo(RequestStatus next) {
        if (!status.next().contains(next)) {
            throw DomainException.conflict("ILLEGAL_REQUEST_TRANSITION", "Request cannot move from " + status + " to " + next);
        }
        status = next;
    }

    public UUID getId() { return id; }
    public UUID getRequesterId() { return requesterId; }
    public UUID getDataCenterId() { return dataCenterId; }
    public UUID getPreferredRackGroupId() { return preferredRackGroupId; }
    public boolean isAllowOtherRackGroups() { return allowOtherRackGroups; }
    public int getServerCount() { return serverCount; }
    public int getCpuCores() { return cpuCores; }
    public int getRamGb() { return ramGb; }
    public int getStorageTb() { return storageTb; }
    public int getPowerWatts() { return powerWatts; }
    public String getJustification() { return justification; }
    public Priority getPriority() { return priority; }
    public RequestStatus getStatus() { return status; }
}
