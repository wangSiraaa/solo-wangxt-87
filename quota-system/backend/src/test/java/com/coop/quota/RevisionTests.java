package com.coop.quota;

import com.coop.quota.domain.*;
import com.coop.quota.dto.BalanceView;
import com.coop.quota.dto.ComponentInput;
import com.coop.quota.dto.ReplayView;
import com.coop.quota.repo.*;
import com.coop.quota.service.BusinessException;
import com.coop.quota.service.QuotaService;
import com.coop.quota.service.RevisionService;
import com.coop.quota.service.VoyageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分类修订验收：两种变三种、复核失败拒绝、修订推翻、转出后不足生成待处理缺口、
 * 跨季部分结转（记录上限、不清零）、任一季节账按当时分类回放。
 */
@SpringBootTest
@Transactional
class RevisionTests {

    @Autowired VoyageService voyageService;
    @Autowired QuotaService quotaService;
    @Autowired RevisionService revisionService;
    @Autowired QuotaAccountRepository accountRepo;
    @Autowired ShortfallRepository shortfallRepo;
    @Autowired CarryoverRepository carryoverRepo;
    @Autowired SpeciesRepository speciesRepo;
    @Autowired SeaAreaRepository areaRepo;
    @Autowired SeasonRepository seasonRepo;
    @Autowired VesselRepository vesselRepo;

    private Long accountId(String species, String area, String season, String vessel) {
        return accountRepo.findBySpeciesIdAndSeaAreaIdAndSeasonIdAndVesselId(
                speciesRepo.findByCode(species).orElseThrow().getId(),
                areaRepo.findByCode(area).orElseThrow().getId(),
                seasonRepo.findByCode(season).orElseThrow().getId(),
                vesselRepo.findByCode(vessel).orElseThrow().getId())
                .orElseThrow().getId();
    }

    private QuotaAccount ensureAccount(String species, String area, String season,
                                       String vessel, String initialKg) {
        Species s = speciesRepo.findByCode(species).orElseThrow();
        return accountRepo.findBySpeciesIdAndSeaAreaIdAndSeasonIdAndVesselId(
                s.getId(),
                areaRepo.findByCode(area).orElseThrow().getId(),
                seasonRepo.findByCode(season).orElseThrow().getId(),
                vesselRepo.findByCode(vessel).orElseThrow().getId())
                .orElseGet(() -> {
                    QuotaAccount acc = accountRepo.save(new QuotaAccount(s,
                            areaRepo.findByCode(area).orElseThrow(),
                            seasonRepo.findByCode(season).orElseThrow(),
                            vesselRepo.findByCode(vessel).orElseThrow()));
                    quotaService.allocate(acc, new BigDecimal(initialKg), 7000L, "测试核拨");
                    return acc;
                });
    }

    private Landing verifiedLanding(String voyageNo, String receipt, String vessel,
                                    String season, String estimated, String verified,
                                    List<ComponentInput> components) {
        Voyage v = voyageService.createDraft(voyageNo, vessel, "CLOUD_BREAM",
                "NORTH_ISLE", season, new BigDecimal(estimated));
        voyageService.declare(v.getId());
        Landing l = voyageService.addLanding(v.getId(), "北屿港", receipt, new BigDecimal(estimated));
        voyageService.verifyLanding(l.getId(), new BigDecimal(verified), components);
        return l;
    }

    private static List<ComponentInput> comp(Object... kv) {
        var list = new java.util.ArrayList<ComponentInput>();
        for (int i = 0; i < kv.length; i += 2) {
            list.add(new ComponentInput((String) kv[i], new BigDecimal((String) kv[i + 1])));
        }
        return list;
    }

    @Test
    void revisionFromTwoToThreeSpeciesConservesTotal() {
        Long bream = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        Long eel = accountId("STAR_EEL", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        ensureAccount("ROSY_SHRIMP", "NORTH_ISLE", "2026_AUTUMN", "COOP-001", "100.000");
        Long rosy = accountId("ROSY_SHRIMP", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");

        Landing l = verifiedLanding("R1-A", "R1-R1", "COOP-001", "2026_AUTUMN", "900.000", "900.000",
                comp("CLOUD_BREAM", "600.000", "STAR_EEL", "300.000"));
        assertEquals(0, new BigDecimal("600.000").compareTo(quotaService.balanceOf(bream).actual()));
        assertEquals(0, new BigDecimal("300.000").compareTo(quotaService.balanceOf(eel).actual()));

        // 两种 → 三种：合计恒等于 900 kg
        revisionService.applyRevision(l.getId(),
                comp("CLOUD_BREAM", "400.000", "STAR_EEL", "300.000", "ROSY_SHRIMP", "200.000"),
                "实验室检测改判");

        BigDecimal totalActual = quotaService.balanceOf(bream).actual()
                .add(quotaService.balanceOf(eel).actual())
                .add(quotaService.balanceOf(rosy).actual());
        assertEquals(0, new BigDecimal("900.000").compareTo(totalActual));
        assertEquals(0, new BigDecimal("400.000").compareTo(quotaService.balanceOf(bream).actual()));
        assertEquals(0, new BigDecimal("200.000").compareTo(quotaService.balanceOf(rosy).actual()));
        // 霞虾账户配额 100 kg、实扣 200 kg → 缺口 100 kg 待处理
        assertEquals(0, new BigDecimal("-100.000").compareTo(quotaService.available(rosy)));
        assertTrue(shortfallRepo.findFirstByAccountIdAndStatus(rosy, ShortfallStatus.PENDING).isPresent());
    }

    @Test
    void revisionRejectedWhenWeightTotalMismatch() {
        Long bream = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        Landing l = verifiedLanding("R2-A", "R2-R1", "COOP-001", "2026_AUTUMN", "500.000", "500.000",
                comp("CLOUD_BREAM", "500.000"));
        BigDecimal actualBefore = quotaService.balanceOf(bream).actual();

        // 部分卸货复核失败：分类合计 499 ≠ 批次 500，修订整体拒绝
        BusinessException ex = assertThrows(BusinessException.class, () ->
                revisionService.applyRevision(l.getId(),
                        comp("CLOUD_BREAM", "300.000", "STAR_EEL", "199.000"), "合计不符"));
        assertTrue(ex.getMessage().contains("不能凭修订增加可捕总量")
                || ex.getMessage().contains("必须等于批次核实重量"));
        assertEquals(0, actualBefore.compareTo(quotaService.balanceOf(bream).actual()));
    }

    @Test
    void overturnRestoresPreviousCompositionAndKeepsTrail() {
        Long bream = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        Long eel = accountId("STAR_EEL", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        Landing l = verifiedLanding("R3-A", "R3-R1", "COOP-001", "2026_AUTUMN", "800.000", "800.000",
                comp("CLOUD_BREAM", "500.000", "STAR_EEL", "300.000"));

        ClassificationRevision rev = revisionService.applyRevision(l.getId(),
                comp("CLOUD_BREAM", "200.000", "STAR_EEL", "600.000"), "检测改判");
        assertEquals(0, new BigDecimal("200.000").compareTo(quotaService.balanceOf(bream).actual()));

        revisionService.overturn(rev.getId());
        assertEquals(RevisionStatus.OVERTURNED, rev.getStatus());
        // 分类回退到修订前
        assertEquals(0, new BigDecimal("500.000").compareTo(quotaService.balanceOf(bream).actual()));
        assertEquals(0, new BigDecimal("300.000").compareTo(quotaService.balanceOf(eel).actual()));
        // 当前余额能追到每次调整：修订与冲回条目都在账本上
        long adjustments = quotaService.ledgerOf(bream).stream()
                .filter(e -> e.type().equals("CATCH_ADJUSTMENT")).count();
        assertEquals(2, adjustments);
        // 已推翻的修订不能再次推翻
        assertThrows(BusinessException.class, () -> revisionService.overturn(rev.getId()));
    }

    @Test
    void shortfallGeneratedWhenQuotaTransferredAwayWithoutRevokingOthers() {
        ensureAccount("STAR_EEL", "NORTH_ISLE", "2026_AUTUMN", "COOP-002", "1000.000");
        Long bream2 = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-002");
        Long bream1 = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");

        // 他人（COOP-001）的合法航次
        Voyage other = voyageService.createDraft("R4-X", "COOP-001", "CLOUD_BREAM",
                "NORTH_ISLE", "2026_AUTUMN", new BigDecimal("100.000"));
        voyageService.declare(other.getId());

        Landing l = verifiedLanding("R4-A", "R4-R1", "COOP-002", "2026_AUTUMN", "200.000", "200.000",
                comp("CLOUD_BREAM", "20.000", "STAR_EEL", "180.000"));
        // 把云鲷额度几乎全部转出，仅留 10 kg
        quotaService.transfer(bream2, bream1, new BigDecimal("4970.000"),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), "额度归集");
        assertEquals(0, new BigDecimal("10.000").compareTo(quotaService.available(bream2)));

        // 修订改判回云鲷：补扣 70 kg → 账户 -60 kg
        revisionService.applyRevision(l.getId(),
                comp("CLOUD_BREAM", "90.000", "STAR_EEL", "110.000"), "检测改判");
        assertEquals(0, new BigDecimal("-60.000").compareTo(quotaService.available(bream2)));

        // 生成待处理缺口，而不是撤销他人航次
        Shortfall s = shortfallRepo.findFirstByAccountIdAndStatus(bream2, ShortfallStatus.PENDING)
                .orElseThrow();
        assertEquals(0, new BigDecimal("60.000").compareTo(s.getAmount()));
        assertEquals(VoyageStatus.DECLARED, other.getStatus());
    }

    @Test
    void carryoverPartialWithCapAndDeficitNotZeroed() {
        // 春汛已结束（2026-06-30）：COOP-001 春汛云鲷 6000 kg
        Long springBream = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_SPRING", "COOP-001");
        Long autumnBream = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        ensureAccount("ROSY_SHRIMP", "NORTH_ISLE", "2026_SPRING", "COOP-001", "500.000");
        ensureAccount("CLOUD_BREAM", "NORTH_ISLE", "2026_SPRING", "COOP-002", "100.000");
        Long springBream2 = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_SPRING", "COOP-002");

        Landing l = verifiedLanding("R5-A", "R5-R1", "COOP-001", "2026_SPRING", "100.000", "100.000",
                comp("CLOUD_BREAM", "10.000", "ROSY_SHRIMP", "90.000"));
        quotaService.transfer(springBream, springBream2, new BigDecimal("5985.000"),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), "春汛归集");
        revisionService.applyRevision(l.getId(),
                comp("CLOUD_BREAM", "80.000", "ROSY_SHRIMP", "20.000"), "春汛批次改判");
        // 可用 = 6000 - 10 - 5985 - 70 = -65
        assertEquals(0, new BigDecimal("-65.000").compareTo(quotaService.available(springBream)));

        // 超出上限被拒
        assertThrows(BusinessException.class, () -> revisionService.carryover(
                springBream, autumnBream, new BigDecimal("50.000"), new BigDecimal("40.000")));
        // 部分结转 40（上限 40）：旧季留 25 kg 欠额，不清零
        Carryover co = revisionService.carryover(springBream, autumnBream,
                new BigDecimal("40.000"), new BigDecimal("40.000"));
        assertEquals(0, new BigDecimal("40.000").compareTo(co.getCapAmount()));
        assertEquals(0, new BigDecimal("-25.000").compareTo(quotaService.available(springBream)));
        assertEquals(0, new BigDecimal("40.000").compareTo(
                quotaService.balanceOf(springBream).carryoverNet()));
        assertEquals(0, new BigDecimal("-40.000").compareTo(
                quotaService.balanceOf(autumnBream).carryoverNet()));
        // 缺口随结转刷新为 25 kg，仍待处理
        Shortfall s = shortfallRepo.findFirstByAccountIdAndStatus(springBream, ShortfallStatus.PENDING)
                .orElseThrow();
        assertEquals(0, new BigDecimal("25.000").compareTo(s.getAmount()));
        // 结转金额不得超过欠额（不得超额清零）
        assertThrows(BusinessException.class, () -> revisionService.carryover(
                springBream, autumnBream, new BigDecimal("30.000"), new BigDecimal("40.000")));
        // 秋汛（未结束）不能作为旧季转出
        assertThrows(BusinessException.class, () -> revisionService.carryover(
                autumnBream, springBream, new BigDecimal("1.000"), new BigDecimal("40.000")));
    }

    @Test
    void replayShowsBalanceAsOfEarlierClassification() {
        Long bream = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        Landing l = verifiedLanding("R6-A", "R6-R1", "COOP-001", "2026_AUTUMN", "400.000", "400.000",
                comp("CLOUD_BREAM", "400.000"));
        BalanceView before = quotaService.balanceOf(bream);
        // 修订前的最后一条账本条目
        Long lastEntryId = quotaService.ledgerOf(bream).stream()
                .mapToLong(e -> e.id()).max().orElseThrow();

        revisionService.applyRevision(l.getId(),
                comp("CLOUD_BREAM", "150.000", "STAR_EEL", "250.000"), "检测改判");
        assertEquals(0, new BigDecimal("150.000").compareTo(quotaService.balanceOf(bream).actual()));

        // 按当时分类回放：修订前的账面原样复现
        ReplayView replayed = quotaService.replay(bream, null, lastEntryId);
        assertEquals(0, before.actual().compareTo(replayed.balance().actual()));
        assertEquals(0, before.available().compareTo(replayed.balance().available()));
        assertTrue(replayed.entries().stream().noneMatch(e -> e.type().equals("CATCH_ADJUSTMENT")));
        // 当前账面则包含修订调整，逐条可追
        assertTrue(quotaService.replay(bream, null, null).entries().stream()
                .anyMatch(e -> e.type().equals("CATCH_ADJUSTMENT") && e.refType().equals("REVISION")));
    }
}
