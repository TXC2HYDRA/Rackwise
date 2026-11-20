package com.capacitydesk.approval.api;

import com.capacitydesk.approval.application.ApprovalService;
import com.capacitydesk.approval.application.ApprovalService.HistoryItem;
import com.capacitydesk.approval.application.ApprovalService.PageView;
import com.capacitydesk.approval.application.ApprovalService.QueueItem;
import com.capacitydesk.planning.application.PlanViews.PlanView;
import com.capacitydesk.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class ApprovalController {

    record ApproveRequest(@NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String contentHash, @Size(max = 500) String comment) {}

    record RejectRequest(@NotBlank @Size(max = 500) String comment) {}

    private final ApprovalService approvals;

    ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping("/approvals/pending")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    PageView<QueueItem> pending(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return approvals.pending(PageRequest.of(page, size));
    }

    @GetMapping("/approvals/history")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    PageView<HistoryItem> history(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return approvals.history(PageRequest.of(page, size));
    }

    @PostMapping("/plans/{id}/approve")
    @PreAuthorize("hasRole('REVIEWER')")
    PlanView approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest r, Authentication auth) {
        return approvals.approve(id, CurrentUser.from(auth), r.contentHash(), r.comment());
    }

    @PostMapping("/plans/{id}/reject")
    @PreAuthorize("hasRole('REVIEWER')")
    PlanView reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest r, Authentication auth) {
        return approvals.reject(id, CurrentUser.from(auth), r.comment());
    }
}
