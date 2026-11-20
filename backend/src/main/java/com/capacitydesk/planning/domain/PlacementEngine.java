package com.capacitydesk.planning.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic placement. Pure function of its inputs; no I/O, no randomness, no AI.
 * Greedy: preferred rack group first, then (only if allowed) others by largest fit, ties by code.
 * ponytail: greedy by fit, not best-fit/optimal packing; upgrade to a knapsack/ILP only if fragmentation matters.
 */
public final class PlacementEngine {

    public enum Feasibility { FEASIBLE, PARTIAL, INFEASIBLE }

    public enum Binding { NONE, SLOTS, POWER, NOT_CONSIDERED }

    public record RackGroupState(UUID id, String code, String dataCenterCode, int freeSlots, long freeWatts, long version) {}

    public record Demand(int servers, int powerWattsPerServer, UUID preferredRackGroupId, boolean allowOtherRackGroups) {}

    public record GroupEvaluation(UUID rackGroupId, String rackGroupCode, int freeSlots, long freeWatts,
                                  int maxBySlots, int maxByPower, int fit, int allocated, Binding binding) {}

    public record Allocation(UUID rackGroupId, String rackGroupCode, String dataCenterCode, int servers, long rackGroupVersion) {}

    public record Result(Feasibility feasibility, int requested, int allocated, List<Allocation> allocations,
                         List<GroupEvaluation> evaluations, String summary) {}

    public Result place(Demand demand, List<RackGroupState> groups) {
        if (demand.servers() < 1 || demand.powerWattsPerServer() < 1) {
            throw new IllegalArgumentException("servers and power per server must be positive");
        }
        List<RackGroupState> ordered = groups.stream()
                .sorted(Comparator.comparingInt((RackGroupState g) -> -fit(g, demand.powerWattsPerServer()))
                        .thenComparing(RackGroupState::code).thenComparing(RackGroupState::id))
                .toList();

        List<RackGroupState> candidates = new ArrayList<>();
        for (RackGroupState g : ordered) {
            boolean preferred = g.id().equals(demand.preferredRackGroupId());
            if (preferred) candidates.add(0, g);
            else if (demand.preferredRackGroupId() == null || demand.allowOtherRackGroups()) candidates.add(g);
        }

        int remaining = demand.servers();
        List<Allocation> allocations = new ArrayList<>();
        List<GroupEvaluation> evaluations = new ArrayList<>();
        for (RackGroupState g : candidates) {
            int fit = fit(g, demand.powerWattsPerServer());
            int take = Math.min(remaining, fit);
            remaining -= take;
            if (take > 0) allocations.add(new Allocation(g.id(), g.code(), g.dataCenterCode(), take, g.version()));
            evaluations.add(evaluation(g, demand.powerWattsPerServer(), take, true));
        }
        for (RackGroupState g : ordered) {
            if (!candidates.contains(g)) evaluations.add(evaluation(g, demand.powerWattsPerServer(), 0, false));
        }

        int allocated = demand.servers() - remaining;
        Feasibility f = allocated == demand.servers() ? Feasibility.FEASIBLE : allocated == 0 ? Feasibility.INFEASIBLE : Feasibility.PARTIAL;
        return new Result(f, demand.servers(), allocated, List.copyOf(allocations), List.copyOf(evaluations), summary(f, demand, allocated, evaluations));
    }

    private static int fit(RackGroupState g, int power) {
        return Math.min(Math.max(0, g.freeSlots()), maxByPower(g, power));
    }

    private static int maxByPower(RackGroupState g, int power) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, g.freeWatts()) / power);
    }

    private static GroupEvaluation evaluation(RackGroupState g, int power, int allocated, boolean considered) {
        int bySlots = Math.max(0, g.freeSlots());
        int byPower = maxByPower(g, power);
        Binding b = !considered ? Binding.NOT_CONSIDERED : byPower < bySlots ? Binding.POWER : Binding.SLOTS;
        return new GroupEvaluation(g.id(), g.code(), g.freeSlots(), g.freeWatts(), bySlots, byPower, Math.min(bySlots, byPower), allocated, b);
    }

    private static String summary(Feasibility f, Demand d, int allocated, List<GroupEvaluation> evals) {
        return switch (f) {
            case FEASIBLE -> "Placed all %d servers across %d rack group(s).".formatted(allocated, evals.stream().filter(e -> e.allocated() > 0).count());
            case PARTIAL -> "PARTIAL: only %d of %d servers fit; %d short. Not approvable as a full fulfilment.".formatted(allocated, d.servers(), d.servers() - allocated);
            case INFEASIBLE -> "INFEASIBLE: no requested server fits within the available slot and power capacity.";
        };
    }
}
