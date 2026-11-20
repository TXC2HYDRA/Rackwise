package com.capacitydesk.capacityrequest.application;

import com.capacitydesk.audit.application.AuditService;
import com.capacitydesk.audit.domain.AuditAction;
import com.capacitydesk.audit.domain.TargetType;
import com.capacitydesk.capacityrequest.domain.CapacityRequest;
import com.capacitydesk.capacityrequest.domain.Priority;
import com.capacitydesk.capacityrequest.domain.RequestStatus;
import com.capacitydesk.capacityrequest.persistence.CapacityRequestRepository;
import com.capacitydesk.common.web.DomainException;
import com.capacitydesk.identity.application.UserService;
import com.capacitydesk.inventory.application.InventoryService;
import com.capacitydesk.inventory.application.InventoryViews.RackGroupCapacity;
import com.capacitydesk.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapacityRequestService {

    public record NewRequest(UUID dataCenterId, UUID preferredRackGroupId, boolean allowOtherRackGroups, int serverCount,
                             int cpuCores, int ramGb, int storageTb, int powerWatts, String justification, Priority priority) {}

    public record RequestView(UUID id, UUID requesterId, String requesterName, UUID dataCenterId, UUID preferredRackGroupId,
                              boolean allowOtherRackGroups, int serverCount, int cpuCores, int ramGb, int storageTb,
                              int powerWatts, String justification, Priority priority, RequestStatus status, Instant createdAt) {}

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    private final CapacityRequestRepository requests;
    private final InventoryService inventory;
    private final UserService users;
    private final AuditService audit;

    public CapacityRequestService(CapacityRequestRepository requests, InventoryService inventory, UserService users, AuditService audit) {
        this.requests = requests;
        this.inventory = inventory;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public RequestView create(CurrentUser user, NewRequest n, String idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = requests.findByRequesterIdAndIdempotencyKey(user.id(), idempotencyKey);
            if (existing.isPresent()) {
                return view(existing.get(), Map.of(user.id(), users.get(user.id()).getUsername()));
            }
        }
        if (inventory.listDataCenters().stream().noneMatch(d -> d.id().equals(n.dataCenterId()))) {
            throw DomainException.unprocessable("UNKNOWN_DATA_CENTER", "Data center does not exist");
        }
        if (n.preferredRackGroupId() != null) {
            RackGroupCapacity rg = inventory.listRackGroups(n.dataCenterId()).stream()
                    .filter(r -> r.id().equals(n.preferredRackGroupId())).findFirst()
                    .orElseThrow(() -> DomainException.unprocessable("UNKNOWN_RACK_GROUP", "Rack group does not exist in that data center"));
        }
        CapacityRequest r = new CapacityRequest(user.id(), n.dataCenterId(), n.preferredRackGroupId(), n.allowOtherRackGroups(),
                n.serverCount(), n.cpuCores(), n.ramGb(), n.storageTb(), n.powerWatts(), n.justification(), n.priority(), idempotencyKey);
        try {
            requests.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw DomainException.conflict("DUPLICATE_SUBMISSION", "A request with this Idempotency-Key already exists");
        }
        audit.success(AuditAction.REQUEST_CREATED, TargetType.CAPACITY_REQUEST, r.getId(), null,
                Map.of("servers", n.serverCount(), "dataCenterId", n.dataCenterId().toString(), "priority", n.priority()));
        return view(r, Map.of(user.id(), users.get(user.id()).getUsername()));
    }

    @Transactional(readOnly = true)
    public RequestView getVisible(UUID id, CurrentUser user) {
        CapacityRequest r = load(id, user);
        return view(r, Map.of(r.getRequesterId(), users.get(r.getRequesterId()).getUsername()));
    }

    @Transactional(readOnly = true)
    public PageView<RequestView> list(CurrentUser user, RequestStatus status, Pageable pageable) {
        Specification<CapacityRequest> spec = Specification.unrestricted();
        if (!user.canSeeAll()) spec = spec.and((r, q, cb) -> cb.equal(r.get("requesterId"), user.id()));
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        Page<CapacityRequest> page = requests.findAll(spec, pageable);
        Map<UUID, String> names = names(page.getContent());
        return new PageView<>(page.map(r -> view(r, names)).getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Internal: used by planning/approval/execution. Locks the request row to serialize status changes. */
    @Transactional
    public RequestView transition(UUID id, RequestStatus next) {
        CapacityRequest r = requests.findByIdForUpdate(id).orElseThrow(() -> DomainException.notFound("Request"));
        r.transitionTo(next);
        return view(r, Map.of(r.getRequesterId(), users.get(r.getRequesterId()).getUsername()));
    }

    /** Internal: no visibility check. */
    @Transactional(readOnly = true)
    public RequestView details(UUID id) {
        CapacityRequest r = requests.findById(id).orElseThrow(() -> DomainException.notFound("Request"));
        return view(r, Map.of(r.getRequesterId(), users.get(r.getRequesterId()).getUsername()));
    }

    private CapacityRequest load(UUID id, CurrentUser user) {
        CapacityRequest r = requests.findById(id).orElseThrow(() -> DomainException.notFound("Request"));
        // 404, not 403, so requesters cannot probe other people's request ids
        if (!user.canSeeAll() && !r.getRequesterId().equals(user.id())) {
            throw DomainException.notFound("Request");
        }
        return r;
    }

    private Map<UUID, String> names(List<CapacityRequest> list) {
        Set<UUID> ids = list.stream().map(CapacityRequest::getRequesterId).collect(Collectors.toSet());
        return users.names(ids);
    }

    private static RequestView view(CapacityRequest r, Map<UUID, String> names) {
        return new RequestView(r.getId(), r.getRequesterId(), names.get(r.getRequesterId()), r.getDataCenterId(),
                r.getPreferredRackGroupId(), r.isAllowOtherRackGroups(), r.getServerCount(), r.getCpuCores(), r.getRamGb(),
                r.getStorageTb(), r.getPowerWatts(), r.getJustification(), r.getPriority(), r.getStatus(), r.getCreatedAt());
    }
}
