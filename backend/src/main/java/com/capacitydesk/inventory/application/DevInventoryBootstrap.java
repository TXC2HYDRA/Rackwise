package com.capacitydesk.inventory.application;

import com.capacitydesk.inventory.domain.DataCenter;
import com.capacitydesk.inventory.domain.RackGroup;
import com.capacitydesk.inventory.domain.Server;
import com.capacitydesk.inventory.domain.ServerStatus;
import com.capacitydesk.inventory.persistence.DataCenterRepository;
import com.capacitydesk.inventory.persistence.RackGroupRepository;
import com.capacitydesk.inventory.persistence.ServerRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Synthetic demo inventory, dev profile only, only into an empty database. */
@Component
@Profile("dev")
class DevInventoryBootstrap implements ApplicationRunner {

    private static final int POWER = 500;

    private final DataCenterRepository dataCenters;
    private final RackGroupRepository rackGroups;
    private final ServerRepository servers;

    DevInventoryBootstrap(DataCenterRepository dataCenters, RackGroupRepository rackGroups, ServerRepository servers) {
        this.dataCenters = dataCenters;
        this.rackGroups = rackGroups;
        this.servers = servers;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (dataCenters.count() > 0) {
            return;
        }
        DataCenter a = dataCenters.save(new DataCenter("DC-A", "Data Center A"));
        DataCenter b = dataCenters.save(new DataCenter("DC-B", "Data Center B"));
        // X: 100 slots / 50 kW, 45 used  -> room for 55 by slots, 55 by power (27.5 kW free)
        seed(a, "X", "Rack Group X", 100, 50_000, 45);
        // Y: 30 slots / 15 kW, 20 used   -> room for 10
        seed(a, "Y", "Rack Group Y", 30, 15_000, 20);
        // Z: 60 slots / 12 kW, 10 used   -> power-bound: 14 more servers at 500 W
        seed(a, "Z", "Rack Group Z (power constrained)", 60, 12_000, 10);
        seed(b, "X", "Rack Group X", 80, 40_000, 30);
    }

    private void seed(DataCenter dc, String code, String name, int slots, long watts, int existing) {
        RackGroup rg = rackGroups.save(new RackGroup(dc, code, name, slots, watts));
        for (int i = 1; i <= existing; i++) {
            String host = "%s-%s-%04d".formatted(dc.getCode().toLowerCase(), code.toLowerCase(), i);
            servers.save(new Server(host, rg, 32, 256, 8, POWER, ServerStatus.ACTIVE));
        }
    }
}
