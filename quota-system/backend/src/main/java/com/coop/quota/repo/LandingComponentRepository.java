package com.coop.quota.repo;

import com.coop.quota.domain.LandingComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LandingComponentRepository extends JpaRepository<LandingComponent, Long> {
    /** 靠港时的初始分类行 */
    List<LandingComponent> findByLandingIdAndRevisionIsNull(Long landingId);
    /** 某次修订的分类行 */
    List<LandingComponent> findByRevisionId(Long revisionId);
}
