package com.coop.quota.service;

import com.coop.quota.domain.*;
import com.coop.quota.dto.BalanceView;
import com.coop.quota.dto.ComponentInput;
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
    private final RevisionService revisionService;

    public DemoService(VoyageService voyageService, QuotaService quotaService,
                       QuotaAccountRepository accountRepo, SpeciesRepository speciesRepo,
                       SeaAreaRepository areaRepo, SeasonRepository seasonRepo,
                       VesselRepository vesselRepo, RevisionService revisionService) {
        this.voyageService = voyageService;
        this.quotaService = quotaService;
        this.accountRepo = accountRepo;
        this.speciesRepo = speciesRepo;
        this.areaRepo = areaRepo;
        this.seasonRepo = seasonRepo;
        this.vesselRepo = vesselRepo;
        this.revisionService = revisionService;
    }

    @Transactional
    public List<String> runScenarios() {
        List<String> log = new ArrayList<>();
        String run = "D" + (System.currentTimeMillis() % 1_000_000);

        Species bream = speciesRepo.findByCode("CLOUD_BREAM").orElseThrow();
        Species eel = speciesRepo.findByCode("STAR_EEL").orElseThrow();
        Species rosy = speciesRepo.findByCode("ROSY_SHRIMP").orElseThrow();
        SeaArea north = areaRepo.findByCode("NORTH_ISLE").orElseThrow();
        Season autumn = seasonRepo.findByCode("2026_AUTUMN").orElseThrow();
        Season spring = seasonRepo.findByCode("2026_SPRING").orElseThrow();
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

        // 场景 8：混合渔获分类从两种变三种（重量守恒，新物种账户自动建立并产生待处理缺口）
        QuotaAccount eelAcc3 = getOrCreate(eel, north, autumn, v3, "1000.000");
        Voyage voyG = voyageService.createDraft(run + "-G", "COOP-003", "CLOUD_BREAM",
                "NORTH_ISLE", "2026_AUTUMN", new BigDecimal("900.000"));
        voyageService.declare(voyG.getId());
        Landing lg = voyageService.addLanding(voyG.getId(), "北屿港", run + "-RG", new BigDecimal("900.000"));
        voyageService.verifyLanding(lg.getId(), new BigDecimal("900.000"), List.of(
                new ComponentInput("CLOUD_BREAM", new BigDecimal("600.000")),
                new ComponentInput("STAR_EEL", new BigDecimal("300.000"))));
        ClassificationRevision rev8 = revisionService.applyRevision(lg.getId(), List.of(
                new ComponentInput("CLOUD_BREAM", new BigDecimal("400.000")),
                new ComponentInput("STAR_EEL", new BigDecimal("300.000")),
                new ComponentInput("ROSY_SHRIMP", new BigDecimal("200.000"))), "实验室检测改判");
        log.add("场景8 两种变三种：凭证 " + run + "-RG 由 云鲷600/星鳗300 修订为 云鲷400/星鳗300/霞虾200"
                + "（合计恒为 900 kg），霞虾账户无配额 → 待处理缺口 "
                + quotaService.available(
                        accountRepo.findBySpeciesIdAndSeaAreaIdAndSeasonIdAndVesselId(
                                rosy.getId(), north.getId(), autumn.getId(), v3.getId()).orElseThrow().getId())
                        .negate() + " kg");

        // 场景 9：部分卸货复核失败——分类合计不等于批次重量，修订被拒
        try {
            revisionService.applyRevision(lg.getId(), List.of(
                    new ComponentInput("CLOUD_BREAM", new BigDecimal("500.000")),
                    new ComponentInput("STAR_EEL", new BigDecimal("399.000"))), "合计不符测试");
            log.add("场景9 复核失败：异常——合计不符的修订不应过账");
        } catch (BusinessException e) {
            log.add("场景9 复核失败被拒：" + e.getMessage());
        }

        // 场景 10：修订被推翻——调整全额冲回，分类回退，缺口自动了结
        revisionService.overturn(rev8.getId());
        log.add("场景10 推翻：修订#" + rev8.getId() + " 已推翻，云鲷扣减恢复 600 kg，"
                + "霞虾缺口随之了结；账本保留修订与冲回全部条目");

        // 场景 11：已转出配额的船舶因修订不足 → 生成待处理缺口，不撤销任何航次
        Voyage voyH = voyageService.createDraft(run + "-H", "COOP-004", "CLOUD_BREAM",
                "NORTH_ISLE", "2026_AUTUMN", new BigDecimal("100.000"));
        voyageService.declare(voyH.getId());
        Landing lh = voyageService.addLanding(voyH.getId(), "北屿港", run + "-RH", new BigDecimal("100.000"));
        voyageService.verifyLanding(lh.getId(), new BigDecimal("100.000"), List.of(
                new ComponentInput("CLOUD_BREAM", new BigDecimal("10.000")),
                new ComponentInput("STAR_EEL", new BigDecimal("90.000"))));
        quotaService.transfer(acc4.getId(), acc3.getId(), new BigDecimal("785.000"),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), "秋汛二次互助");
        ClassificationRevision rev11 = revisionService.applyRevision(lh.getId(), List.of(
                new ComponentInput("CLOUD_BREAM", new BigDecimal("80.000")),
                new ComponentInput("STAR_EEL", new BigDecimal("20.000"))), "检测改判回云鲷");
        log.add("场景11 转出后不足：渔协004 云鲷已调出 785 kg，修订#" + rev11.getId()
                + " 补扣 70 kg 后可用 " + quotaService.available(acc4.getId())
                + " kg → 生成待处理缺口，渔协003 的航次与额度不受影响");

        // 场景 12：跨季欠额结转——部分承接、记录上限、旧季负余额不清零
        QuotaAccount springBream3 = getOrCreate(bream, north, spring, v3, "2000.000");
        QuotaAccount springRosy3 = getOrCreate(rosy, north, spring, v3, "500.000");
        QuotaAccount springBream4 = getOrCreate(bream, north, spring, v4, "100.000");
        Voyage voyI = voyageService.createDraft(run + "-I", "COOP-003", "CLOUD_BREAM",
                "NORTH_ISLE", "2026_SPRING", new BigDecimal("100.000"));
        voyageService.declare(voyI.getId());
        Landing li = voyageService.addLanding(voyI.getId(), "北屿港", run + "-RI", new BigDecimal("100.000"));
        voyageService.verifyLanding(li.getId(), new BigDecimal("100.000"), List.of(
                new ComponentInput("CLOUD_BREAM", new BigDecimal("10.000")),
                new ComponentInput("ROSY_SHRIMP", new BigDecimal("90.000"))));
        quotaService.transfer(springBream3.getId(), springBream4.getId(), new BigDecimal("1985.000"),
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), "春汛额度归集");
        revisionService.applyRevision(li.getId(), List.of(
                new ComponentInput("CLOUD_BREAM", new BigDecimal("60.000")),
                new ComponentInput("ROSY_SHRIMP", new BigDecimal("40.000"))), "春汛批次检测改判");
        BigDecimal springDeficit = quotaService.available(springBream3.getId()).negate();
        Carryover co = revisionService.carryover(springBream3.getId(), acc3.getId(),
                new BigDecimal("30.000"), new BigDecimal("30.000"));
        log.add("场景12 跨季结转：春汛欠额 " + springDeficit + " kg，结转#" + co.getId()
                + " 承接 30 kg（上限 30 kg）至秋汛账户，春汛剩余欠额 "
                + quotaService.available(springBream3.getId()).negate() + " kg 保留不清零");

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
