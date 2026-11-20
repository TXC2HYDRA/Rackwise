package com.capacitydesk.assistant;

import com.capacitydesk.planning.application.PlanViews.PlanView;

/** Optional text generator (an LLM). Failure of any kind is expected and handled by the caller. */
public interface Narrator {
    String narrate(PlanView plan) throws Exception;
}
