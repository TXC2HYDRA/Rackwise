package com.capacitydesk.inventory.domain;

import com.capacitydesk.common.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "data_centers")
public class DataCenter extends TimestampedEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false)
    private String name;

    protected DataCenter() {
    }

    public DataCenter(String code, String name) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
