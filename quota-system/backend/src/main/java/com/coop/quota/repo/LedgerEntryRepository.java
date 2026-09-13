package com.coop.quota.repo;

import com.coop.quota.domain.LedgerEntry;
import com.coop.quota.domain.LedgerType;
import com.coop.quota.domain.RefType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtAscIdAsc(Long accountId);

    List<LedgerEntry> findByRefTypeAndRefId(RefType refType, Long refId);

    /** 账户全部条目带符号求和 = 可用余额 */
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.account.id = :accountId")
    BigDecimal sumByAccountId(@Param("accountId") Long accountId);

    /** 按类型分组汇总，用于余额视图（配额/占用/实捕分列） */
    @Query("select e.type, coalesce(sum(e.amount), 0) from LedgerEntry e " +
            "where e.account.id = :accountId group by e.type")
    List<Object[]> sumByAccountIdGroupByType(@Param("accountId") Long accountId);

    /** 某单据（如航次）在指定类型上的合计 */
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e " +
            "where e.refType = :refType and e.refId = :refId and e.type in :types")
    BigDecimal sumByRefAndTypes(@Param("refType") RefType refType,
                                @Param("refId") Long refId,
                                @Param("types") Collection<LedgerType> types);

    /** 某航次全部卸货单带来的占用释放合计（释放条目挂在 LANDING 单据上，需关联回航次） */
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e, Landing l " +
            "where e.refType = com.coop.quota.domain.RefType.LANDING and e.refId = l.id " +
            "and l.voyage.id = :voyageId and e.type = com.coop.quota.domain.LedgerType.RESERVATION_RELEASE")
    BigDecimal sumLandingReleasesByVoyageId(@Param("voyageId") Long voyageId);
}
