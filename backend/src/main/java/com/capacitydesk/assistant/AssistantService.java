package com.capacitydesk.assistant;

import com.capacitydesk.assistant.PlanningAssistant.Advice;
import com.capacitydesk.assistant.PlanningAssistant.Source;
import com.capacitydesk.planning.application.PlanViews.PlanView;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Local advice always; an LLM narrative replaces the local text only when it arrives in time and
 * passes {@link NarrativeValidator}. Retries are bounded; any failure degrades to local advice.
 */
@Service
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final LocalPlanningAssistant local;
    private final Optional<Narrator> narrator;
    private final int retries;
    private final MeterRegistry metrics;

    public AssistantService(LocalPlanningAssistant local, Optional<Narrator> narrator, AssistantProperties props, MeterRegistry metrics) {
        this.local = local;
        this.narrator = narrator;
        this.retries = Math.max(0, props.llm().retries());
        this.metrics = metrics;
    }

    public Advice advise(PlanView plan) {
        Advice base = local.advise(plan);
        if (narrator.isEmpty()) return count(base);

        String reason = "UNKNOWN";
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                String text = narrator.get().narrate(plan);
                Optional<String> rejected = NarrativeValidator.reject(text, plan);
                if (rejected.isEmpty()) {
                    return count(new Advice(Source.LLM_NARRATIVE, text.strip(), base.risks(), base.suggestions(), null, base.disclaimer()));
                }
                reason = "NARRATIVE_" + rejected.get();
                break; // a bad answer is not retried; the next one would be no safer
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                reason = "INTERRUPTED";
                break;
            } catch (Exception e) {
                reason = e instanceof java.net.http.HttpTimeoutException ? "TIMEOUT" : "LLM_ERROR";
                log.warn("assistant LLM attempt {} failed: {}", attempt + 1, e.getClass().getSimpleName());
            }
        }
        metrics.counter("assistant.fallback", "reason", reason).increment();
        return count(new Advice(Source.LOCAL, base.narrative(), base.risks(), base.suggestions(), reason, base.disclaimer()));
    }

    private Advice count(Advice a) {
        metrics.counter("assistant.advice", "source", a.source().name()).increment();
        return a;
    }
}
