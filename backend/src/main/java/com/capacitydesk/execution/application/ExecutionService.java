package com.capacitydesk.execution.application;

import com.capacitydesk.audit.application.AuditService;
import com.capacitydesk.audit.domain.AuditAction;
import com.capacitydesk.audit.domain.AuditResult;
import com.capacitydesk.audit.domain.TargetType;
import com.capacitydesk.capacityrequest.application.CapacityRequestService;
import com.capacitydesk.capacityrequest.application.CapacityRequestService.RequestView;
import com.capacitydesk.capacityrequest.domain.RequestStatus;
import com.capacitydesk.common.web.DomainException;
import com.capacitydesk.inventory.application.InventoryService;
import com.capacitydesk.inventory.application.InventoryService.AllocationBlockedException;
import com.capacitydesk.planning.application.PlanViews.PlanView;
import com.capacitydesk.planning.application.PlanningService;
import com.capacitydesk.planning.domain.PlacementPlan;
import com.capacitydesk.planning.domain.PlanAllocation;
import com.capacitydesk.planning.domain.PlanStatus;
import com.capacitydesk.planning.persistence.PlacementPlanRepository;
import com.capacitydesk.security.CurrentUser;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies an APPROVED plan to the simulated inventory. Deterministic, transactional, and the only code path that creates
 * servers from a plan. Nothing here is reachable by the AI layer.
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    public record ApplyResult(PlanView plan, int serversCreated) {}

    private final PlacementPlanRepository plans;
    private final CapacityRequestService requests;
    private final InventoryService inventory;
    private final PlanningService planning;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public ExecutionService(PlacementPlanRepository plans, CapacityRequestService requests, InventoryService inventory,
                            PlanningService planning, AuditService audit, PlatformTransactionManager txManager) {
        this.plans = plans;
        this.requests = requests;
        this.inventory = inventory;
        this.planning = planning;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
    }

    public ApplyResult apply(UUID planId, CurrentUser user) {
        try {
            return tx.execute(s -> doApply(planId, user));
        } catch (AllocationBlockedException e) {
            // The apply transaction rolled back; record the outcome in a fresh one.
            tx.executeWithoutResult(s -> recordBlocked(planId, e));
            throw e;
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Plan application failed unexpectedly", e);
            audit.recordIndependent(AuditAction.PLAN_APPLY_FAILED, TargetType.PLAN, planId.toString(), AuditResult.FAILURE, null, null, "unexpected error");
            throw e;
        }
    }

    private ApplyResult doApply(UUID planId, CurrentUser user) {
        PlacementPlan plan = plans.findByIdForUpdate(planId).orElseThrow(() -> DomainException.notFound("Plan"));
        switch (plan.getStatus()) {
            case APPLIED -> throw DomainException.conflict("ALREADY_APPLIED", "Plan was already applied");
            case APPROVED -> { }
            default -> throw DomainException.conflict("PLAN_NOT_APPROVED", "Plan is " + plan.getStatus() + "; only APPROVED plans can be applied");
        }
        RequestView req = requests.details(plan.getRequestId());
        InventoryService.Profile profile = new InventoryService.Profile(req.cpuCores(), req.ramGb(), req.storageTb(), req.powerWatts());

        // Deterministic lock order (by rack group id) so concurrent plans cannot deadlock.
        List<PlanAllocation> ordered = plan.getAllocations().stream().sorted(Comparator.comparing(a -> a.getRackGroupId().toString())).toList();
        int created = 0;
        for (PlanAllocation a : ordered) {
            created += inventory.allocateForPlan(a.getRackGroupId(), a.getRackGroupVersion(), a.getServerCount(), profile, planId);
        }
        plan.markApplied(user.id());
        requests.transition(plan.getRequestId(), RequestStatus.FULFILLED);
        audit.success(AuditAction.PLAN_APPLIED, TargetType.PLAN, planId, Map.of("status", PlanStatus.APPROVED),
                Map.of("status", PlanStatus.APPLIED, "serversCreated", created));
        return new ApplyResult(planning.view(plan), created);
    }

    private void recordBlocked(UUID planId, AllocationBlockedException e) {
        PlacementPlan plan = plans.findByIdForUpdate(planId).orElseThrow();
        if (plan.getStatus() != PlanStatus.APPROVED) {
            return;
        }
        if (e.isStale()) {
            plan.transitionTo(PlanStatus.STALE);
            audit.record(AuditAction.PLAN_MARKED_STALE, TargetType.PLAN, planId.toString(), AuditResult.FAILURE,
                    Map.of("status", PlanStatus.APPROVED), Map.of("status", PlanStatus.STALE), e.getMessage());
        } else {
            plan.markFailed(e.getMessage());
            audit.record(AuditAction.PLAN_APPLY_FAILED, TargetType.PLAN, planId.toString(), AuditResult.FAILURE,
                    Map.of("status", PlanStatus.APPROVED), Map.of("status", PlanStatus.FAILED), e.getMessage());
        }
        requests.transition(plan.getRequestId(), RequestStatus.PLANNED); // needs a fresh plan
    }
}
