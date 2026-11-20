package com.capacitydesk.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Append-only: no setters, never updated. */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditResult result;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "before_state")
    private String beforeState;

    @Column(name = "after_state")
    private String afterState;

    private String detail;

    protected AuditEvent() {
    }

    public AuditEvent(UUID actorId, String actorName, AuditAction action, TargetType targetType, String targetId,
                      AuditResult result, String correlationId, String beforeState, String afterState, String detail) {
        this.id = UUID.randomUUID();
        this.occurredAt = Instant.now();
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.result = result;
        this.correlationId = correlationId;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.detail = detail;
    }

    public UUID getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public AuditAction getAction() { return action; }
    public TargetType getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public AuditResult getResult() { return result; }
    public String getCorrelationId() { return correlationId; }
    public String getBeforeState() { return beforeState; }
    public String getAfterState() { return afterState; }
    public String getDetail() { return detail; }
}
