package com.capacitydesk.inventorysync;

import com.capacitydesk.audit.application.AuditService;
import com.capacitydesk.audit.domain.AuditAction;
import com.capacitydesk.audit.domain.AuditResult;
import com.capacitydesk.audit.domain.TargetType;
import com.capacitydesk.common.web.DomainException;
import com.capacitydesk.inventory.application.InventoryService;
import com.capacitydesk.inventory.application.InventoryService.NewServer;
import com.capacitydesk.inventory.application.InventoryViews.RackGroupCapacity;
import com.capacitydesk.inventory.domain.ServerStatus;
import com.capacitydesk.inventory.persistence.ServerRepository;
import com.capacitydesk.inventorysync.ExternalInventorySource.ExternalServer;
import com.capacitydesk.inventorysync.SyncRun.SyncView;
import com.capacitydesk.security.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pulls servers from the external source into the inventory. Creates servers it has not seen and aligns status of known
 * ones; never deletes. Every change goes through {@link InventoryService}, so capacity limits, hostname uniqueness,
 * locking, version bumps (stale plans) and per-change audit all apply. A record that does not fit is skipped, never forced.
 * ponytail: one run at a time per instance (in-process lock); use a DB advisory lock if the backend is ever scaled out.
 */
@Service
public class InventorySyncService {

    private final ExternalInventorySource source;
    private final InventoryService inventory;
    private final ServerRepository servers;
    private final SyncRunRepository runs;
    private final AuditService audit;
    private final MeterRegistry metrics;
    private final TransactionTemplate tx;
    private final ReentrantLock lock = new ReentrantLock();

    public InventorySyncService(ExternalInventorySource source, InventoryService inventory, ServerRepository servers,
                                SyncRunRepository runs, AuditService audit, MeterRegistry metrics,
                                org.springframework.transaction.PlatformTransactionManager txm) {
        this.source = source;
        this.inventory = inventory;
        this.servers = servers;
        this.runs = runs;
        this.audit = audit;
        this.metrics = metrics;
        this.tx = new TransactionTemplate(txm);
    }

    public SyncView run(CurrentUser user) {
        if (!lock.tryLock()) {
            throw DomainException.conflict("SYNC_IN_PROGRESS", "An inventory sync is already running");
        }
        try {
            SyncRun run = runs.save(new SyncRun(source.name(), user.id()));
            audit.recordIndependent(AuditAction.INVENTORY_SYNC_STARTED, TargetType.SYNC_RUN, run.view().id().toString(), AuditResult.SUCCESS, null, null, source.name());
            List<ExternalServer> records;
            try {
                records = source.fetch();
            } catch (Exception e) {
                return finish(run, "FAILED", 0, 0, 0, 0, List.of(), "source unavailable: " + e.getClass().getSimpleName());
            }
            return apply(run, records);
        } finally {
            lock.unlock();
        }
    }

    public List<SyncView> recent() {
        return runs.findAll(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt"))).map(SyncRun::view).getContent();
    }

    private SyncView apply(SyncRun run, List<ExternalServer> records) {
        Map<String, RackGroupCapacity> groups = new HashMap<>();
        for (var dc : inventory.listDataCenters()) {
            for (RackGroupCapacity g : inventory.listRackGroups(dc.id())) groups.put(dc.code() + "/" + g.code(), g);
        }
        int created = 0, updated = 0, unchanged = 0;
        List<String> skipped = new ArrayList<>();
        for (ExternalServer r : records) {
            try {
                RackGroupCapacity g = groups.get(r.dataCenterCode() + "/" + r.rackGroupCode());
                if (g == null) {
                    skipped.add(r.hostname() + ": UNKNOWN_RACK_GROUP " + r.dataCenterCode() + "/" + r.rackGroupCode());
                    continue;
                }
                var existing = servers.findByHostname(r.hostname());
                if (existing.isPresent()) {
                    if (existing.get().getStatus() == r.status()) {
                        unchanged++;
                    } else {
                        inventory.setServerStatus(existing.get().getId(), r.status());
                        updated++;
                    }
                } else {
                    var added = inventory.addServer(new NewServer(r.hostname(), g.id(), r.cpuCores(), r.ramGb(), r.storageTb(), r.powerWatts()));
                    if (r.status() != ServerStatus.ACTIVE) inventory.setServerStatus(added.id(), r.status());
                    created++;
                }
            } catch (DomainException e) {
                skipped.add(r.hostname() + ": " + e.code());
            } catch (RuntimeException e) {
                skipped.add(r.hostname() + ": INVALID_RECORD");
            }
        }
        return finish(run, "SUCCEEDED", created, updated, unchanged, skipped.size(), skipped, null);
    }

    private SyncView finish(SyncRun run, String status, int created, int updated, int unchanged, int skippedCount, List<String> skipped, String error) {
        run.finish(status, created, updated, unchanged, skippedCount, String.join("\n", skipped), error);
        SyncView view = tx.execute(s -> runs.save(run).view());
        metrics.counter("rackwise.inventory.sync", "result", status).increment();
        audit.recordIndependent("SUCCEEDED".equals(status) ? AuditAction.INVENTORY_SYNC_COMPLETED : AuditAction.INVENTORY_SYNC_FAILED,
                TargetType.SYNC_RUN, view.id().toString(), "SUCCEEDED".equals(status) ? AuditResult.SUCCESS : AuditResult.FAILURE, null,
                Map.of("created", created, "updated", updated, "unchanged", unchanged, "skipped", skippedCount), error);
        return view;
    }
}
