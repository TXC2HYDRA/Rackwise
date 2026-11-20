package com.capacitydesk.inventory.application;

import com.capacitydesk.audit.application.AuditService;
import com.capacitydesk.audit.domain.AuditAction;
import com.capacitydesk.audit.domain.TargetType;
import com.capacitydesk.common.web.DomainException;
import com.capacitydesk.inventory.application.InventoryViews.CapacitySummary;
import com.capacitydesk.inventory.application.InventoryViews.DataCenterView;
import com.capacitydesk.inventory.application.InventoryViews.PageView;
import com.capacitydesk.inventory.application.InventoryViews.RackGroupCapacity;
import com.capacitydesk.inventory.application.InventoryViews.ServerView;
import com.capacitydesk.inventory.domain.DataCenter;
import com.capacitydesk.inventory.domain.RackGroup;
import com.capacitydesk.inventory.domain.Server;
import com.capacitydesk.inventory.domain.ServerStatus;
import com.capacitydesk.inventory.persistence.DataCenterRepository;
import com.capacitydesk.inventory.persistence.RackGroupRepository;
import com.capacitydesk.inventory.persistence.ServerRepository;
import com.capacitydesk.inventory.persistence.UsageRow;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    public record Profile(int cpuCores, int ramGb, int storageTb, int powerWatts) {}

    /** Raised when allocation cannot proceed; {@code stale} distinguishes a changed inventory from a capacity shortfall. */
    public static class AllocationBlockedException extends DomainException {
        private final boolean stale;

        public AllocationBlockedException(boolean stale, String message) {
            super(org.springframework.http.HttpStatus.CONFLICT, stale ? "STALE_INVENTORY" : "NO_CAPACITY", message);
            this.stale = stale;
        }

        public boolean isStale() {
            return stale;
        }
    }

    public record NewServer(String hostname, UUID rackGroupId, int cpuCores, int ramGb, int storageTb, int powerWatts) {}

    private final DataCenterRepository dataCenters;
    private final RackGroupRepository rackGroups;
    private final ServerRepository servers;
    private final AuditService audit;

    public InventoryService(DataCenterRepository dataCenters, RackGroupRepository rackGroups, ServerRepository servers, AuditService audit) {
        this.audit = audit;
        this.dataCenters = dataCenters;
        this.rackGroups = rackGroups;
        this.servers = servers;
    }

    // ---- reads ----

    @Transactional(readOnly = true)
    public List<DataCenterView> listDataCenters() {
        return dataCenters.findAll().stream()
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .map(d -> new DataCenterView(d.getId(), d.getCode(), d.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RackGroupCapacity> listRackGroups(UUID dataCenterId) {
        Map<UUID, UsageRow> usage = servers.usageByRackGroup().stream()
                .collect(Collectors.toMap(UsageRow::rackGroupId, Function.identity()));
        return rackGroups.findAllOrdered(dataCenterId).stream()
                .map(r -> toCapacity(r, usage.get(r.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CapacitySummary capacity(UUID dataCenterId) {
        List<RackGroupCapacity> groups = listRackGroups(dataCenterId);
        return new CapacitySummary(
                groups.stream().mapToLong(RackGroupCapacity::slotCapacity).sum(),
                groups.stream().mapToLong(RackGroupCapacity::slotsUsed).sum(),
                groups.stream().mapToLong(RackGroupCapacity::slotsAvailable).sum(),
                groups.stream().mapToLong(RackGroupCapacity::powerCapacityWatts).sum(),
                groups.stream().mapToLong(RackGroupCapacity::powerUsedWatts).sum(),
                groups.stream().mapToLong(RackGroupCapacity::powerAvailableWatts).sum(),
                groups);
    }

    @Transactional(readOnly = true)
    public RackGroupCapacity getRackGroup(UUID id) {
        RackGroup r = rackGroups.findWithDataCenterById(id).orElseThrow(() -> DomainException.notFound("Rack group"));
        return toCapacity(r, servers.usageOf(id).orElse(null));
    }

    @Transactional(readOnly = true)
    public PageView<ServerView> listServers(UUID rackGroupId, ServerStatus status, Pageable pageable) {
        Specification<Server> spec = Specification.unrestricted();
        if (rackGroupId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("rackGroup").get("id"), rackGroupId));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        Page<Server> page = servers.findAll(spec, pageable);
        return new PageView<>(page.map(InventoryService::toView).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    // ---- admin writes ----

    @Transactional
    public DataCenterView createDataCenter(String code, String name) {
        if (dataCenters.existsByCode(code)) {
            throw DomainException.conflict("DUPLICATE_CODE", "Data center code already exists");
        }
        DataCenter d = dataCenters.save(new DataCenter(code, name));
        audit.success(AuditAction.DATA_CENTER_CREATED, TargetType.DATA_CENTER, d.getId(), null, Map.of("code", code, "name", name));
        return new DataCenterView(d.getId(), d.getCode(), d.getName());
    }

    @Transactional
    public RackGroupCapacity createRackGroup(UUID dataCenterId, String code, String name, int slots, long powerWatts) {
        DataCenter dc = dataCenters.findById(dataCenterId)
                .orElseThrow(() -> DomainException.unprocessable("UNKNOWN_DATA_CENTER", "Data center does not exist"));
        if (rackGroups.existsByDataCenterIdAndCode(dataCenterId, code)) {
            throw DomainException.conflict("DUPLICATE_CODE", "Rack group code already exists in this data center");
        }
        RackGroup saved = rackGroups.save(new RackGroup(dc, code, name, slots, powerWatts));
        audit.success(AuditAction.RACK_GROUP_CREATED, TargetType.RACK_GROUP, saved.getId(), null,
                Map.of("dataCenter", dc.getCode(), "code", code, "slots", slots, "powerWatts", powerWatts));
        return toCapacity(saved, null);
    }

    @Transactional
    public RackGroupCapacity updateLimits(UUID rackGroupId, int slots, long powerWatts) {
        RackGroup r = lock(rackGroupId);
        UsageRow usage = servers.usageOf(rackGroupId).orElse(null);
        if ((usage != null && (usage.servers() > slots || usage.powerWatts() > powerWatts))) {
            throw DomainException.unprocessable("LIMIT_BELOW_USAGE", "New limits are below current usage");
        }
        Object before = Map.of("slots", r.getSlotCapacity(), "powerWatts", r.getPowerCapacityWatts());
        r.changeLimits(slots, powerWatts);
        audit.success(AuditAction.RACK_GROUP_LIMITS_CHANGED, TargetType.RACK_GROUP, rackGroupId, before,
                Map.of("slots", slots, "powerWatts", powerWatts));
        return toCapacity(r, usage);
    }

    @Transactional
    public ServerView addServer(NewServer n) {
        RackGroup r = lock(n.rackGroupId());
        if (servers.existsByHostname(n.hostname())) {
            throw DomainException.conflict("DUPLICATE_HOSTNAME", "Hostname already exists");
        }
        requireRoomFor(r, n.powerWatts());
        Server saved = servers.save(new Server(n.hostname(), r, n.cpuCores(), n.ramGb(), n.storageTb(), n.powerWatts(), ServerStatus.ACTIVE));
        audit.success(AuditAction.SERVER_ADDED, TargetType.SERVER, saved.getId(), null,
                Map.of("hostname", n.hostname(), "rackGroupId", r.getId().toString(), "powerWatts", n.powerWatts()));
        return toView(saved);
    }

    @Transactional
    public ServerView setServerStatus(UUID serverId, ServerStatus status) {
        Server s = servers.findWithRackGroupById(serverId).orElseThrow(() -> DomainException.notFound("Server"));
        RackGroup r = lock(s.getRackGroup().getId());
        if (!s.getStatus().occupiesCapacity() && status.occupiesCapacity()) {
            requireRoomFor(r, s.getPowerWatts());
        }
        ServerStatus old = s.getStatus();
        s.setStatus(status);
        audit.success(AuditAction.SERVER_STATUS_CHANGED, TargetType.SERVER, serverId, Map.of("status", old), Map.of("status", status));
        return toView(s);
    }

    /**
     * Applies one plan allocation. Joins the caller's transaction. Locks the rack group row, requires its version to equal the
     * plan's snapshot, re-checks slots and power, creates the servers and bumps the version.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public int allocateForPlan(UUID rackGroupId, long expectedVersion, int count, Profile p, UUID planId) {
        RackGroup r = rackGroups.findByIdForWrite(rackGroupId)
                .orElseThrow(() -> new AllocationBlockedException(true, "Rack group no longer exists"));
        if (r.getVersion() != expectedVersion) {
            throw new AllocationBlockedException(true, "Rack group " + r.getCode() + " changed since the plan was computed");
        }
        UsageRow u = servers.usageOf(rackGroupId).orElse(new UsageRow(rackGroupId, 0, 0));
        if (u.servers() + count > r.getSlotCapacity() || u.powerWatts() + (long) count * p.powerWatts() > r.getPowerCapacityWatts()) {
            throw new AllocationBlockedException(false, "Rack group " + r.getCode() + " cannot fit the allocation");
        }
        String tag = planId.toString().substring(0, 8);
        for (int i = 1; i <= count; i++) {
            String host = "%s-%s-%s-%03d".formatted(r.getDataCenter().getCode().toLowerCase(), r.getCode().toLowerCase(), tag, i);
            servers.save(new Server(host, r, p.cpuCores(), p.ramGb(), p.storageTb(), p.powerWatts(), ServerStatus.ACTIVE).fromPlan(planId));
        }
        servers.flush();
        rackGroups.bumpVersion(rackGroupId);
        return count;
    }

    private RackGroup lock(UUID rackGroupId) {
        return rackGroups.findByIdForUpdate(rackGroupId)
                .orElseThrow(() -> DomainException.unprocessable("UNKNOWN_RACK_GROUP", "Rack group does not exist"));
    }

    private void requireRoomFor(RackGroup r, int powerWatts) {
        UsageRow u = servers.usageOf(r.getId()).orElse(new UsageRow(r.getId(), 0, 0));
        if (u.servers() + 1 > r.getSlotCapacity()) {
            throw DomainException.unprocessable("NO_SLOT_CAPACITY", "Rack group has no free slot");
        }
        if (u.powerWatts() + powerWatts > r.getPowerCapacityWatts()) {
            throw DomainException.unprocessable("NO_POWER_CAPACITY", "Rack group power budget exceeded");
        }
    }

    static RackGroupCapacity toCapacity(RackGroup r, UsageRow u) {
        int used = u == null ? 0 : (int) u.servers();
        long power = u == null ? 0 : u.powerWatts();
        return new RackGroupCapacity(r.getId(), r.getDataCenter().getId(), r.getDataCenter().getCode(), r.getCode(), r.getName(),
                r.getSlotCapacity(), used, r.getSlotCapacity() - used,
                r.getPowerCapacityWatts(), power, r.getPowerCapacityWatts() - power, r.getVersion());
    }

    static ServerView toView(Server s) {
        RackGroup r = s.getRackGroup();
        return new ServerView(s.getId(), s.getHostname(), r.getId(), r.getCode(), r.getDataCenter().getCode(),
                s.getCpuCores(), s.getRamGb(), s.getStorageTb(), s.getPowerWatts(), s.getStatus());
    }
}
