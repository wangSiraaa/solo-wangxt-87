package com.coop.quota.repo;

import com.coop.quota.domain.ClassificationRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassificationRevisionRepository extends JpaRepository<ClassificationRevision, Long> {
    List<ClassificationRevision> findByLandingIdOrderByIdAsc(Long landingId);
}
