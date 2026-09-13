package com.coop.quota.repo;

import com.coop.quota.domain.Voyage;
import com.coop.quota.domain.VoyageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoyageRepository extends JpaRepository<Voyage, Long> {
    Optional<Voyage> findByVoyageNo(String voyageNo);
    List<Voyage> findByStatus(VoyageStatus status);
    List<Voyage> findAllByOrderByCreatedAtDesc();
}
