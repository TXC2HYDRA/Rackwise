package com.capacitydesk.assistant;

import com.capacitydesk.assistant.PlanningAssistant.Advice;
import com.capacitydesk.planning.application.PlanningService;
import com.capacitydesk.security.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
class AssistantController {

    private final PlanningService planning;
    private final AssistantService assistant;

    AssistantController(PlanningService planning, AssistantService assistant) {
        this.planning = planning;
        this.assistant = assistant;
    }

    /** Same visibility rules as the plan itself (404 for plans the caller may not see). Read-only. */
    @GetMapping("/{id}/advice")
    Advice advice(@PathVariable UUID id, Authentication auth) {
        return assistant.advise(planning.get(id, CurrentUser.from(auth)));
    }
}
