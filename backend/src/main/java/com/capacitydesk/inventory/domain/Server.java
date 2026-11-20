package com.capacitydesk.inventory.domain;

import com.capacitydesk.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "servers")
public class Server extends TimestampedEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String hostname;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rack_group_id", nullable = false)
    private RackGroup rackGroup;

    @Column(name = "cpu_cores", nullable = false)
    private int cpuCores;

    @Column(name = "ram_gb", nullable = false)
    private int ramGb;

    @Column(name = "storage_tb", nullable = false)
    private int storageTb;

    @Column(name = "power_watts", nullable = false)
    private int powerWatts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServerStatus status;

    protected Server() {
    }

    public Server(String hostname, RackGroup rackGroup, int cpuCores, int ramGb, int storageTb, int powerWatts, ServerStatus status) {
        this.id = UUID.randomUUID();
        this.hostname = hostname;
        this.rackGroup = rackGroup;
        this.cpuCores = cpuCores;
        this.ramGb = ramGb;
        this.storageTb = storageTb;
        this.powerWatts = powerWatts;
        this.status = status;
    }

    public void setStatus(ServerStatus status) {
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getHostname() { return hostname; }
    public RackGroup getRackGroup() { return rackGroup; }
    public int getCpuCores() { return cpuCores; }
    public int getRamGb() { return ramGb; }
    public int getStorageTb() { return storageTb; }
    public int getPowerWatts() { return powerWatts; }
    public ServerStatus getStatus() { return status; }
}
