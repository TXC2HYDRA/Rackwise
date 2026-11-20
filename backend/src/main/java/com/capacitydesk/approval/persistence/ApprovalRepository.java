package com.capacitydesk.approval.persistence;

import com.capacitydesk.approval.domain.Approval;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, UUID> {
    Optional<Approval> findByPlanId(UUID planId);
}
