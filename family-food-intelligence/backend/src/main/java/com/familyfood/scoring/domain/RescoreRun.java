package com.familyfood.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One bulk recomputation of {@code product_score} against a rule set
 * (V16__scoring_v2_engine.sql). Progress counters are updated page by page by
 * {@code com.familyfood.scoring.application.RescoreService} so an operator can watch a long
 * run; {@code triggeredBy} records what started it (startup runner, admin, a migration
 * activating a new set).
 */
@Entity
@Table(name = "rescore_run")
public class RescoreRun {

    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    @Id
    private UUID id;

    @Column(name = "rule_set_id", nullable = false)
    private UUID ruleSetId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int done;

    @Column(nullable = false)
    private int failed;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy;

    protected RescoreRun() {
        // JPA
    }

    public static RescoreRun start(UUID ruleSetId, int total, String triggeredBy, Instant now) {
        RescoreRun run = new RescoreRun();
        run.id = UUID.randomUUID();
        run.ruleSetId = ruleSetId;
        run.status = RUNNING;
        run.total = total;
        run.done = 0;
        run.failed = 0;
        run.startedAt = now;
        run.triggeredBy = triggeredBy;
        return run;
    }

    public void progress(int done, int failed) {
        this.done = done;
        this.failed = failed;
    }

    public void finish(boolean success, Instant now) {
        this.status = success ? COMPLETED : FAILED;
        this.finishedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRuleSetId() {
        return ruleSetId;
    }

    public String getStatus() {
        return status;
    }

    public int getTotal() {
        return total;
    }

    public int getDone() {
        return done;
    }

    public int getFailed() {
        return failed;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }
}
