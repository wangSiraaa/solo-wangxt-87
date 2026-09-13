package com.coop.quota.repo;

import com.coop.quota.domain.QuotaAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuotaAccountRepository extends JpaRepository<QuotaAccount, Long> {
    Optional<QuotaAccount> findBySpeciesIdAndSeaAreaIdAndSeasonIdAndVesselId(
            Long speciesId, Long seaAreaId, Long seasonId, Long vesselId);
}
