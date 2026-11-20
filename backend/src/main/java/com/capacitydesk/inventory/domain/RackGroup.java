package com.capacitydesk.inventory.domain;

import com.capacitydesk.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "rack_groups")
public class RackGroup extends TimestampedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "data_center_id", nullable = false, updatable = false)
    private DataCenter dataCenter;

    @Column(nullable = false, length = 16)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "slot_capacity", nullable = false)
    private int slotCapacity;

    @Column(name = "power_capacity_watts", nullable = false)
    private long powerCapacityWatts;

    @Version
    private long version;

    protected RackGroup() {
    }

    public RackGroup(DataCenter dataCenter, String code, String name, int slotCapacity, long powerCapacityWatts) {
        this.id = UUID.randomUUID();
        this.dataCenter = dataCenter;
        this.code = code;
        this.name = name;
        this.slotCapacity = slotCapacity;
        this.powerCapacityWatts = powerCapacityWatts;
    }

    public void changeLimits(int slotCapacity, long powerCapacityWatts) {
        this.slotCapacity = slotCapacity;
        this.powerCapacityWatts = powerCapacityWatts;
    }

    public UUID getId() { return id; }
    public DataCenter getDataCenter() { return dataCenter; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getSlotCapacity() { return slotCapacity; }
    public long getPowerCapacityWatts() { return powerCapacityWatts; }
    public long getVersion() { return version; }
}
