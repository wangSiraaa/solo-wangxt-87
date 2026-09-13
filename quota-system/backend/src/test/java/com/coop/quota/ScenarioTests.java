package com.coop.quota;

import com.coop.quota.domain.*;
import com.coop.quota.dto.BalanceView;
import com.coop.quota.repo.*;
import com.coop.quota.service.BusinessException;
import com.coop.quota.service.QuotaService;
import com.coop.quota.service.VoyageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 样例验证：预计大于实捕、同船多港卸货、两航次争用额度、凭证幂等、
 * 删除草稿不丢事实、维度不匹配不得顶替、待确认称重不计入实捕、调拨留痕。
 */
@SpringBootTest
@Transactional
class ScenarioTests {

    @Autowired VoyageService voyageService;
    @Autowired QuotaService quotaService;
    @Autowired QuotaAccountRepository accountRepo;
    @Autowired QuotaTransferRepository transferRepo;
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

    private Voyage declared(String no, String vessel, String species, String area,
                            String season, String estimatedKg) {
        Voyage v = voyageService.createDraft(no, vessel, species, area, season,
                new BigDecimal(estimatedKg));
        return voyageService.declare(v.getId());
    }

    @Test
    void estimatedGreaterThanActual_releasesDifference() {
        Long acc = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        Voyage v = declared("T1-A", "COOP-001", "CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "1000.000");
        Landing l = voyageService.addLanding(v.getId(), "北屿港", "T1-R1", new BigDecimal("1000.000"));
        voyageService.verifyLanding(l.getId(), new BigDecimal("700.000"));
        voyageService.close(v.getId());

        BalanceView b = quotaService.balanceOf(acc);
        assertEquals(0, new BigDecimal("700.000").compareTo(b.actual()));
        assertEquals(0, BigDecimal.ZERO.compareTo(b.reserved()));
        assertEquals(0, new BigDecimal("7300.000").compareTo(b.available()));
    }

    @Test
    void sameVesselMultiPortLandings() {
        Long acc = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        Voyage v = declared("T2-A", "COOP-001", "CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "2000.000");
        Landing p1 = voyageService.addLanding(v.getId(), "北屿港", "T2-R1", new BigDecimal("800.000"));
        Landing p2 = voyageService.addLanding(v.getId(), "东礁港", "T2-R2", new BigDecimal("1200.000"));
        voyageService.verifyLanding(p1.getId(), new BigDecimal("750.000"));
        voyageService.verifyLanding(p2.getId(), new BigDecimal("1100.000"));
        voyageService.close(v.getId());

        BalanceView b = quotaService.balanceOf(acc);
        assertEquals(0, new BigDecimal("1850.000").compareTo(b.actual()));
        assertEquals(0, BigDecimal.ZERO.compareTo(b.reserved()));
        assertEquals(0, new BigDecimal("6150.000").compareTo(b.available()));
    }

    @Test
    void twoVoyagesCompetingForQuota_secondRejected() {
        declared("T3-A", "COOP-002", "CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "4500.000");
        Voyage second = voyageService.createDraft("T3-B", "COOP-002", "CLOUD_BREAM",
                "NORTH_ISLE", "2026_AUTUMN", new BigDecimal("1000.000"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> voyageService.declare(second.getId()));
        assertTrue(ex.getMessage().contains("可用额度不足"));
    }

    @Test
    void duplicateReceiptNotDeductedTwice() {
        Long acc = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        Voyage v = declared("T4-A", "COOP-001", "CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "500.000");
        Landing first = voyageService.addLanding(v.getId(), "北屿港", "T4-R1", new BigDecimal("500.000"));
        voyageService.verifyLanding(first.getId(), new BigDecimal("400.000"));
        BigDecimal actualAfterFirst = quotaService.balanceOf(acc).actual();

        // 同一凭证重复回传：建单幂等 + 核实幂等
        Landing dup = voyageService.addLanding(v.getId(), "北屿港", "T4-R1", new BigDecimal("500.000"));
        assertEquals(first.getId(), dup.getId());
        voyageService.verifyLanding(dup.getId(), new BigDecimal("400.000"));

        BalanceView b = quotaService.balanceOf(acc);
        assertEquals(0, actualAfterFirst.compareTo(b.actual()));
        assertEquals(0, new BigDecimal("400.000").compareTo(b.actual()));
    }

    @Test
    void pendingWeighingNotCountedInFinalCatch() {
        Long acc = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        Voyage v = declared("T5-A", "COOP-001", "CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "600.000");
        voyageService.addLanding(v.getId(), "北屿港", "T5-R1", new BigDecimal("600.000"));

        BalanceView b = quotaService.balanceOf(acc);
        assertEquals(0, BigDecimal.ZERO.compareTo(b.actual()));       // 待确认不计入实捕
        assertEquals(0, new BigDecimal("600.000").compareTo(b.reserved())); // 占用仍在
        // 有待确认称重时不允许结案
        assertThrows(BusinessException.class, () -> voyageService.close(v.getId()));
    }

    @Test
    void deletingDraftDoesNotLoseVerifiedCatchFacts() {
        Long acc = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        Voyage v = declared("T6-A", "COOP-001", "CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "300.000");
        Landing l = voyageService.addLanding(v.getId(), "北屿港", "T6-R1", new BigDecimal("300.000"));
        voyageService.verifyLanding(l.getId(), new BigDecimal("250.000"));
        BigDecimal actualBefore = quotaService.balanceOf(acc).actual();

        Voyage draft = voyageService.createDraft("T6-B", "COOP-001", "CLOUD_BREAM",
                "NORTH_ISLE", "2026_AUTUMN", new BigDecimal("100.000"));
        voyageService.deleteDraft(draft.getId());
        assertEquals(0, actualBefore.compareTo(quotaService.balanceOf(acc).actual()));

        // 已申报航次（含已核实捕捞事实）不允许删除
        assertThrows(BusinessException.class, () -> voyageService.deleteDraft(v.getId()));
    }

    @Test
    void mismatchedDimensionsCannotSubstitute() {
        // COOP-001 蓝鳍云鲷额度充足，但银鳞星鳗账户只有 2000 kg：申报 3000 kg 必须失败
        Voyage v = voyageService.createDraft("T7-A", "COOP-001", "STAR_EEL",
                "NORTH_ISLE", "2026_AUTUMN", new BigDecimal("3000.000"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> voyageService.declare(v.getId()));
        assertTrue(ex.getMessage().contains("可用额度不足"));

        // 跨物种调拨同样被拒绝
        Long breamAcc = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        Long eelAcc = accountId("STAR_EEL", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        assertThrows(BusinessException.class, () -> quotaService.transfer(
                breamAcc, eelAcc, new BigDecimal("100.000"),
                LocalDate.now(), LocalDate.now().plusDays(1), "跨物种"));
    }

    @Test
    void transferKeepsSourceAndEffectivePeriod() {
        Long from = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-001");
        Long to = accountId("CLOUD_BREAM", "NORTH_ISLE", "2026_AUTUMN", "COOP-002");

        // 不在生效期间内不得过账
        assertThrows(BusinessException.class, () -> quotaService.transfer(
                from, to, new BigDecimal("100.000"),
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20), "未到生效期"));

        QuotaTransfer t = quotaService.transfer(from, to, new BigDecimal("500.000"),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), "秋汛互助");
        assertEquals(from, t.getFromAccount().getId());   // 来源留痕
        assertEquals(to, t.getToAccount().getId());
        assertNotNull(t.getEffectiveFrom());
        assertNotNull(t.getEffectiveTo());

        BalanceView fb = quotaService.balanceOf(from);
        BalanceView tb = quotaService.balanceOf(to);
        assertEquals(0, new BigDecimal("7500.000").compareTo(fb.available()));
        assertEquals(0, new BigDecimal("5500.000").compareTo(tb.available()));
        // 账本可追溯到调拨单据
        assertTrue(quotaService.ledgerOf(to).stream()
                .anyMatch(e -> e.refType().equals("TRANSFER") && e.refId().equals(t.getId())));
    }

    @Test
    void overCatchBeyondReservationRequiresSufficientBalance() {
        Long acc = accountId("STAR_EEL", "EAST_REEF", "2026_AUTUMN", "COOP-003");
        Voyage v = declared("T9-A", "COOP-003", "STAR_EEL", "EAST_REEF", "2026_AUTUMN", "3900.000");
        Landing l = voyageService.addLanding(v.getId(), "东礁港", "T9-R1", new BigDecimal("3900.000"));
        // 实捕 4200 kg 超出账户 4000 kg 总额度，必须拒绝
        assertThrows(BusinessException.class,
                () -> voyageService.verifyLanding(l.getId(), new BigDecimal("4200.000")));
        // 实捕 3950 kg 超出占用但余额可兜底，允许并实扣
        voyageService.verifyLanding(l.getId(), new BigDecimal("3950.000"));
        assertEquals(0, new BigDecimal("50.000").compareTo(quotaService.balanceOf(acc).available()));
    }
}
