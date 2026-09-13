package com.coop.quota.repo;

import com.coop.quota.domain.Landing;
import com.coop.quota.domain.LandingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LandingRepository extends JpaRepository<Landing, Long> {
    Optional<Landing> findByReceiptNo(String receiptNo);
    List<Landing> findByVoyageIdOrderByIdAsc(Long voyageId);
    long countByVoyageIdAndStatus(Long voyageId, LandingStatus status);
}
