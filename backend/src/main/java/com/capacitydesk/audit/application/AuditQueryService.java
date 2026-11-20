package com.capacitydesk.audit.application;

import com.capacitydesk.audit.domain.AuditAction;
import com.capacitydesk.audit.domain.AuditEvent;
import com.capacitydesk.audit.domain.TargetType;
import com.capacitydesk.audit.persistence.AuditEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

    /** What a REVIEWER may see: the request/plan lifecycle only. */
    private static final List<TargetType> REVIEWER_VISIBLE = List.of(TargetType.CAPACITY_REQUEST, TargetType.PLAN);

    public record Filter(AuditAction action, TargetType targetType, String targetId, UUID actorId, Instant from, Instant to) {}

    private final AuditEventRepository events;

    public AuditQueryService(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> search(Filter f, boolean adminView, Pageable pageable) {
        Specification<AuditEvent> spec = Specification.unrestricted();
        if (!adminView) {
            spec = spec.and((r, q, cb) -> r.get("targetType").in(REVIEWER_VISIBLE));
        }
        if (f.action() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("action"), f.action()));
        if (f.targetType() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("targetType"), f.targetType()));
        if (f.targetId() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("targetId"), f.targetId()));
        if (f.actorId() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("actorId"), f.actorId()));
        if (f.from() != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("occurredAt"), f.from()));
        if (f.to() != null) spec = spec.and((r, q, cb) -> cb.lessThan(r.get("occurredAt"), f.to()));
        return events.findAll(spec, pageable);
    }
}
