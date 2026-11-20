package com.capacitydesk.inventorysync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_runs")
class SyncRun {

    @Id
    private UUID id = UUID.randomUUID();
    @Column(nullable = false, updatable = false)
    private String source;
    @Column(nullable = false)
    private String status = "RUNNING";
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();
    @Column(name = "finished_at")
    private Instant finishedAt;
    private int created;
    private int updated;
    private int unchanged;
    private int skipped;
    @Column(name = "skip_details")
    private String skipDetails;
    private String error;
    @Column(name = "triggered_by", nullable = false, updatable = false)
    private UUID triggeredBy;

    protected SyncRun() {
    }

    SyncRun(String source, UUID triggeredBy) {
        this.source = source;
        this.triggeredBy = triggeredBy;
    }

    void finish(String status, int created, int updated, int unchanged, int skipped, String skipDetails, String error) {
        this.status = status;
        this.finishedAt = Instant.now();
        this.created = created;
        this.updated = updated;
        this.unchanged = unchanged;
        this.skipped = skipped;
        this.skipDetails = skipDetails;
        this.error = error == null ? null : error.substring(0, Math.min(500, error.length()));
    }

    SyncView view() {
        return new SyncView(id, source, status, startedAt, finishedAt, created, updated, unchanged, skipped,
                skipDetails == null || skipDetails.isEmpty() ? java.util.List.of() : java.util.List.of(skipDetails.split("\n")), error);
    }

    record SyncView(UUID id, String source, String status, Instant startedAt, Instant finishedAt, int created, int updated,
                    int unchanged, int skipped, java.util.List<String> skipDetails, String error) {}
}
