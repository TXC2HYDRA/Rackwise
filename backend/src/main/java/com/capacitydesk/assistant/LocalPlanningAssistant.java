package com.capacitydesk.assistant;

import com.capacitydesk.planning.application.PlanViews.PlanView;
import com.capacitydesk.planning.domain.PlacementEngine.Binding;
import com.capacitydesk.planning.domain.PlacementEngine.Feasibility;
import com.capacitydesk.planning.domain.PlacementEngine.GroupEvaluation;
import com.capacitydesk.planning.domain.PlanStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Deterministic rules over the plan's own numbers. Same plan in, same advice out. No I/O. */
@Component
public class LocalPlanningAssistant implements PlanningAssistant {

    static final String DISCLAIMER = "Advisory only. Decisions are made by reviewers; the placement result is computed by the deterministic planner.";
    private static final int LOW_HEADROOM_SLOTS = 5;

    @Override
    public Advice advise(PlanView p) {
        List<String> risks = new ArrayList<>();
        List<Suggestion> suggestions = new ArrayList<>();
        int shortfall = p.requestedServers() - p.allocatedServers();

        if (p.status() == PlanStatus.STALE) {
            risks.add("This plan is stale and can no longer be approved or applied.");
            suggestions.add(new Suggestion(SuggestionType.REGENERATE_PLAN, "Generate a new plan from the request."));
        }
        for (GroupEvaluation e : p.evaluation()) {
            int left = e.freeSlots() - e.allocated();
            if (e.allocated() > 0 && left <= LOW_HEADROOM_SLOTS) {
                risks.add("Rack group %s would have only %d free slot(s) left after this plan.".formatted(e.rackGroupCode(), left));
            }
        }

        if (shortfall > 0) {
            risks.add("%d of %d requested servers do not fit.".formatted(shortfall, p.requestedServers()));
            if (p.allocatedServers() > 0) {
                suggestions.add(new Suggestion(SuggestionType.REDUCE_REQUEST,
                        "Request %d servers instead; that amount fits completely.".formatted(p.allocatedServers())));
            }
            int excluded = p.evaluation().stream().filter(e -> e.binding() == Binding.NOT_CONSIDERED).mapToInt(GroupEvaluation::fit).sum();
            if (excluded > 0) {
                suggestions.add(new Suggestion(SuggestionType.ALLOW_OTHER_RACK_GROUPS,
                        "Allow other rack groups in the data center: up to %d more server(s) fit there.".formatted(excluded)));
            }
            p.evaluation().stream().filter(e -> e.binding() == Binding.POWER).findFirst().ifPresent(e -> suggestions.add(
                    new Suggestion(SuggestionType.LOWER_POWER_PER_SERVER,
                            "Rack group %s is limited by power, not slots (%d by power vs %d by slots). Lower power per server or ask an administrator to raise the power limit."
                                    .formatted(e.rackGroupCode(), e.maxByPower(), e.maxBySlots()))));
            if (p.feasibility() == Feasibility.INFEASIBLE && excluded == 0) {
                suggestions.add(new Suggestion(SuggestionType.ADD_CAPACITY, "Ask an administrator to raise rack group limits or add a rack group."));
            }
        }
        return new Advice(Source.LOCAL, narrative(p, shortfall), List.copyOf(risks), List.copyOf(suggestions), null, DISCLAIMER);
    }

    private static String narrative(PlanView p, int shortfall) {
        String where = p.allocations().stream().map(a -> "%d in %s/%s".formatted(a.serverCount(), a.dataCenterCode(), a.rackGroupCode()))
                .reduce((a, b) -> a + ", " + b).orElse("nowhere");
        return switch (p.feasibility()) {
            case FEASIBLE -> "All %d requested servers fit: %s.".formatted(p.requestedServers(), where);
            case PARTIAL -> "Only %d of %d requested servers fit (%s); %d are short. A partial plan cannot be approved.".formatted(p.allocatedServers(), p.requestedServers(), where, shortfall);
            case INFEASIBLE -> "None of the %d requested servers fit within the available slots and power.".formatted(p.requestedServers());
        };
    }
}
