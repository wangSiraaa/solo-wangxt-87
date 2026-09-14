package com.coop.quota.web;

import com.coop.quota.domain.*;
import com.coop.quota.dto.BalanceView;
import com.coop.quota.dto.LedgerEntryView;
import com.coop.quota.repo.*;
import com.coop.quota.service.QuotaService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QuotaController {

    private final QuotaService quotaService;
    private final SpeciesRepository speciesRepo;
    private final SeaAreaRepository areaRepo;
    private final SeasonRepository seasonRepo;
    private final VesselRepository vesselRepo;
    private final QuotaTransferRepository transferRepo;

    public QuotaController(QuotaService quotaService, SpeciesRepository speciesRepo,
                           SeaAreaRepository areaRepo, SeasonRepository seasonRepo,
                           VesselRepository vesselRepo, QuotaTransferRepository transferRepo) {
        this.quotaService = quotaService;
        this.speciesRepo = speciesRepo;
        this.areaRepo = areaRepo;
        this.seasonRepo = seasonRepo;
        this.vesselRepo = vesselRepo;
        this.transferRepo = transferRepo;
    }

    /** 基础数据 + 免责声明（虚构数据，不作为真实捕捞许可） */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return Map.of(
                "species", speciesRepo.findAll(),
                "areas", areaRepo.findAll(),
                "seasons", seasonRepo.findAll(),
                "vessels", vesselRepo.findAll(),
                "disclaimer", "本系统使用虚构物种与许可规则，不连接任何监管系统，不作为真实捕捞许可。");
    }

    @GetMapping("/accounts")
    public List<BalanceView> accounts() {
        return quotaService.listBalances();
    }

    @GetMapping("/accounts/{id}")
    public BalanceView account(@PathVariable Long id) {
        return quotaService.balanceOf(id);
    }

    /** 从余额追到航次与调拨记录：账户的完整账本明细 */
    @GetMapping("/accounts/{id}/ledger")
    public List<LedgerEntryView> ledger(@PathVariable Long id) {
        return quotaService.ledgerOf(id);
    }

    /** 回放：按当时分类复现账面（at=时刻 或 upToEntry=条目号，精确到某次调整前） */
    @GetMapping("/accounts/{id}/replay")
    public com.coop.quota.dto.ReplayView replay(
            @PathVariable Long id,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.Instant at,
            @RequestParam(required = false) Long upToEntry) {
        return quotaService.replay(id, at, upToEntry);
    }

    @GetMapping("/transfers")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<com.coop.quota.dto.TransferView> transfers() {
        return transferRepo.findAll().stream().map(com.coop.quota.dto.TransferView::of).toList();
    }

    public record TransferRequest(Long fromAccountId, Long toAccountId, java.math.BigDecimal amount,
                                  java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveTo,
                                  String reason) {}

    @PostMapping("/transfers")
    public com.coop.quota.dto.TransferView transfer(@RequestBody TransferRequest req) {
        return com.coop.quota.dto.TransferView.of(quotaService.transfer(req.fromAccountId(),
                req.toAccountId(), req.amount(), req.effectiveFrom(), req.effectiveTo(), req.reason()));
    }
}
