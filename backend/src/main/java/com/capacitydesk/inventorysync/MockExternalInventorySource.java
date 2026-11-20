package com.capacitydesk.inventorysync;

import com.capacitydesk.inventory.domain.ServerStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Simulated CMDB feed. Synthetic and deterministic: {@code count} servers in DC-A / rack group Y, the last one in
 * maintenance, plus one record for a rack group that does not exist (to show skipping). No network, no real data.
 */
@Component
@ConditionalOnMissingBean(value = ExternalInventorySource.class, ignored = MockExternalInventorySource.class)
class MockExternalInventorySource implements ExternalInventorySource {

    private final int count;

    MockExternalInventorySource(@Value("${capacitydesk.inventory-sync.mock.count:5}") int count) {
        this.count = count;
    }

    @Override
    public String name() {
        return "mock-cmdb";
    }

    @Override
    public List<ExternalServer> fetch() {
        List<ExternalServer> out = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            out.add(new ExternalServer("ext-dca-y-%04d".formatted(i), "DC-A", "Y", 32, 256, 8, 500,
                    i == count ? ServerStatus.MAINTENANCE : ServerStatus.ACTIVE));
        }
        out.add(new ExternalServer("ext-dca-q-0001", "DC-A", "Q", 32, 256, 8, 500, ServerStatus.ACTIVE));
        return out;
    }
}
