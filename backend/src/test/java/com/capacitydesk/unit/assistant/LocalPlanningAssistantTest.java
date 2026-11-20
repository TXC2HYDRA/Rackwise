package com.capacitydesk.unit.assistant;

import static com.capacitydesk.unit.assistant.Plans.group;
import static com.capacitydesk.unit.assistant.Plans.plan;
import static org.assertj.core.api.Assertions.assertThat;

import com.capacitydesk.assistant.LocalPlanningAssistant;
import com.capacitydesk.assistant.PlanningAssistant.Advice;
import com.capacitydesk.assistant.PlanningAssistant.Source;
import com.capacitydesk.assistant.PlanningAssistant.SuggestionType;
import com.capacitydesk.planning.domain.PlanStatus;
import org.junit.jupiter.api.Test;

class LocalPlanningAssistantTest {

    final LocalPlanningAssistant assistant = new LocalPlanningAssistant();

    @Test
    void feasiblePlanIsExplainedWithoutSuggestions() {
        Advice a = assistant.advise(plan(40, 500, null, false, group("X", 55, 27_500_000)));
        assertThat(a.source()).isEqualTo(Source.LOCAL);
        assertThat(a.narrative()).contains("All 40 requested servers fit");
        assertThat(a.suggestions()).isEmpty();
        assertThat(a.fallbackReason()).isNull();
        assertThat(a.disclaimer()).containsIgnoringCase("advisory");
    }

    @Test
    void partialPlanSuggestsReducingAndAllowingOtherGroups() {
        Advice a = assistant.advise(plan(60, 500, "X", false, group("X", 55, 27_500), group("Y", 10, 5_000)));
        assertThat(a.narrative()).contains("Only 55 of 60").contains("partial plan cannot be approved");
        assertThat(a.suggestions()).extracting(s -> s.type()).contains(SuggestionType.REDUCE_REQUEST, SuggestionType.ALLOW_OTHER_RACK_GROUPS);
        assertThat(a.suggestions().stream().filter(s -> s.type() == SuggestionType.REDUCE_REQUEST).findFirst().orElseThrow().text()).contains("55");
        assertThat(a.suggestions().stream().filter(s -> s.type() == SuggestionType.ALLOW_OTHER_RACK_GROUPS).findFirst().orElseThrow().text()).contains("10");
        assertThat(a.risks()).anyMatch(r -> r.contains("5 of 60"));
    }

    @Test
    void powerBoundGroupGetsPowerSuggestion() {
        Advice a = assistant.advise(plan(40, 500, null, false, group("P", 100, 10_000))); // 20 by power, 100 by slots
        assertThat(a.suggestions()).extracting(s -> s.type()).contains(SuggestionType.REDUCE_REQUEST, SuggestionType.LOWER_POWER_PER_SERVER);
        assertThat(a.suggestions().stream().filter(s -> s.type() == SuggestionType.LOWER_POWER_PER_SERVER).findFirst().orElseThrow().text())
                .contains("20 by power vs 100 by slots");
    }

    @Test
    void infeasiblePlanSuggestsAddingCapacity() {
        Advice a = assistant.advise(plan(10, 500, null, false, group("F", 0, 0)));
        assertThat(a.narrative()).contains("None of the 10");
        assertThat(a.suggestions()).extracting(s -> s.type()).containsExactly(SuggestionType.ADD_CAPACITY);
    }

    @Test
    void lowHeadroomIsFlagged() {
        Advice a = assistant.advise(plan(52, 500, null, false, group("X", 55, 27_500_000)));
        assertThat(a.risks()).anyMatch(r -> r.contains("only 3 free slot"));
    }

    @Test
    void stalePlanSaysToRegenerate() {
        Advice a = assistant.advise(plan(10, 500, null, false, PlanStatus.STALE, group("X", 55, 27_500_000)));
        assertThat(a.suggestions()).extracting(s -> s.type()).contains(SuggestionType.REGENERATE_PLAN);
    }

    @Test
    void sameInputGivesSameAdvice() {
        var p = plan(60, 500, "X", false, group("X", 55, 27_500), group("Y", 10, 5_000));
        assertThat(assistant.advise(p)).isEqualTo(assistant.advise(p));
    }
}
