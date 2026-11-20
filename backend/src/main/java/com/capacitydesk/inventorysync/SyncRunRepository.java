package com.capacitydesk.inventorysync;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {
}
