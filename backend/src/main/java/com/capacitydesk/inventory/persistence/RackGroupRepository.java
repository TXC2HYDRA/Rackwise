package com.capacitydesk.inventory.persistence;

import com.capacitydesk.inventory.domain.RackGroup;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RackGroupRepository extends JpaRepository<RackGroup, UUID> {

    boolean existsByDataCenterIdAndCode(UUID dataCenterId, String code);

    @EntityGraph(attributePaths = "dataCenter")
    @Query("select r from RackGroup r where (:dc is null or r.dataCenter.id = :dc) order by r.dataCenter.code, r.code")
    List<RackGroup> findAllOrdered(@Param("dc") UUID dataCenterId);

    @EntityGraph(attributePaths = "dataCenter")
    Optional<RackGroup> findWithDataCenterById(UUID id);

    /** Row lock that also bumps the version: every writer of a rack group's capacity serializes here. */
    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    @Query("select r from RackGroup r where r.id = :id")
    Optional<RackGroup> findByIdForUpdate(@Param("id") UUID id);

    /** Row lock without bumping the version; callers compare the version first, then force-increment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RackGroup r where r.id = :id")
    Optional<RackGroup> findByIdForWrite(@Param("id") UUID id);

    /** Bumps the inventory version of a row the caller already holds a write lock on. */
    @Modifying
    @Query("update RackGroup r set r.version = r.version + 1, r.updatedAt = CURRENT_TIMESTAMP where r.id = :id")
    void bumpVersion(@Param("id") UUID id);
}
