package com.coop.quota.service;

import com.coop.quota.domain.*;
import com.coop.quota.dto.BalanceView;
import com.coop.quota.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 样例验证场景：预计大于实捕、同船多港卸货、两航次争用额度、
 * 同一卸货凭证重复回传、删除草稿不丢捕捞事实、跨维度调拨拒绝、同维度调拨过账。
 * 每次运行使用独立的航次号/凭证号后缀，可重复执行。
 */
@Service
public class DemoService {

    private final VoyageService voyageService;
    private final QuotaService quotaService;
    private final QuotaAccountRepository accountRepo;
    private final SpeciesRepository speciesRepo;
    private final SeaAreaRepository areaRepo;
    private final SeasonRepository seasonRepo;
    private final VesselRepository vesselRepo;

    public DemoService(VoyageService voyageService, QuotaService quotaService,
                       QuotaAccountRepository accountRepo, SpeciesRepository speciesRepo,
                       SeaAreaRepository areaRepo, SeasonRepository seasonRepo,
                       VesselRepository vesselRepo) {
        this.voyageService = voyageService;
        this.quotaService = quotaService;
        this.accountRepo = accountRepo;
        this.speciesRepo = speciesRepo;
        this.areaRepo = areaRepo;
        this.seasonRepo = seasonRepo;
        this.vesselRepo = vesselRepo;
    }

    @Transactional
    public List<String> runScenarios() {
        List<String> log = new ArrayList<>();
        String run = "D" + (System.currentTimeMillis() % 1_000_000);

        Species bream = speciesRepo.findByCode("CLOUD_BREAM").orElseThrow();
        Species eel = speciesRepo.findByCode("STAR_EEL").orElseThrow();
        SeaArea north = areaRepo.findByCode("NORTH_ISLE").orElseThrow();
        Season autumn = seasonRepo.findByCode("2026_AUTUMN").orElseThrow();
        Vessel v3 = vesselRepo.findByCode("COOP-003").orElseThrow();
        Vessel v4 = vesselRepo.findByCode("COOP-004").orElseThrow();

        QuotaAccount acc3 = getOrCreate(bream, north, autumn, v3, "3000.000");
        QuotaAccount acc4 = getOrCreate(bream, north, autumn, v4, "1000.000");
        log.add("准备：渔协003 蓝鳍云鲷/北屿/秋汛 账户#" + acc3.getId()
                + " 可用 " + quotaService.available(acc3.getId()) + " kg");

        // 场景 1：预计大于实捕 → 差额释放
        Voyage voy1 = voyageService.createDraft(run + "-A", "COOP-003", "CLOUD_BREAM",
                "NORTH_ISLE", "2026_AUTUMN", new BigDecimal("1000.000"));
        voyageService.declare(voy1.getId());
        Landing l1 = voyageService.addLanding(voy1.getId(), "北屿港", run + "-R1", new BigDecimal("1000.000"));
        voyageService.verifyLanding(l1.getId(), new BigDecimal("700.000"));
        voyageService.close(voy1.getId());
        log.add("场景1 预计>实捕：申报 1000 kg，核实 700 kg，差额 300 kg 已释放；账户可用 "
                + quotaService.available(acc3.getId()) + " kg");

        // 场景 2：同船多港卸货
        Voyage voy2 = voyageService.createDraft(run + "-B", "COOP-003", "CLOUD_BREAM",
                "NORTH_ISLE", "2026_AUTUMN", new BigDecimal("2000.000"));
        voyageService.declare(voy2.getId());
        Landing l2a = voyageService.addLanding(voy2.getId(), "北屿港", run + "-R2A", new BigDecimal("800.000"));
        Landing l2b = voyageService.addLanding(voy2.getId(), "东礁港", run + "-R2B", new BigDecimal("1200.000"));
        voyageService.verifyLanding(l2a.getId(), new BigDecimal("750.000"));
        voyageService.verifyLanding(l2b.getId(), new BigDecimal("1100.000"));
        voyageService.close(voy2.getId());
        log.add("场景2 多港卸货：北屿港 750 kg + 东礁港 1100 kg，结案释放剩余 150 kg；账户可用 "
                + quotaService.available(acc3.getId()) + " kg");

        // 场景 3：两个航次争用额度
        BigDecimal before = quotaService.available(acc3.getId());
        Voyage voy3 = voyageService.createDraft(run + "-C", "COOP-003", "CLOUD_BREAM",
                "NORTH_ISLE", "2026_AUTUMN", before.subtract(new BigDecimal("100")));
        voyageService.declare(voy3.getId());
        Voyage voy4 = voyageService.createDraft(run + "-D", "COOP-003", "CLOUD_BREAM",
                "NORTH_ISLE", "2026_AUTUMN", new BigDecimal("500.000"));
        try {
            voyageService.declare(voy4.getId());
            log.add("场景3 争用：异常——第二个航次不应申报成功");
        } catch (BusinessException e) {
            log.add("场景3 争用：航次 " + voy4.getVoyageNo() + " 申报被拒（" + e.getMessage() + "）");
        }
        voyageService.cancel(voy3.getId());

        // 场景 4：同一卸货凭证重复回传不重复扣减
        Voyage voy5 = voyageService.createDraft(run + "-E", "COOP-003", "CLOUD_BREAM",
                "NORTH_ISLE", "2026_AUTUMN", new BigDecimal("500.000"));
        voyageService.declare(voy5.getId());
        Landing l5 = voyageService.addLanding(voy5.getId(), "北屿港", run + "-R5", new BigDecimal("500.000"));
        voyageService.verifyLanding(l5.getId(), new BigDecimal("400.000"));
        BigDecimal actualAfterFirst = quotaService.balanceOf(acc3.getId()).actual();
        Landing dup = voyageService.addLanding(voy5.getId(), "北屿港", run + "-R5", new BigDecimal("500.000"));
        voyageService.verifyLanding(dup.getId(), new BigDecimal("400.000"));
        BigDecimal actualAfterDup = quotaService.balanceOf(acc3.getId()).actual();
        log.add("场景4 幂等：重复回传凭证 " + run + "-R5 得到同一卸货单#" + dup.getId()
                + "，实捕保持 " + actualAfterDup + " kg（重复前后差 "
                + actualAfterDup.subtract(actualAfterFirst) + " kg）");
        voyageService.close(voy5.getId());

        // 场景 5：删除草稿不丢失实际捕捞事实
        Voyage draft = voyageService.createDraft(run + "-F", "COOP-003", "CLOUD_BREAM",
                "NORTH_ISLE", "2026_AUTUMN", new BigDecimal("100.000"));
        voyageService.deleteDraft(draft.getId());
        BigDecimal actualAfterDelete = quotaService.balanceOf(acc3.getId()).actual();
        log.add("场景5 删除草稿：草稿 " + run + "-F 已删除，账户实捕仍为 " + actualAfterDelete
                + " kg（已核实捕捞事实不受影响）");

        // 场景 6：物种/海区/季节不匹配的额度不得互相顶替
        QuotaAccount eelAcc = getOrCreate(eel, north, autumn, v4, "500.000");
        try {
            quotaService.transfer(eelAcc.getId(), acc3.getId(), new BigDecimal("100.000"),
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), "跨物种调拨测试");
            log.add("场景6 跨维度调拨：异常——不应过账");
        } catch (BusinessException e) {
            log.add("场景6 跨维度调拨被拒：" + e.getMessage());
        }

        // 场景 7：同维度调拨，保存来源与生效期间
        QuotaTransfer t = quotaService.transfer(acc4.getId(), acc3.getId(), new BigDecimal("200.000"),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), "秋汛互助调拨");
        log.add("场景7 调拨：调拨#" + t.getId() + " 渔协004 → 渔协003 200 kg，生效期间 "
                + t.getEffectiveFrom() + " ~ " + t.getEffectiveTo()
                + "，渔协003 可用 " + quotaService.available(acc3.getId()) + " kg");

        BalanceView finalBalance = quotaService.balanceOf(acc3.getId());
        log.add("最终：账户#" + acc3.getId() + " 配额 " + finalBalance.quota()
                + " / 占用 " + finalBalance.reserved()
                + " / 实捕 " + finalBalance.actual()
                + " / 可用 " + finalBalance.available() + " kg");
        return log;
    }

    private QuotaAccount getOrCreate(Species s, SeaArea a, Season season, Vessel v, String initialKg) {
        return accountRepo.findBySpeciesIdAndSeaAreaIdAndSeasonIdAndVesselId(
                s.getId(), a.getId(), season.getId(), v.getId())
                .orElseGet(() -> {
                    QuotaAccount acc = accountRepo.save(new QuotaAccount(s, a, season, v));
                    quotaService.allocate(acc, new BigDecimal(initialKg), 9000L, "演示场景核拨");
                    return acc;
                });
    }
}
