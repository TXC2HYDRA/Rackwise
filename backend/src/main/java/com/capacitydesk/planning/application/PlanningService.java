package com.capacitydesk.planning.application;

import com.capacitydesk.audit.application.AuditService;
import com.capacitydesk.audit.domain.AuditAction;
import com.capacitydesk.audit.domain.TargetType;
import com.capacitydesk.capacityrequest.application.CapacityRequestService;
import com.capacitydesk.capacityrequest.application.CapacityRequestService.RequestView;
import com.capacitydesk.capacityrequest.domain.RequestStatus;
import com.capacitydesk.common.web.DomainException;
import com.capacitydesk.inventory.application.InventoryService;
import com.capacitydesk.inventory.application.InventoryViews.RackGroupCapacity;
import com.capacitydesk.planning.application.PlanViews.AllocationView;
import com.capacitydesk.planning.application.PlanViews.PlanView;
import com.capacitydesk.planning.domain.PlacementEngine;
import com.capacitydesk.planning.domain.PlacementPlan;
import com.capacitydesk.planning.domain.PlanStatus;
import com.capacitydesk.planning.persistence.PlacementPlanRepository;
import com.capacitydesk.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PlanningService {

    private final PlacementEngine engine = new PlacementEngine();
    private final PlacementPlanRepository plans;
    private final CapacityRequestService requests;
    private final InventoryService inventory;
    private final AuditService audit;
    private final JsonMapper json;

    public PlanningService(PlacementPlanRepository plans, CapacityRequestService requests, InventoryService inventory,
                           AuditService audit, JsonMapper json) {
        this.plans = plans;
        this.requests = requests;
        this.inventory = inventory;
        this.audit = audit;
        this.json = json;
    }

    /** Only the request owner or an admin may generate plans. Earlier open plans become STALE. */
    @Transactional
    public PlanView generate(UUID requestId, CurrentUser user) {
        RequestView req = requests.getVisible(requestId, user);
        if (!req.requesterId().equals(user.id()) && !user.has("ADMIN")) {
            throw DomainException.notFound("Request");
        }
        if (req.status() == RequestStatus.FULFILLED || req.status() == RequestStatus.REJECTED) {
            throw DomainException.conflict("REQUEST_CLOSED", "Request is " + req.status() + " and cannot be planned again");
        }
        for (PlacementPlan old : plans.findByRequestIdAndStatusIn(requestId, Set.of(PlanStatus.PROPOSED, PlanStatus.APPROVED))) {
            old.transitionTo(PlanStatus.STALE);
            audit.success(AuditAction.PLAN_MARKED_STALE, TargetType.PLAN, old.getId(), Map.of("status", "OPEN"), Map.of("status", PlanStatus.STALE));
        }

        List<PlacementEngine.RackGroupState> states = inventory.listRackGroups(req.dataCenterId()).stream().map(PlanningService::state).toList();
        PlacementEngine.Result result = engine.place(
                new PlacementEngine.Demand(req.serverCount(), req.powerWatts(), req.preferredRackGroupId(), req.allowOtherRackGroups()), states);

        PlacementPlan plan = plans.save(new PlacementPlan(requestId, plans.maxPlanNumber(requestId) + 1, result,
                json.writeValueAsString(result.evaluations()), user.id()));
        if (req.status() == RequestStatus.APPROVED) {
            requests.transition(requestId, RequestStatus.PLANNED);
        } else if (req.status() == RequestStatus.SUBMITTED) {
            requests.transition(requestId, RequestStatus.PLANNED);
        }
        audit.success(AuditAction.PLAN_GENERATED, TargetType.PLAN, plan.getId(), null,
                Map.of("requestId", requestId.toString(), "feasibility", plan.getFeasibility(), "allocated", plan.getAllocatedServers(),
                        "hash", plan.getContentHash()));
        return view(plan);
    }

    @Transactional(readOnly = true)
    public PlanView get(UUID planId, CurrentUser user) {
        PlacementPlan plan = plans.findWithAllocationsById(planId).orElseThrow(() -> DomainException.notFound("Plan"));
        requests.getVisible(plan.getRequestId(), user); // 404 if the caller may not see the parent request
        return view(plan);
    }

    @Transactional(readOnly = true)
    public List<PlanView> listForRequest(UUID requestId, CurrentUser user) {
        requests.getVisible(requestId, user);
        return plans.findByRequestIdOrderByPlanNumberDesc(requestId).stream().map(this::view).toList();
    }

    public PlanView view(PlacementPlan p) {
        List<PlacementEngine.GroupEvaluation> eval = json.readValue(p.getEvaluationJson(), new TypeReference<List<PlacementEngine.GroupEvaluation>>() {});
        return new PlanView(p.getId(), p.getRequestId(), p.getPlanNumber(), p.getStatus(), p.getFeasibility(), p.getRequestedServers(),
                p.getAllocatedServers(), p.getSummary(),
                p.getAllocations().stream().map(a -> new AllocationView(a.getRackGroupId(), a.getRackGroupCode(), a.getDataCenterCode(),
                        a.getServerCount(), a.getRackGroupVersion())).sorted(java.util.Comparator.comparing(AllocationView::rackGroupCode)).toList(),
                eval, p.getContentHash(), p.getCreatedBy(), p.getCreatedAt(), p.getAppliedAt(), p.getFailureReason());
    }

    private static PlacementEngine.RackGroupState state(RackGroupCapacity r) {
        return new PlacementEngine.RackGroupState(r.id(), r.code(), r.dataCenterCode(), r.slotsAvailable(), r.powerAvailableWatts(), r.version());
    }
}
