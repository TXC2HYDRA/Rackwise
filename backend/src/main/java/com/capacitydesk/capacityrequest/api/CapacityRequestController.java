package com.capacitydesk.capacityrequest.api;

import com.capacitydesk.capacityrequest.application.CapacityRequestService;
import com.capacitydesk.capacityrequest.application.CapacityRequestService.NewRequest;
import com.capacitydesk.capacityrequest.application.CapacityRequestService.PageView;
import com.capacitydesk.capacityrequest.application.CapacityRequestService.RequestView;
import com.capacitydesk.capacityrequest.domain.Priority;
import com.capacitydesk.capacityrequest.domain.RequestStatus;
import com.capacitydesk.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/capacity-requests")
class CapacityRequestController {

    /** Status and requester are never accepted from the client. */
    record CreateRequest(@NotNull UUID dataCenterId, UUID preferredRackGroupId, boolean allowOtherRackGroups,
                         @Min(1) @Max(500) int serverCount,
                         @Min(8) @Max(128) int cpuCores, @Min(32) @Max(2048) int ramGb,
                         @Min(1) @Max(100) int storageTb, @Min(100) @Max(3000) int powerWatts,
                         @NotBlank @Size(max = 1000) String justification, @NotNull Priority priority) {}

    private final CapacityRequestService service;

    CapacityRequestController(CapacityRequestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('REQUESTER','ADMIN')")
    RequestView create(@Valid @RequestBody CreateRequest r, Authentication auth,
                       @RequestHeader(name = "Idempotency-Key", required = false) @Pattern(regexp = "^[A-Za-z0-9-]{8,64}$") String key) {
        return service.create(CurrentUser.from(auth), new NewRequest(r.dataCenterId(), r.preferredRackGroupId(), r.allowOtherRackGroups(),
                r.serverCount(), r.cpuCores(), r.ramGb(), r.storageTb(), r.powerWatts(), r.justification(), r.priority()), key);
    }

    @GetMapping
    PageView<RequestView> list(@RequestParam(required = false) RequestStatus status,
                               @RequestParam(defaultValue = "0") @Min(0) int page,
                               @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size, Authentication auth) {
        return service.list(CurrentUser.from(auth), status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    RequestView get(@PathVariable UUID id, Authentication auth) {
        return service.getVisible(id, CurrentUser.from(auth));
    }
}
