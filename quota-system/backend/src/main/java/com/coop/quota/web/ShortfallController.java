package com.coop.quota.web;

import com.coop.quota.dto.CarryoverView;
import com.coop.quota.dto.ShortfallView;
import com.coop.quota.repo.CarryoverRepository;
import com.coop.quota.repo.ShortfallRepository;
import com.coop.quota.service.RevisionService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ShortfallController {

    private final ShortfallRepository shortfallRepo;
    private final CarryoverRepository carryoverRepo;
    private final RevisionService revisionService;

    public ShortfallController(ShortfallRepository shortfallRepo, CarryoverRepository carryoverRepo,
                               RevisionService revisionService) {
        this.shortfallRepo = shortfallRepo;
        this.carryoverRepo = carryoverRepo;
        this.revisionService = revisionService;
    }

    @GetMapping("/shortfalls")
    public List<ShortfallView> shortfalls() {
        return shortfallRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(ShortfallView::of).toList();
    }

    @GetMapping("/carryovers")
    public List<CarryoverView> carryovers() {
        return carryoverRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(CarryoverView::of).toList();
    }

    public record CarryoverRequest(Long fromAccountId, Long toAccountId,
                                   BigDecimal amount, BigDecimal capAmount) {}

    /** 跨季欠额结转：部分承接，记录承接关系与上限，旧季负余额不清零 */
    @PostMapping("/carryovers")
    public CarryoverView carryover(@RequestBody CarryoverRequest req) {
        return CarryoverView.of(revisionService.carryover(req.fromAccountId(), req.toAccountId(),
                req.amount(), req.capAmount()));
    }
}
