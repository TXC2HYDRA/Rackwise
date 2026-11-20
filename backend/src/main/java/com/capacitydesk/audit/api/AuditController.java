package com.capacitydesk.audit.api;

import com.capacitydesk.audit.application.AuditQueryService;
import com.capacitydesk.audit.domain.AuditAction;
import com.capacitydesk.audit.domain.AuditEvent;
import com.capacitydesk.audit.domain.AuditResult;
import com.capacitydesk.audit.domain.TargetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN sees everything; REVIEWER sees request and plan events only. */
@RestController
@RequestMapping("/api/v1/audit-events")
@PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
class AuditController {

    record AuditEventView(UUID id, Instant occurredAt, UUID actorId, String actorName, AuditAction action,
                          TargetType targetType, String targetId, AuditResult result, String correlationId,
                          String before, String after, String detail) {
        static AuditEventView from(AuditEvent e) {
            return new AuditEventView(e.getId(), e.getOccurredAt(), e.getActorId(), e.getActorName(), e.getAction(),
                    e.getTargetType(), e.getTargetId(), e.getResult(), e.getCorrelationId(),
                    e.getBeforeState(), e.getAfterState(), e.getDetail());
        }
    }

    record PageView(List<AuditEventView> content, int page, int size, long totalElements, int totalPages) {}

    private final AuditQueryService query;

    AuditController(AuditQueryService query) {
        this.query = query;
    }

    @GetMapping
    PageView list(@RequestParam(required = false) AuditAction action,
                  @RequestParam(required = false) TargetType targetType,
                  @RequestParam(required = false) String targetId,
                  @RequestParam(required = false) UUID actorId,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                  @RequestParam(defaultValue = "0") @Min(0) int page,
                  @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
                  Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        Page<AuditEvent> p = query.search(new AuditQueryService.Filter(action, targetType, targetId, actorId, from, to), admin,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
        return new PageView(p.map(AuditEventView::from).getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
