package com.capacitydesk.execution.api;

import com.capacitydesk.execution.application.ExecutionService;
import com.capacitydesk.execution.application.ExecutionService.ApplyResult;
import com.capacitydesk.security.CurrentUser;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
class ExecutionController {

    private final ExecutionService execution;

    ExecutionController(ExecutionService execution) {
        this.execution = execution;
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    ApplyResult apply(@PathVariable UUID id, Authentication auth) {
        return execution.apply(id, CurrentUser.from(auth));
    }
}
