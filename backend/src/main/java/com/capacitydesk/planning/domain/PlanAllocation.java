package com.capacitydesk.planning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "plan_allocations")
public class PlanAllocation {

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private PlacementPlan plan;
    @Column(name = "rack_group_id", nullable = false)
    private UUID rackGroupId;
    @Column(name = "rack_group_code", nullable = false)
    private String rackGroupCode;
    @Column(name = "data_center_code", nullable = false)
    private String dataCenterCode;
    @Column(name = "server_count", nullable = false)
    private int serverCount;
    @Column(name = "rack_group_version", nullable = false)
    private long rackGroupVersion;

    protected PlanAllocation() {
    }

    PlanAllocation(PlacementPlan plan, PlacementEngine.Allocation a) {
        this.id = UUID.randomUUID();
        this.plan = plan;
        this.rackGroupId = a.rackGroupId();
        this.rackGroupCode = a.rackGroupCode();
        this.dataCenterCode = a.dataCenterCode();
        this.serverCount = a.servers();
        this.rackGroupVersion = a.rackGroupVersion();
    }

    public UUID getRackGroupId() { return rackGroupId; }
    public String getRackGroupCode() { return rackGroupCode; }
    public String getDataCenterCode() { return dataCenterCode; }
    public int getServerCount() { return serverCount; }
    public long getRackGroupVersion() { return rackGroupVersion; }
}
