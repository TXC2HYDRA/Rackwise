package com.capacitydesk.planning.api;

import com.capacitydesk.planning.application.PlanViews.PlanView;
import com.capacitydesk.planning.application.PlanningService;
import com.capacitydesk.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class PlanController {

    private final PlanningService planning;

    PlanController(PlanningService planning) {
        this.planning = planning;
    }

    @PostMapping("/capacity-requests/{requestId}/plans")
    @ResponseStatus(HttpStatus.CREATED)
    PlanView generate(@PathVariable UUID requestId, Authentication auth) {
        return planning.generate(requestId, CurrentUser.from(auth));
    }

    @GetMapping("/capacity-requests/{requestId}/plans")
    List<PlanView> list(@PathVariable UUID requestId, Authentication auth) {
        return planning.listForRequest(requestId, CurrentUser.from(auth));
    }

    @GetMapping("/plans/{id}")
    PlanView get(@PathVariable UUID id, Authentication auth) {
        return planning.get(id, CurrentUser.from(auth));
    }
}
