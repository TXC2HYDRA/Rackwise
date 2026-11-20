package com.capacitydesk.inventory.persistence;

import com.capacitydesk.inventory.domain.Server;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServerRepository extends JpaRepository<Server, UUID>, JpaSpecificationExecutor<Server> {

    String USAGE = "select new com.capacitydesk.inventory.persistence.UsageRow(s.rackGroup.id, count(s), coalesce(sum(s.powerWatts), 0L)) "
            + "from Server s where s.status <> com.capacitydesk.inventory.domain.ServerStatus.DECOMMISSIONED ";

    boolean existsByHostname(String hostname);

    @EntityGraph(attributePaths = "rackGroup")
    Optional<Server> findByHostname(String hostname);

    @Query(USAGE + "group by s.rackGroup.id")
    List<UsageRow> usageByRackGroup();

    @Query(USAGE + "and s.rackGroup.id = :id group by s.rackGroup.id")
    Optional<UsageRow> usageOf(@Param("id") UUID rackGroupId);

    @Override
    @EntityGraph(attributePaths = "rackGroup.dataCenter")
    Page<Server> findAll(Specification<Server> spec, Pageable pageable);

    @EntityGraph(attributePaths = "rackGroup.dataCenter")
    Optional<Server> findWithRackGroupById(UUID id);
}
