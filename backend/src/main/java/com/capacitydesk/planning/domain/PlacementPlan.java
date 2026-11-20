package com.capacitydesk.planning.domain;

import com.capacitydesk.common.persistence.TimestampedEntity;
import com.capacitydesk.common.web.DomainException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * A proposal. Content (allocations, counts, hash) is fixed at creation; only status and
 * execution outcome change, via {@link #transitionTo}.
 */
@Entity
@Table(name = "placement_plans")
public class PlacementPlan extends TimestampedEntity {

    @Id
    private UUID id;
    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;
    @Column(name = "plan_number", nullable = false, updatable = false)
    private int planNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status = PlanStatus.PROPOSED;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PlacementEngine.Feasibility feasibility;
    @Column(name = "requested_servers", nullable = false, updatable = false)
    private int requestedServers;
    @Column(name = "allocated_servers", nullable = false, updatable = false)
    private int allocatedServers;
    @Column(nullable = false, updatable = false)
    private String summary;
    @Column(name = "evaluation_json", nullable = false, updatable = false)
    private String evaluationJson;
    @Column(name = "content_hash", nullable = false, updatable = false)
    private String contentHash;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "applied_at")
    private Instant appliedAt;
    @Column(name = "applied_by")
    private UUID appliedBy;
    @Column(name = "failure_reason")
    private String failureReason;
    @Version
    private long version;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL)
    private List<PlanAllocation> allocations = new ArrayList<>();

    protected PlacementPlan() {
    }

    public PlacementPlan(UUID requestId, int planNumber, PlacementEngine.Result r, String evaluationJson, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.requestId = requestId;
        this.planNumber = planNumber;
        this.feasibility = r.feasibility();
        this.requestedServers = r.requested();
        this.allocatedServers = r.allocated();
        this.summary = r.summary();
        this.evaluationJson = evaluationJson;
        this.createdBy = createdBy;
        r.allocations().forEach(a -> allocations.add(new PlanAllocation(this, a)));
        this.contentHash = hash(requestId, r);
    }

    /** SHA-256 over the immutable proposal content. Approval is bound to this value. */
    static String hash(UUID requestId, PlacementEngine.Result r) {
        StringBuilder sb = new StringBuilder(requestId.toString()).append('|').append(r.feasibility()).append('|')
                .append(r.requested()).append('|').append(r.allocated());
        r.allocations().stream().sorted(Comparator.comparing(a -> a.rackGroupId().toString()))
                .forEach(a -> sb.append('|').append(a.rackGroupId()).append(':').append(a.servers()).append('@').append(a.rackGroupVersion()));
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void transitionTo(PlanStatus next) {
        if (!status.next().contains(next)) {
            throw DomainException.conflict("ILLEGAL_PLAN_TRANSITION", "Plan cannot move from " + status + " to " + next);
        }
        status = next;
    }

    public void markApplied(UUID by) {
        transitionTo(PlanStatus.APPLIED);
        appliedAt = Instant.now();
        appliedBy = by;
    }

    public void markFailed(String reason) {
        transitionTo(PlanStatus.FAILED);
        failureReason = reason == null ? null : reason.substring(0, Math.min(500, reason.length()));
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public int getPlanNumber() { return planNumber; }
    public PlanStatus getStatus() { return status; }
    public PlacementEngine.Feasibility getFeasibility() { return feasibility; }
    public int getRequestedServers() { return requestedServers; }
    public int getAllocatedServers() { return allocatedServers; }
    public String getSummary() { return summary; }
    public String getEvaluationJson() { return evaluationJson; }
    public String getContentHash() { return contentHash; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getAppliedAt() { return appliedAt; }
    public String getFailureReason() { return failureReason; }
    public List<PlanAllocation> getAllocations() { return List.copyOf(allocations); }
    public long getVersion() { return version; }
}
