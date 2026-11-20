package com.capacitydesk.observability;

import com.capacitydesk.inventory.application.InventoryService;
import com.capacitydesk.inventory.application.InventoryViews.CapacitySummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Component;

/** Fleet-wide capacity gauges, read from the database at scrape time and cached briefly to bound DB load. */
@Component
class CapacityMetrics implements MeterBinder {

    private static final Duration TTL = Duration.ofSeconds(15);
    private final InventoryService inventory;
    private volatile CapacitySummary cached;
    private volatile long loadedAt;

    CapacityMetrics(InventoryService inventory) {
        this.inventory = inventory;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        gauge(registry, "rackwise.capacity.slots.limit", CapacitySummary::totalSlots);
        gauge(registry, "rackwise.capacity.slots.used", CapacitySummary::usedSlots);
        gauge(registry, "rackwise.capacity.power.limit.watts", CapacitySummary::totalPowerWatts);
        gauge(registry, "rackwise.capacity.power.used.watts", CapacitySummary::usedPowerWatts);
    }

    private void gauge(MeterRegistry r, String name, ToDoubleFunction<CapacitySummary> f) {
        Gauge.builder(name, this, m -> {
            try {
                return f.applyAsDouble(m.summary());
            } catch (RuntimeException e) {
                return Double.NaN; // DB unavailable: report no value rather than a stale one
            }
        }).register(r);
    }

    private synchronized CapacitySummary summary() {
        if (cached == null || System.nanoTime() - loadedAt > TTL.toNanos()) {
            cached = inventory.capacity(null);
            loadedAt = System.nanoTime();
        }
        return cached;
    }
}
