package com.coop.quota.service;

import com.coop.quota.domain.*;
import com.coop.quota.repo.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

/**
 * 航次与卸货服务。
 * 申报占用预计额度 → 靠港核实转为实扣并释放差额 → 结案释放剩余占用。
 * 卸货凭证号幂等：同一凭证多次回传不重复扣减。
 */
@Service
public class VoyageService {

    private final VoyageRepository voyageRepo;
    private final LandingRepository landingRepo;
    private final QuotaAccountRepository accountRepo;
    private final LedgerEntryRepository entryRepo;
    private final VesselRepository vesselRepo;
    private final SpeciesRepository speciesRepo;
    private final SeaAreaRepository areaRepo;
    private final SeasonRepository seasonRepo;

    public VoyageService(VoyageRepository voyageRepo, LandingRepository landingRepo,
                         QuotaAccountRepository accountRepo, LedgerEntryRepository entryRepo,
                         VesselRepository vesselRepo, SpeciesRepository speciesRepo,
                         SeaAreaRepository areaRepo, SeasonRepository seasonRepo) {
        this.voyageRepo = voyageRepo;
        this.landingRepo = landingRepo;
        this.accountRepo = accountRepo;
        this.entryRepo = entryRepo;
        this.vesselRepo = vesselRepo;
        this.speciesRepo = speciesRepo;
        this.areaRepo = areaRepo;
        this.seasonRepo = seasonRepo;
    }

    /** 建草稿：不占用任何额度。 */
    @Transactional
    public Voyage createDraft(String voyageNo, String vesselCode, String speciesCode,
                              String areaCode, String seasonCode, BigDecimal estimatedWeight) {
        QuotaService.requirePositive(estimatedWeight);
        if (voyageRepo.findByVoyageNo(voyageNo).isPresent()) {
            throw new BusinessException("航次号已存在: " + voyageNo);
        }
        Voyage v = new Voyage(voyageNo,
                vesselRepo.findByCode(vesselCode).orElseThrow(() -> new BusinessException("船舶不存在: " + vesselCode)),
                speciesRepo.findByCode(speciesCode).orElseThrow(() -> new BusinessException("物种不存在: " + speciesCode)),
                areaRepo.findByCode(areaCode).orElseThrow(() -> new BusinessException("海区不存在: " + areaCode)),
                seasonRepo.findByCode(seasonCode).orElseThrow(() -> new BusinessException("季节不存在: " + seasonCode)),
                estimatedWeight);
        return voyageRepo.save(v);
    }

    /**
     * 申报：按 (物种,海区,季节,船舶) 精确定位账户并占用预计额度。
     * 维度不匹配的账户不会被触碰；余额不足则申报失败（两个航次争用时后者失败）。
     */
    @Transactional
    public Voyage declare(Long voyageId) {
        Voyage v = voyageRepo.findById(voyageId)
                .orElseThrow(() -> new BusinessException("航次不存在: " + voyageId));
        if (v.getStatus() != VoyageStatus.DRAFT) {
            throw new BusinessException("只有草稿航次可以申报，当前状态: " + v.getStatus());
        }
        QuotaAccount account = accountOf(v);
        BigDecimal available = entryRepo.sumByAccountId(account.getId());
        if (available.compareTo(v.getEstimatedWeight()) < 0) {
            throw new BusinessException("可用额度不足：可用 " + available + " kg，申报占用 "
                    + v.getEstimatedWeight() + " kg");
        }
        entryRepo.save(new LedgerEntry(account, LedgerType.RESERVATION,
                v.getEstimatedWeight().negate(), RefType.VOYAGE, v.getId(),
                "航次 " + v.getVoyageNo() + " 申报占用预计额度"));
        v.markDeclared();
        return voyageRepo.save(v);
    }

    /**
     * 登记卸货单（待确认）。按凭证号幂等：同一 receiptNo 重复回传直接返回原单，
     * 不会产生第二笔扣减。待确认的称重不计入最终捕捞量（不落任何账本条目）。
     */
    @Transactional
    public Landing addLanding(Long voyageId, String portName, String receiptNo, BigDecimal estimatedWeight) {
        QuotaService.requirePositive(estimatedWeight);
        Voyage v = voyageRepo.findById(voyageId)
                .orElseThrow(() -> new BusinessException("航次不存在: " + voyageId));
        if (v.getStatus() != VoyageStatus.DECLARED) {
            throw new BusinessException("只有已申报航次可以登记卸货，当前状态: " + v.getStatus());
        }
        var existing = landingRepo.findByReceiptNo(receiptNo);
        if (existing.isPresent()) {
            return existing.get(); // 幂等回传：同一凭证不重复建单
        }
        try {
            return landingRepo.saveAndFlush(new Landing(v, portName, receiptNo, estimatedWeight));
        } catch (DataIntegrityViolationException e) {
            // 并发回传同一凭证触发唯一约束：本事务回滚，客户端重试时经上方预检命中原单
            throw new BusinessException("凭证号已存在，请重试: " + receiptNo);
        }
    }

    /**
     * 核实卸货：释放本票预计占用，按核实重量实扣。
     * 预计大于实捕 → 差额回到可用余额；实捕大于预计 → 超出部分须有余额兜底，否则拒绝。
     * 重复核实同一凭证（相同重量）幂等返回，不重复扣减。
     */
    @Transactional
    public Landing verifyLanding(Long landingId, BigDecimal verifiedWeight) {
        QuotaService.requirePositive(verifiedWeight);
        Landing l = landingRepo.findById(landingId)
                .orElseThrow(() -> new BusinessException("卸货单不存在: " + landingId));
        if (l.getStatus() == LandingStatus.VERIFIED) {
            if (l.getVerifiedWeight().compareTo(verifiedWeight) == 0) {
                return l; // 同一凭证同一重量重复回传：幂等
            }
            throw new BusinessException("凭证 " + l.getReceiptNo() + " 已核实为 "
                    + l.getVerifiedWeight() + " kg，与本次回传 " + verifiedWeight + " kg 不一致");
        }
        Voyage v = l.getVoyage();
        if (v.getStatus() != VoyageStatus.DECLARED) {
            throw new BusinessException("航次状态不允许核实卸货: " + v.getStatus());
        }
        QuotaAccount account = accountOf(v);

        // 释放本票预计占用（不超过该航次剩余占用），再按实重扣减
        BigDecimal remainingReserved = remainingReservation(v.getId());
        BigDecimal release = l.getEstimatedWeight().min(remainingReserved);
        BigDecimal availableAfter = entryRepo.sumByAccountId(account.getId())
                .add(release).subtract(verifiedWeight);
        if (availableAfter.signum() < 0) {
            throw new BusinessException("实捕超出占用且余额不足：实扣 " + verifiedWeight
                    + " kg，释放占用 " + release + " kg 后仍超支");
        }
        if (release.signum() > 0) {
            entryRepo.save(new LedgerEntry(account, LedgerType.RESERVATION_RELEASE, release,
                    RefType.LANDING, l.getId(),
                    "凭证 " + l.getReceiptNo() + " 核实，释放预计占用"));
        }
        entryRepo.save(new LedgerEntry(account, LedgerType.ACTUAL_DEDUCTION, verifiedWeight.negate(),
                RefType.LANDING, l.getId(),
                "凭证 " + l.getReceiptNo() + " 于 " + l.getPortName() + " 核实实扣"));
        l.markVerified(verifiedWeight);
        return landingRepo.save(l);
    }

    /** 结案：释放航次剩余占用。 */
    @Transactional
    public Voyage close(Long voyageId) {
        Voyage v = voyageRepo.findById(voyageId)
                .orElseThrow(() -> new BusinessException("航次不存在: " + voyageId));
        if (v.getStatus() != VoyageStatus.DECLARED) {
            throw new BusinessException("只有已申报航次可以结案，当前状态: " + v.getStatus());
        }
        if (landingRepo.countByVoyageIdAndStatus(voyageId, LandingStatus.PENDING) > 0) {
            throw new BusinessException("存在待确认的称重，不能结案");
        }
        releaseRemaining(v);
        v.markClosed();
        return voyageRepo.save(v);
    }

    /** 取消：仅允许无已核实卸货的航次，释放全部占用。 */
    @Transactional
    public Voyage cancel(Long voyageId) {
        Voyage v = voyageRepo.findById(voyageId)
                .orElseThrow(() -> new BusinessException("航次不存在: " + voyageId));
        if (v.getStatus() != VoyageStatus.DECLARED && v.getStatus() != VoyageStatus.DRAFT) {
            throw new BusinessException("当前状态不允许取消: " + v.getStatus());
        }
        if (landingRepo.countByVoyageIdAndStatus(voyageId, LandingStatus.VERIFIED) > 0) {
            throw new BusinessException("已存在核实卸货，不能取消（实际捕捞事实必须保留）");
        }
        if (v.getStatus() == VoyageStatus.DECLARED) {
            releaseRemaining(v);
        }
        v.markCancelled();
        return voyageRepo.save(v);
    }

    /**
     * 删除草稿：只允许 DRAFT 状态。草稿从未落过账本条目，
     * 而已核实的卸货与实扣条目永远保留——删除草稿不会丢失任何实际捕捞事实。
     */
    @Transactional
    public void deleteDraft(Long voyageId) {
        Voyage v = voyageRepo.findById(voyageId)
                .orElseThrow(() -> new BusinessException("航次不存在: " + voyageId));
        if (v.getStatus() != VoyageStatus.DRAFT) {
            throw new BusinessException("只有草稿可以删除；已申报/结案航次及其捕捞事实必须保留");
        }
        voyageRepo.delete(v);
    }

    @Transactional(readOnly = true)
    public BigDecimal remainingReservation(Long voyageId) {
        // RESERVATION 为负、RELEASE 为正；航次级条目 + 该航次所有卸货单的释放，合计取负即剩余占用
        BigDecimal voyageLevel = entryRepo.sumByRefAndTypes(RefType.VOYAGE, voyageId,
                EnumSet.of(LedgerType.RESERVATION, LedgerType.RESERVATION_RELEASE));
        BigDecimal landingReleases = entryRepo.sumLandingReleasesByVoyageId(voyageId);
        return voyageLevel.add(landingReleases).negate();
    }

    private void releaseRemaining(Voyage v) {
        BigDecimal remaining = remainingReservation(v.getId());
        if (remaining.signum() > 0) {
            QuotaAccount account = accountOf(v);
            entryRepo.save(new LedgerEntry(account, LedgerType.RESERVATION_RELEASE, remaining,
                    RefType.VOYAGE, v.getId(), "航次 " + v.getVoyageNo() + " 结案/取消，释放剩余占用"));
        }
    }

    private QuotaAccount accountOf(Voyage v) {
        return accountRepo.findBySpeciesIdAndSeaAreaIdAndSeasonIdAndVesselId(
                v.getSpecies().getId(), v.getSeaArea().getId(),
                v.getSeason().getId(), v.getVessel().getId())
                .orElseThrow(() -> new BusinessException(
                        "该船舶在 物种/海区/季节 维度下没有配额账户，额度不得跨维度顶替"));
    }

    @Transactional(readOnly = true)
    public List<Voyage> listVoyages() {
        return voyageRepo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Landing> landingsOf(Long voyageId) {
        return landingRepo.findByVoyageIdOrderByIdAsc(voyageId);
    }
}
