package com.capacitydesk.inventory.api;

import com.capacitydesk.inventory.application.InventoryService;
import com.capacitydesk.inventory.application.InventoryViews.CapacitySummary;
import com.capacitydesk.inventory.application.InventoryViews.DataCenterView;
import com.capacitydesk.inventory.application.InventoryViews.PageView;
import com.capacitydesk.inventory.application.InventoryViews.RackGroupCapacity;
import com.capacitydesk.inventory.application.InventoryViews.ServerView;
import com.capacitydesk.inventory.domain.ServerStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Read access for any authenticated user; writes are ADMIN only. */
@RestController
@RequestMapping("/api/v1/inventory")
class InventoryController {

    private static final String CODE = "^[A-Z0-9][A-Z0-9-]{0,15}$";

    record DataCenterRequest(@NotBlank @Pattern(regexp = CODE) String code, @NotBlank @Size(max = 120) String name) {}

    record RackGroupRequest(@NotNull UUID dataCenterId, @NotBlank @Pattern(regexp = CODE) String code,
                            @NotBlank @Size(max = 120) String name,
                            @Min(1) @Max(1000) int slotCapacity, @Min(1) @Max(10_000_000) long powerCapacityWatts) {}

    record LimitsRequest(@Min(1) @Max(1000) int slotCapacity, @Min(1) @Max(10_000_000) long powerCapacityWatts) {}

    record ServerRequest(@NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9.-]{0,78}[a-z0-9]$") String hostname, @NotNull UUID rackGroupId,
                         @Min(1) @Max(1024) int cpuCores, @Min(1) @Max(16384) int ramGb,
                         @Min(1) @Max(10000) int storageTb, @Min(1) @Max(10000) int powerWatts) {}

    record StatusRequest(@NotNull ServerStatus status) {}

    private final InventoryService inventory;

    InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/datacenters")
    List<DataCenterView> dataCenters() {
        return inventory.listDataCenters();
    }

    @GetMapping("/rack-groups")
    List<RackGroupCapacity> rackGroups(@RequestParam(required = false) UUID dataCenterId) {
        return inventory.listRackGroups(dataCenterId);
    }

    @GetMapping("/rack-groups/{id}")
    RackGroupCapacity rackGroup(@PathVariable UUID id) {
        return inventory.getRackGroup(id);
    }

    @GetMapping("/capacity")
    CapacitySummary capacity(@RequestParam(required = false) UUID dataCenterId) {
        return inventory.capacity(dataCenterId);
    }

    @GetMapping("/servers")
    PageView<ServerView> servers(@RequestParam(required = false) UUID rackGroupId,
                                 @RequestParam(required = false) ServerStatus status,
                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                 @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return inventory.listServers(rackGroupId, status, PageRequest.of(page, size, Sort.by("hostname")));
    }

    @PostMapping("/datacenters")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    DataCenterView createDataCenter(@Valid @RequestBody DataCenterRequest r) {
        return inventory.createDataCenter(r.code(), r.name());
    }

    @PostMapping("/rack-groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    RackGroupCapacity createRackGroup(@Valid @RequestBody RackGroupRequest r) {
        return inventory.createRackGroup(r.dataCenterId(), r.code(), r.name(), r.slotCapacity(), r.powerCapacityWatts());
    }

    @PutMapping("/rack-groups/{id}/limits")
    @PreAuthorize("hasRole('ADMIN')")
    RackGroupCapacity updateLimits(@PathVariable UUID id, @Valid @RequestBody LimitsRequest r) {
        return inventory.updateLimits(id, r.slotCapacity(), r.powerCapacityWatts());
    }

    @PostMapping("/servers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ServerView addServer(@Valid @RequestBody ServerRequest r) {
        return inventory.addServer(new InventoryService.NewServer(r.hostname(), r.rackGroupId(), r.cpuCores(), r.ramGb(), r.storageTb(), r.powerWatts()));
    }

    @PutMapping("/servers/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    ServerView setStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest r) {
        return inventory.setServerStatus(id, r.status());
    }
}
