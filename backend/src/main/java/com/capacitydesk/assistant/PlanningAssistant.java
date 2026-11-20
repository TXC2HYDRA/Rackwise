package com.capacitydesk.assistant;

import com.capacitydesk.planning.application.PlanViews.PlanView;
import java.util.List;

/**
 * Advisory only. An assistant explains a plan and suggests what-ifs; it never creates, approves,
 * rejects or applies anything, and no decision path reads its output.
 */
public interface PlanningAssistant {

    enum Source { LOCAL, LLM_NARRATIVE }

    enum SuggestionType { REDUCE_REQUEST, ALLOW_OTHER_RACK_GROUPS, LOWER_POWER_PER_SERVER, ADD_CAPACITY, REGENERATE_PLAN }

    record Suggestion(SuggestionType type, String text) {}

    /**
     * @param narrative   plain-text explanation; from the LLM only when it passed validation
     * @param fallbackReason set when an LLM was configured but its answer was not used
     */
    record Advice(Source source, String narrative, List<String> risks, List<Suggestion> suggestions,
                  String fallbackReason, String disclaimer) {}

    Advice advise(PlanView plan);
}
