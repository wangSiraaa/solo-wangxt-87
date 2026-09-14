package com.coop.quota.service;

import com.coop.quota.domain.*;
import com.coop.quota.dto.ComponentInput;
import com.coop.quota.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 分类修订、待处理缺口与跨季欠额结转。
 * 修订只重新分配各物种扣减量，重量合计守恒；账户为负时生成待处理缺口，
 * 不自动撤销任何合法航次；欠额可部分结转到下一季并记录承接关系与上限。
 */
@Service
public class RevisionService {

    private final LandingRepository landingRepo;
    private final LandingComponentRepository componentRepo;
    private final ClassificationRevisionRepository revisionRepo;
    private final ShortfallRepository shortfallRepo;
    private final CarryoverRepository carryoverRepo;
    private final QuotaAccountRepository accountRepo;
    private final LedgerEntryRepository entryRepo;
    private final SpeciesRepository speciesRepo;

    public RevisionService(LandingRepository landingRepo, LandingComponentRepository componentRepo,
                           ClassificationRevisionRepository revisionRepo,
                           ShortfallRepository shortfallRepo, CarryoverRepository carryoverRepo,
                           QuotaAccountRepository accountRepo, LedgerEntryRepository entryRepo,
                           SpeciesRepository speciesRepo) {
        this.landingRepo = landingRepo;
        this.componentRepo = componentRepo;
        this.revisionRepo = revisionRepo;
        this.shortfallRepo = shortfallRepo;
        this.carryoverRepo = carryoverRepo;
        this.accountRepo = accountRepo;
        this.entryRepo = entryRepo;
        this.speciesRepo = speciesRepo;
    }

    /**
     * 应用分类修订：新分类合计必须等于批次核实重量（不能凭修订增加可捕总量）。
     * 差额以 CATCH_ADJUSTMENT 条目记在原 (物种,海区,季节,船舶) 账户上——
     * 即使原季节已结束；账户因此为负时生成待处理缺口。
     */
    @Transactional
    public ClassificationRevision applyRevision(Long landingId, List<ComponentInput> components,
                                                String reason) {
        Landing l = landingRepo.findById(landingId)
                .orElseThrow(() -> new BusinessException("卸货单不存在: " + landingId));
        if (l.getStatus() != LandingStatus.VERIFIED) {
            throw new BusinessException("只有已核实的卸货批次可以修订分类");
        }
        Map<Long, BigDecimal> newComposition = validateAndNormalize(components, l.getVerifiedWeight());
        Map<Long, BigDecimal> oldComposition = currentComposition(l);
        if (oldComposition.equals(newComposition)) {
            throw new BusinessException("修订未改变任何物种归属");
        }

        ClassificationRevision revision = revisionRepo.save(new ClassificationRevision(l, reason));
        Voyage v = l.getVoyage();
        Set<Long> allSpecies = new HashSet<>();
        allSpecies.addAll(oldComposition.keySet());
        allSpecies.addAll(newComposition.keySet());

        Set<QuotaAccount> affected = new HashSet<>();
        for (Long speciesId : allSpecies) {
            BigDecimal delta = newComposition.getOrDefault(speciesId, BigDecimal.ZERO)
                    .subtract(oldComposition.getOrDefault(speciesId, BigDecimal.ZERO));
            if (delta.signum() == 0) continue;
            Species species = speciesRepo.findById(speciesId)
                    .orElseThrow(() -> new BusinessException("物种不存在: " + speciesId));
            QuotaAccount account = getOrCreateAccount(species, v);
            entryRepo.save(new LedgerEntry(account, LedgerType.CATCH_ADJUSTMENT, delta.negate(),
                    RefType.REVISION, revision.getId(),
                    "修订#" + revision.getId() + "（凭证 " + l.getReceiptNo() + "）物种 "
                            + species.getCode() + " 扣减调整 " + delta + " kg：" + reason));
            affected.add(account);
        }
        for (Map.Entry<Long, BigDecimal> e : newComposition.entrySet()) {
            componentRepo.save(new LandingComponent(l, speciesRepo.findById(e.getKey()).orElseThrow(),
                    e.getValue(), revision));
        }
        // 已转出配额的船舶可能因此不足：生成待处理缺口，而非自动撤销合法航次
        for (QuotaAccount account : affected) {
            refreshShortfall(account, revision.getId(), "修订#" + revision.getId() + " 导致账户欠额");
        }
        return revision;
    }

    /**
     * 推翻修订：只允许推翻当前生效的最后一笔；全额冲回其调整，分类回退到之前状态。
     */
    @Transactional
    public ClassificationRevision overturn(Long revisionId) {
        ClassificationRevision revision = revisionRepo.findById(revisionId)
                .orElseThrow(() -> new BusinessException("修订单不存在: " + revisionId));
        if (revision.getStatus() != RevisionStatus.APPLIED) {
            throw new BusinessException("该修订已被推翻");
        }
        Landing l = revision.getLanding();
        ClassificationRevision current = currentRevision(l.getId());
        if (current == null || !current.getId().equals(revisionId)) {
            throw new BusinessException("只能推翻当前生效的最后一笔修订");
        }

        Map<Long, BigDecimal> revised = compositionOf(revision);
        Map<Long, BigDecimal> previous = previousComposition(l, revision);
        Voyage v = l.getVoyage();
        Set<Long> allSpecies = new HashSet<>();
        allSpecies.addAll(revised.keySet());
        allSpecies.addAll(previous.keySet());

        Set<QuotaAccount> affected = new HashSet<>();
        for (Long speciesId : allSpecies) {
            BigDecimal delta = previous.getOrDefault(speciesId, BigDecimal.ZERO)
                    .subtract(revised.getOrDefault(speciesId, BigDecimal.ZERO));
            if (delta.signum() == 0) continue;
            Species species = speciesRepo.findById(speciesId).orElseThrow();
            QuotaAccount account = getOrCreateAccount(species, v);
            entryRepo.save(new LedgerEntry(account, LedgerType.CATCH_ADJUSTMENT, delta.negate(),
                    RefType.REVISION, revision.getId(),
                    "推翻修订#" + revision.getId() + "（凭证 " + l.getReceiptNo() + "）物种 "
                            + species.getCode() + " 冲回 " + delta.negate() + " kg"));
            affected.add(account);
        }
        revision.markOverturned();
        revisionRepo.save(revision);
        for (QuotaAccount account : affected) {
            refreshShortfall(account, revision.getId(), "推翻修订#" + revision.getId() + " 后账户仍欠额");
        }
        return revision;
    }

    /**
     * 跨季欠额结转：旧季账户的部分欠额由新季同物种同海区账户承接。
     * 单独记录承接关系与上限；amount ≤ 欠额且 ≤ 上限，旧季负余额不清零。
     */
    @Transactional
    public Carryover carryover(Long fromAccountId, Long toAccountId,
                               BigDecimal amount, BigDecimal capAmount) {
        QuotaService.requirePositive(amount);
        QuotaService.requirePositive(capAmount);
        if (amount.compareTo(capAmount) > 0) {
            throw new BusinessException("结转金额超出本笔承接上限 " + capAmount + " kg");
        }
        QuotaAccount from = accountRepo.findById(fromAccountId)
                .orElseThrow(() -> new BusinessException("旧季账户不存在: " + fromAccountId));
        QuotaAccount to = accountRepo.findById(toAccountId)
                .orElseThrow(() -> new BusinessException("新季账户不存在: " + toAccountId));
        if (!from.getSpecies().getId().equals(to.getSpecies().getId())
                || !from.getSeaArea().getId().equals(to.getSeaArea().getId())
                || !from.getVessel().getId().equals(to.getVessel().getId())) {
            throw new BusinessException("欠额结转仅限同物种、同海区、同船舶的跨季账户之间");
        }
        if (from.getSeason().getId().equals(to.getSeason().getId())) {
            throw new BusinessException("结转必须跨季节");
        }
        if (!from.getSeason().getEndDate().isBefore(LocalDate.now())) {
            throw new BusinessException("上一季尚未结束，不能结转欠额");
        }
        BigDecimal deficit = entryRepo.sumByAccountId(fromAccountId).negate();
        if (deficit.signum() <= 0) {
            throw new BusinessException("旧季账户没有欠额可结转");
        }
        if (amount.compareTo(deficit) > 0) {
            throw new BusinessException("结转金额 " + amount + " kg 超出欠额 " + deficit
                    + " kg；负余额不得被超额清零");
        }

        Carryover carryover = carryoverRepo.save(new Carryover(from, to, amount, capAmount));
        entryRepo.save(new LedgerEntry(from, LedgerType.CARRYOVER_OUT, amount,
                RefType.CARRYOVER, carryover.getId(),
                "欠额结转至 " + to.getSeason().getCode() + "（上限 " + capAmount + " kg），剩余欠额保留"));
        entryRepo.save(new LedgerEntry(to, LedgerType.CARRYOVER_IN, amount.negate(),
                RefType.CARRYOVER, carryover.getId(),
                "承接 " + from.getSeason().getCode() + " 欠额（上限 " + capAmount + " kg）"));
        refreshShortfall(from, null, "结转#" + carryover.getId() + " 后剩余欠额");
        return carryover;
    }

    /** 当前生效分类：最后一笔 APPLIED 修订的分类行；无修订则为初始分类行 */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> currentComposition(Landing l) {
        ClassificationRevision current = currentRevision(l.getId());
        if (current == null) {
            return toMap(componentRepo.findByLandingIdAndRevisionIsNull(l.getId()));
        }
        return compositionOf(current);
    }

    @Transactional(readOnly = true)
    public List<LandingComponent> currentComponents(Long landingId) {
        Landing l = landingRepo.findById(landingId)
                .orElseThrow(() -> new BusinessException("卸货单不存在: " + landingId));
        ClassificationRevision current = currentRevision(landingId);
        return current == null ? componentRepo.findByLandingIdAndRevisionIsNull(landingId)
                : componentRepo.findByRevisionId(current.getId());
    }

    private ClassificationRevision currentRevision(Long landingId) {
        ClassificationRevision current = null;
        for (ClassificationRevision r : revisionRepo.findByLandingIdOrderByIdAsc(landingId)) {
            if (r.getStatus() == RevisionStatus.APPLIED) current = r;
        }
        return current;
    }

    private Map<Long, BigDecimal> previousComposition(Landing l, ClassificationRevision revision) {
        ClassificationRevision prev = null;
        for (ClassificationRevision r : revisionRepo.findByLandingIdOrderByIdAsc(l.getId())) {
            if (r.getId() >= revision.getId()) break;
            if (r.getStatus() == RevisionStatus.APPLIED) prev = r;
        }
        return prev == null ? toMap(componentRepo.findByLandingIdAndRevisionIsNull(l.getId()))
                : compositionOf(prev);
    }

    private Map<Long, BigDecimal> compositionOf(ClassificationRevision revision) {
        return toMap(componentRepo.findByRevisionId(revision.getId()));
    }

    private Map<Long, BigDecimal> toMap(List<LandingComponent> lines) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (LandingComponent c : lines) {
            map.merge(c.getSpecies().getId(), c.getWeight(), BigDecimal::add);
        }
        return map;
    }

    /** 校验分类行：每行为正、物种存在、合计等于批次重量（重量守恒） */
    private Map<Long, BigDecimal> validateAndNormalize(List<ComponentInput> components,
                                                       BigDecimal expectedTotal) {
        if (components == null || components.isEmpty()) {
            throw new BusinessException("分类行不能为空");
        }
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ComponentInput c : components) {
            QuotaService.requirePositive(c.weight());
            Species species = speciesRepo.findByCode(c.speciesCode())
                    .orElseThrow(() -> new BusinessException("物种不存在: " + c.speciesCode()));
            map.merge(species.getId(), c.weight(), BigDecimal::add);
            total = total.add(c.weight());
        }
        if (total.compareTo(expectedTotal) != 0) {
            throw new BusinessException("分类重量合计 " + total + " kg 必须等于批次核实重量 "
                    + expectedTotal + " kg；不能凭修订增加可捕总量");
        }
        return map;
    }

    private QuotaAccount getOrCreateAccount(Species species, Voyage v) {
        return accountRepo.findBySpeciesIdAndSeaAreaIdAndSeasonIdAndVesselId(
                species.getId(), v.getSeaArea().getId(), v.getSeason().getId(), v.getVessel().getId())
                .orElseGet(() -> accountRepo.save(
                        new QuotaAccount(species, v.getSeaArea(), v.getSeason(), v.getVessel())));
    }

    /** 账户可用为负时生成/刷新待处理缺口；回正后自动了结。 */
    private void refreshShortfall(QuotaAccount account, Long revisionId, String reason) {
        BigDecimal deficit = entryRepo.sumByAccountId(account.getId()).negate();
        var pending = shortfallRepo.findFirstByAccountIdAndStatus(
                account.getId(), ShortfallStatus.PENDING);
        if (deficit.signum() > 0) {
            Shortfall s = pending.orElseGet(() ->
                    new Shortfall(account, deficit, revisionId, reason));
            s.refresh(deficit, reason + "（缺口 " + deficit + " kg）");
            shortfallRepo.save(s);
        } else {
            pending.ifPresent(s -> {
                s.markResolved();
                shortfallRepo.save(s);
            });
        }
    }
}
