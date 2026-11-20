package com.capacitydesk.planning.persistence;

import com.capacitydesk.planning.domain.PlacementPlan;
import com.capacitydesk.planning.domain.PlanStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementPlanRepository extends JpaRepository<PlacementPlan, UUID> {

    @EntityGraph(attributePaths = "allocations")
    Optional<PlacementPlan> findWithAllocationsById(UUID id);

    @EntityGraph(attributePaths = "allocations")
    List<PlacementPlan> findByRequestIdOrderByPlanNumberDesc(UUID requestId);

    @Query("select coalesce(max(p.planNumber), 0) from PlacementPlan p where p.requestId = :requestId")
    int maxPlanNumber(@Param("requestId") UUID requestId);

    List<PlacementPlan> findByRequestIdAndStatusIn(UUID requestId, Collection<PlanStatus> statuses);
}
