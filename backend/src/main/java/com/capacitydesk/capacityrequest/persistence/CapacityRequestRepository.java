package com.capacitydesk.capacityrequest.persistence;

import com.capacitydesk.capacityrequest.domain.CapacityRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CapacityRequestRepository extends JpaRepository<CapacityRequest, UUID>, JpaSpecificationExecutor<CapacityRequest> {

    Optional<CapacityRequest> findByRequesterIdAndIdempotencyKey(UUID requesterId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CapacityRequest r where r.id = :id")
    Optional<CapacityRequest> findByIdForUpdate(@Param("id") UUID id);
}
