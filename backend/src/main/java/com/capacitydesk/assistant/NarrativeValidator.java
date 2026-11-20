package com.capacitydesk.assistant;

import com.capacitydesk.planning.application.PlanViews.PlanView;
import com.capacitydesk.planning.domain.PlacementEngine.GroupEvaluation;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gate for model output. A narrative is used only if it is short, plain text, free of links and
 * markup, and every number in it appears in the plan. Anything else falls back to the local text.
 */
final class NarrativeValidator {

    static final int MAX_CHARS = 600;
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern FORBIDDEN = Pattern.compile("(?i)https?:|www\\.|[<>`\\[\\]{}]|[\\p{Cntrl}&&[^\\n]]|ignore (all|previous)|approve this|you should approve");

    private NarrativeValidator() {
    }

    /** @return empty if valid, otherwise the reason it was rejected */
    static Optional<String> reject(String text, PlanView plan) {
        if (text == null || text.isBlank()) return Optional.of("EMPTY");
        if (text.length() > MAX_CHARS) return Optional.of("TOO_LONG");
        if (FORBIDDEN.matcher(text).find()) return Optional.of("FORBIDDEN_CONTENT");
        Set<String> allowed = allowedNumbers(plan);
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            if (!allowed.contains(m.group())) return Optional.of("UNKNOWN_NUMBER");
        }
        return Optional.empty();
    }

    static Set<String> allowedNumbers(PlanView p) {
        Set<String> s = new HashSet<>();
        add(s, p.planNumber(), p.requestedServers(), p.allocatedServers(), p.requestedServers() - p.allocatedServers());
        for (GroupEvaluation e : p.evaluation()) {
            add(s, e.freeSlots(), e.freeWatts(), e.maxBySlots(), e.maxByPower(), e.fit(), e.allocated(), e.freeSlots() - e.allocated());
            s.add(String.valueOf(e.freeWatts() / 1000)); // "27 kW" style rounding
        }
        p.allocations().forEach(a -> add(s, a.serverCount()));
        return s;
    }

    private static void add(Set<String> s, long... values) {
        for (long v : values) s.add(Long.toString(v));
    }
}
