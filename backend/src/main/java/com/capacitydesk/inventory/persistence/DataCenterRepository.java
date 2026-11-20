package com.capacitydesk.inventory.persistence;

import com.capacitydesk.inventory.domain.DataCenter;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataCenterRepository extends JpaRepository<DataCenter, UUID> {
    boolean existsByCode(String code);
}
