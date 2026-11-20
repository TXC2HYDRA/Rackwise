package com.capacitydesk.assistant;

import com.capacitydesk.planning.application.PlanViews.PlanView;
import java.util.Optional;

/** Test-only door into the package-private validator. */
public final class NarrativeValidatorAccess {
    private NarrativeValidatorAccess() {
    }

    public static Optional<String> reject(String text, PlanView plan) {
        return NarrativeValidator.reject(text, plan);
    }
}
