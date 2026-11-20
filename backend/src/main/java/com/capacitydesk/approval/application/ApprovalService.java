package com.capacitydesk.approval.application;

import com.capacitydesk.approval.domain.Approval;
import com.capacitydesk.approval.domain.Decision;
import com.capacitydesk.approval.persistence.ApprovalRepository;
import com.capacitydesk.audit.application.AuditService;
import com.capacitydesk.audit.domain.AuditAction;
import com.capacitydesk.audit.domain.AuditResult;
import com.capacitydesk.audit.domain.TargetType;
import com.capacitydesk.capacityrequest.application.CapacityRequestService;
import com.capacitydesk.capacityrequest.application.CapacityRequestService.RequestView;
import com.capacitydesk.capacityrequest.domain.RequestStatus;
import com.capacitydesk.common.web.DomainException;
import com.capacitydesk.identity.application.UserService;
import com.capacitydesk.planning.application.PlanViews.PlanView;
import com.capacitydesk.planning.application.PlanningService;
import com.capacitydesk.planning.domain.PlacementEngine.Feasibility;
import com.capacitydesk.planning.domain.PlacementPlan;
import com.capacitydesk.planning.domain.PlanStatus;
import com.capacitydesk.planning.persistence.PlacementPlanRepository;
import com.capacitydesk.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

    public record QueueItem(PlanView plan, RequestView request) {}

    public record HistoryItem(UUID id, UUID planId, UUID requestId, int planNumber, Decision decision, String reviewerName,
                              String comment, Instant decidedAt, PlanStatus planStatus, int allocatedServers,
                              int requestedServers, String requesterName) {}

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    private final PlacementPlanRepository plans;
    private final ApprovalRepository approvals;
    private final CapacityRequestService requests;
    private final PlanningService planning;
    private final UserService users;
    private final AuditService audit;

    public ApprovalService(PlacementPlanRepository plans, ApprovalRepository approvals, CapacityRequestService requests,
                           PlanningService planning, UserService users, AuditService audit) {
        this.plans = plans;
        this.approvals = approvals;
        this.requests = requests;
        this.planning = planning;
        this.users = users;
        this.audit = audit;
    }

    /** Approval is bound to the immutable proposal: the caller must present the content hash it reviewed. */
    @Transactional
    public PlanView approve(UUID planId, CurrentUser reviewer, String contentHash, String comment) {
        PlacementPlan plan = lockOpenPlan(planId);
        RequestView req = requests.details(plan.getRequestId());
        denySelfDecision(plan, req, reviewer, AuditAction.PLAN_APPROVED);
        if (!plan.getContentHash().equals(contentHash)) {
            throw DomainException.conflict("PLAN_CONTENT_MISMATCH", "Plan content differs from what was reviewed");
        }
        if (plan.getFeasibility() != Feasibility.FEASIBLE) {
            throw DomainException.unprocessable("PLAN_NOT_APPROVABLE", "Only FEASIBLE plans can be approved; this plan is " + plan.getFeasibility());
        }
        plan.transitionTo(PlanStatus.APPROVED);
        approvals.save(new Approval(planId, reviewer.id(), Decision.APPROVED, comment, plan.getContentHash()));
        requests.transition(plan.getRequestId(), RequestStatus.APPROVED);
        audit.success(AuditAction.PLAN_APPROVED, TargetType.PLAN, planId, Map.of("status", PlanStatus.PROPOSED),
                Map.of("status", PlanStatus.APPROVED, "hash", plan.getContentHash()));
        return planning.view(plan);
    }

    @Transactional
    public PlanView reject(UUID planId, CurrentUser reviewer, String comment) {
        PlacementPlan plan = lockOpenPlan(planId);
        RequestView req = requests.details(plan.getRequestId());
        denySelfDecision(plan, req, reviewer, AuditAction.PLAN_REJECTED);
        plan.transitionTo(PlanStatus.REJECTED);
        approvals.save(new Approval(planId, reviewer.id(), Decision.REJECTED, comment, plan.getContentHash()));
        requests.transition(plan.getRequestId(), RequestStatus.REJECTED);
        audit.record(AuditAction.PLAN_REJECTED, TargetType.PLAN, planId.toString(), AuditResult.SUCCESS,
                Map.of("status", PlanStatus.PROPOSED), Map.of("status", PlanStatus.REJECTED), comment);
        return planning.view(plan);
    }

    @Transactional(readOnly = true)
    public PageView<QueueItem> pending(Pageable pageable) {
        Page<PlacementPlan> page = plans.findByStatus(PlanStatus.PROPOSED,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("createdAt")));
        List<QueueItem> items = page.getContent().stream()
                .map(p -> new QueueItem(planning.view(p), requests.details(p.getRequestId()))).toList();
        return new PageView<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageView<HistoryItem> history(Pageable pageable) {
        Page<Approval> page = approvals.findAll(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "decidedAt")));
        Map<UUID, String> names = users.names(page.getContent().stream().map(Approval::getReviewerId).toList());
        List<HistoryItem> items = page.getContent().stream().map(a -> {
            PlacementPlan p = plans.findById(a.getPlanId()).orElseThrow();
            RequestView r = requests.details(p.getRequestId());
            return new HistoryItem(a.getId(), p.getId(), p.getRequestId(), p.getPlanNumber(), a.getDecision(), names.get(a.getReviewerId()),
                    a.getComment(), a.getDecidedAt(), p.getStatus(), p.getAllocatedServers(), p.getRequestedServers(), r.requesterName());
        }).toList();
        return new PageView<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private PlacementPlan lockOpenPlan(UUID planId) {
        PlacementPlan plan = plans.findByIdForUpdate(planId).orElseThrow(() -> DomainException.notFound("Plan"));
        if (plan.getStatus() != PlanStatus.PROPOSED) {
            throw DomainException.conflict("PLAN_NOT_PROPOSED", "Plan is " + plan.getStatus() + " and can no longer be decided");
        }
        return plan;
    }

    private void denySelfDecision(PlacementPlan plan, RequestView req, CurrentUser reviewer, AuditAction action) {
        if (req.requesterId().equals(reviewer.id()) || plan.getCreatedBy().equals(reviewer.id())) {
            audit.recordIndependent(action, TargetType.PLAN, plan.getId().toString(), AuditResult.DENIED, null, null,
                    "self-approval attempt");
            throw DomainException.forbidden("SELF_APPROVAL_FORBIDDEN", "You cannot decide a plan for your own request");
        }
    }
}
