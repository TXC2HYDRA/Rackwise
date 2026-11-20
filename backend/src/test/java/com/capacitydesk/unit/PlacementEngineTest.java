package com.capacitydesk.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capacitydesk.planning.domain.PlacementEngine;
import com.capacitydesk.planning.domain.PlacementEngine.Binding;
import com.capacitydesk.planning.domain.PlacementEngine.Demand;
import com.capacitydesk.planning.domain.PlacementEngine.Feasibility;
import com.capacitydesk.planning.domain.PlacementEngine.RackGroupState;
import com.capacitydesk.planning.domain.PlacementEngine.Result;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlacementEngineTest {

    private final PlacementEngine engine = new PlacementEngine();

    private static UUID id(int n) {
        return new UUID(0, n);
    }

    private static RackGroupState rg(int n, String code, int freeSlots, long freeWatts) {
        return new RackGroupState(id(n), code, "DC-A", freeSlots, freeWatts, 7L);
    }

    @Test
    void sufficientCapacityInPreferredGroupIsFeasible() {
        Result r = engine.place(new Demand(40, 500, id(1), false), List.of(rg(1, "X", 55, 27_500), rg(2, "Y", 10, 5_000)));
        assertThat(r.feasibility()).isEqualTo(Feasibility.FEASIBLE);
        assertThat(r.allocated()).isEqualTo(40);
        assertThat(r.allocations()).singleElement().satisfies(a -> {
            assertThat(a.rackGroupCode()).isEqualTo("X");
            assertThat(a.servers()).isEqualTo(40);
            assertThat(a.rackGroupVersion()).isEqualTo(7L);
        });
    }

    @Test
    void insufficientSlotsInPreferredOnlyIsPartialAndNeverLabelledSuccess() {
        Result r = engine.place(new Demand(70, 500, id(1), false), List.of(rg(1, "X", 55, 100_000), rg(2, "Y", 100, 100_000)));
        assertThat(r.feasibility()).isEqualTo(Feasibility.PARTIAL);
        assertThat(r.allocated()).isEqualTo(55);
        assertThat(r.summary()).startsWith("PARTIAL").contains("15 short");
        assertThat(r.evaluations()).anySatisfy(e -> {
            assertThat(e.rackGroupCode()).isEqualTo("Y");
            assertThat(e.binding()).isEqualTo(Binding.NOT_CONSIDERED);
            assertThat(e.allocated()).isZero();
        });
    }

    @Test
    void powerCanBeTheBindingConstraint() {
        Result r = engine.place(new Demand(30, 500, id(3), false), List.of(rg(3, "Z", 50, 7_000)));
        assertThat(r.feasibility()).isEqualTo(Feasibility.PARTIAL);
        assertThat(r.allocated()).isEqualTo(14);
        assertThat(r.evaluations().get(0).binding()).isEqualTo(Binding.POWER);
        assertThat(r.evaluations().get(0).maxByPower()).isEqualTo(14);
    }

    @Test
    void spilloverFillsPreferredFirstThenLargestFit() {
        Result r = engine.place(new Demand(70, 500, id(1), true),
                List.of(rg(1, "X", 40, 100_000), rg(2, "Y", 10, 100_000), rg(3, "Z", 25, 100_000)));
        assertThat(r.feasibility()).isEqualTo(Feasibility.FEASIBLE);
        assertThat(r.allocations()).extracting(a -> a.rackGroupCode() + ":" + a.servers()).containsExactly("X:40", "Z:25", "Y:5");
    }

    @Test
    void noPreferenceUsesLargestFitFirstAndPrefersSingleGroup() {
        Result r = engine.place(new Demand(20, 500, null, false), List.of(rg(1, "A", 10, 100_000), rg(2, "B", 30, 100_000)));
        assertThat(r.allocations()).singleElement().satisfies(a -> assertThat(a.rackGroupCode()).isEqualTo("B"));
    }

    @Test
    void noFeasibleRackGroupsIsInfeasible() {
        Result r = engine.place(new Demand(5, 500, null, false), List.of(rg(1, "X", 0, 50_000), rg(2, "Y", 10, 100)));
        assertThat(r.feasibility()).isEqualTo(Feasibility.INFEASIBLE);
        assertThat(r.allocated()).isZero();
        assertThat(r.allocations()).isEmpty();
    }

    @Test
    void emptyInventoryIsInfeasible() {
        assertThat(engine.place(new Demand(5, 500, null, false), List.of()).feasibility()).isEqualTo(Feasibility.INFEASIBLE);
    }

    @Test
    void negativeFreeCapacityIsTreatedAsZero() {
        Result r = engine.place(new Demand(5, 500, null, false), List.of(rg(1, "X", -3, -1000)));
        assertThat(r.allocated()).isZero();
    }

    @Test
    void invalidDemandIsRejected() {
        assertThatThrownBy(() -> engine.place(new Demand(0, 500, null, false), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.place(new Demand(1, 0, null, false), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultDoesNotDependOnInputOrder() {
        List<RackGroupState> groups = new ArrayList<>(List.of(rg(1, "X", 12, 100_000), rg(2, "Y", 12, 100_000), rg(3, "Z", 30, 100_000)));
        Result first = engine.place(new Demand(40, 500, null, false), groups);
        for (int i = 0; i < 20; i++) {
            Collections.shuffle(groups, new Random(i));
            assertThat(engine.place(new Demand(40, 500, null, false), groups)).isEqualTo(first);
        }
    }

    @Test
    void neverExceedsSlotsPowerOrRequestedCount() {
        Random rnd = new Random(42);
        for (int run = 0; run < 500; run++) {
            int n = 1 + rnd.nextInt(6);
            List<RackGroupState> groups = new ArrayList<>();
            for (int i = 1; i <= n; i++) {
                groups.add(rg(i, "G" + i, rnd.nextInt(60), rnd.nextInt(40_000)));
            }
            int power = 100 + rnd.nextInt(1500);
            int want = 1 + rnd.nextInt(150);
            UUID pref = rnd.nextBoolean() ? id(1 + rnd.nextInt(n)) : null;
            Result r = engine.place(new Demand(want, power, pref, rnd.nextBoolean()), groups);

            assertThat(r.allocated()).isBetween(0, want);
            assertThat(r.allocations().stream().mapToInt(PlacementEngine.Allocation::servers).sum()).isEqualTo(r.allocated());
            for (PlacementEngine.Allocation a : r.allocations()) {
                RackGroupState g = groups.stream().filter(x -> x.id().equals(a.rackGroupId())).findFirst().orElseThrow();
                assertThat(a.servers()).isPositive().isLessThanOrEqualTo(g.freeSlots());
                assertThat((long) a.servers() * power).isLessThanOrEqualTo(g.freeWatts());
            }
            assertThat(r.feasibility()).isEqualTo(r.allocated() == want ? Feasibility.FEASIBLE : r.allocated() == 0 ? Feasibility.INFEASIBLE : Feasibility.PARTIAL);
        }
    }
}
