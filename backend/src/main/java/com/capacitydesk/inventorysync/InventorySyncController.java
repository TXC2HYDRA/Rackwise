package com.capacitydesk.inventorysync;

import com.capacitydesk.inventorysync.SyncRun.SyncView;
import com.capacitydesk.security.CurrentUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inventory-sync")
@PreAuthorize("hasRole('ADMIN')")
class InventorySyncController {

    private final InventorySyncService sync;

    InventorySyncController(InventorySyncService sync) {
        this.sync = sync;
    }

    @PostMapping
    SyncView run(Authentication auth) {
        return sync.run(CurrentUser.from(auth));
    }

    @GetMapping("/runs")
    List<SyncView> runs() {
        return sync.recent();
    }
}
