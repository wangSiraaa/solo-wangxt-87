package com.coop.quota.repo;

import com.coop.quota.domain.Shortfall;
import com.coop.quota.domain.ShortfallStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShortfallRepository extends JpaRepository<Shortfall, Long> {
    Optional<Shortfall> findFirstByAccountIdAndStatus(Long accountId, ShortfallStatus status);
    List<Shortfall> findAllByOrderByCreatedAtDesc();
}
