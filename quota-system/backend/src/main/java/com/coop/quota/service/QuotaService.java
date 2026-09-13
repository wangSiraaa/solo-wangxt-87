package com.coop.quota.service;

import com.coop.quota.domain.*;
import com.coop.quota.dto.BalanceView;
import com.coop.quota.dto.LedgerEntryView;
import com.coop.quota.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 配额账户与调拨服务。所有余额均由账本条目汇总，调拨只新增条目，不改余额。
 */
@Service
public class QuotaService {

    private final QuotaAccountRepository accountRepo;
    private final LedgerEntryRepository entryRepo;
    private final QuotaTransferRepository transferRepo;
    private final VoyageRepository voyageRepo;
    private final LandingRepository landingRepo;

    public QuotaService(QuotaAccountRepository accountRepo, LedgerEntryRepository entryRepo,
                        QuotaTransferRepository transferRepo, VoyageRepository voyageRepo,
                        LandingRepository landingRepo) {
        this.accountRepo = accountRepo;
        this.entryRepo = entryRepo;
        this.transferRepo = transferRepo;
        this.voyageRepo = voyageRepo;
        this.landingRepo = landingRepo;
    }

    /** 核拨初始配额（演示数据/批次导入用），产生一条 ALLOCATION 条目。 */
    @Transactional
    public LedgerEntry allocate(QuotaAccount account, BigDecimal amount, Long batchId, String note) {
        requirePositive(amount);
        return entryRepo.save(new LedgerEntry(account, LedgerType.ALLOCATION, amount,
                RefType.ALLOCATION, batchId, note));
    }

    /**
     * 配额调拨：校验同维度、生效期间与可用余额后，写入一出一进两条条目。
     * 来源账户与生效期间持久化在 QuotaTransfer 上，可随时回溯。
     */
    @Transactional
    public QuotaTransfer transfer(Long fromAccountId, Long toAccountId, BigDecimal amount,
                                  LocalDate effectiveFrom, LocalDate effectiveTo, String reason) {
        requirePositive(amount);
        if (Objects.equals(fromAccountId, toAccountId)) {
            throw new BusinessException("调拨来源与去向不能是同一账户");
        }
        if (effectiveFrom == null || effectiveTo == null || effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException("调拨生效期间非法");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(effectiveFrom) || today.isAfter(effectiveTo)) {
            throw new BusinessException("当前日期不在调拨生效期间 " + effectiveFrom + " ~ " + effectiveTo + " 内");
        }
        QuotaAccount from = accountRepo.findById(fromAccountId)
                .orElseThrow(() -> new BusinessException("来源账户不存在: " + fromAccountId));
        QuotaAccount to = accountRepo.findById(toAccountId)
                .orElseThrow(() -> new BusinessException("去向账户不存在: " + toAccountId));

        // 物种/海区/季节不匹配的额度不得互相顶替：只允许同维度账户间调拨
        if (!from.getSpecies().getId().equals(to.getSpecies().getId())
                || !from.getSeaArea().getId().equals(to.getSeaArea().getId())
                || !from.getSeason().getId().equals(to.getSeason().getId())) {
            throw new BusinessException("物种、海区或季节不匹配，额度不得互相顶替");
        }

        BigDecimal available = available(fromAccountId);
        if (available.compareTo(amount) < 0) {
            throw new BusinessException("来源账户可用余额不足：可用 " + available + " kg，申请调出 " + amount + " kg");
        }

        QuotaTransfer transfer = transferRepo.save(
                new QuotaTransfer(from, to, amount, effectiveFrom, effectiveTo, reason));
        entryRepo.save(new LedgerEntry(from, LedgerType.TRANSFER_OUT, amount.negate(),
                RefType.TRANSFER, transfer.getId(), "调出至 " + to.getVessel().getCode() + "：" + reason));
        entryRepo.save(new LedgerEntry(to, LedgerType.TRANSFER_IN, amount,
                RefType.TRANSFER, transfer.getId(), "来自 " + from.getVessel().getCode() + "：" + reason));
        return transfer;
    }

    /** 可用余额 = 账户全部条目之和 */
    @Transactional(readOnly = true)
    public BigDecimal available(Long accountId) {
        return entryRepo.sumByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public List<BalanceView> listBalances() {
        List<BalanceView> result = new ArrayList<>();
        for (QuotaAccount a : accountRepo.findAll()) {
            result.add(toBalanceView(a));
        }
        result.sort(Comparator.comparing(BalanceView::vesselCode)
                .thenComparing(BalanceView::speciesCode)
                .thenComparing(BalanceView::areaCode)
                .thenComparing(BalanceView::seasonCode));
        return result;
    }

    @Transactional(readOnly = true)
    public BalanceView balanceOf(Long accountId) {
        QuotaAccount a = accountRepo.findById(accountId)
                .orElseThrow(() -> new BusinessException("账户不存在: " + accountId));
        return toBalanceView(a);
    }

    private BalanceView toBalanceView(QuotaAccount a) {
        Map<LedgerType, BigDecimal> sums = new EnumMap<>(LedgerType.class);
        for (Object[] row : entryRepo.sumByAccountIdGroupByType(a.getId())) {
            sums.put((LedgerType) row[0], (BigDecimal) row[1]);
        }
        BigDecimal quota = sums.getOrDefault(LedgerType.ALLOCATION, BigDecimal.ZERO)
                .add(sums.getOrDefault(LedgerType.TRANSFER_IN, BigDecimal.ZERO))
                .add(sums.getOrDefault(LedgerType.TRANSFER_OUT, BigDecimal.ZERO));
        BigDecimal reserved = sums.getOrDefault(LedgerType.RESERVATION, BigDecimal.ZERO)
                .add(sums.getOrDefault(LedgerType.RESERVATION_RELEASE, BigDecimal.ZERO)).negate();
        BigDecimal actual = sums.getOrDefault(LedgerType.ACTUAL_DEDUCTION, BigDecimal.ZERO).negate();
        BigDecimal available = quota.subtract(reserved).subtract(actual);
        return new BalanceView(a.getId(),
                a.getSpecies().getCode(), a.getSpecies().getName(),
                a.getSeaArea().getCode(), a.getSeaArea().getName(),
                a.getSeason().getCode(), a.getSeason().getName(),
                a.getVessel().getCode(), a.getVessel().getName(),
                quota, reserved, actual, available);
    }

    /** 账户账本明细：从余额追溯到具体航次/卸货/调拨单据。 */
    @Transactional(readOnly = true)
    public List<LedgerEntryView> ledgerOf(Long accountId) {
        List<LedgerEntryView> result = new ArrayList<>();
        for (LedgerEntry e : entryRepo.findByAccountIdOrderByCreatedAtAscIdAsc(accountId)) {
            result.add(LedgerEntryView.of(e, refLabel(e)));
        }
        return result;
    }

    private String refLabel(LedgerEntry e) {
        return switch (e.getRefType()) {
            case VOYAGE -> voyageRepo.findById(e.getRefId()).map(Voyage::getVoyageNo)
                    .orElse("航次#" + e.getRefId());
            case LANDING -> landingRepo.findById(e.getRefId())
                    .map(l -> l.getReceiptNo() + "@" + l.getPortName())
                    .orElse("卸货#" + e.getRefId());
            case TRANSFER -> "调拨#" + e.getRefId();
            case ALLOCATION -> "核拨批次#" + e.getRefId();
        };
    }

    static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("重量必须为正数");
        }
    }
}
