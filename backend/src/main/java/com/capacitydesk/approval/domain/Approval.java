package com.capacitydesk.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "approvals")
public class Approval {

    @Id
    private UUID id;
    @Column(name = "plan_id", nullable = false)
    private UUID planId;
    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;
    private String comment;
    @Column(name = "content_hash", nullable = false)
    private String contentHash;
    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected Approval() {
    }

    public Approval(UUID planId, UUID reviewerId, Decision decision, String comment, String contentHash) {
        this.id = UUID.randomUUID();
        this.planId = planId;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.comment = comment;
        this.contentHash = contentHash;
        this.decidedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPlanId() { return planId; }
    public UUID getReviewerId() { return reviewerId; }
    public Decision getDecision() { return decision; }
    public String getComment() { return comment; }
    public String getContentHash() { return contentHash; }
    public Instant getDecidedAt() { return decidedAt; }
}
