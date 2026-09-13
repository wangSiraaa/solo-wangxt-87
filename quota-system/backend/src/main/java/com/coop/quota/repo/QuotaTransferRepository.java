package com.coop.quota.repo;

import com.coop.quota.domain.QuotaTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotaTransferRepository extends JpaRepository<QuotaTransfer, Long> {
}
