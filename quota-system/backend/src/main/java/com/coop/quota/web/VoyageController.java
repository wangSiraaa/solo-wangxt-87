package com.coop.quota.web;

import com.coop.quota.dto.LandingView;
import com.coop.quota.dto.VoyageView;
import com.coop.quota.service.VoyageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/voyages")
public class VoyageController {

    private final VoyageService voyageService;

    public VoyageController(VoyageService voyageService) {
        this.voyageService = voyageService;
    }

    public record VoyageRequest(String voyageNo, String vesselCode, String speciesCode,
                                String areaCode, String seasonCode, BigDecimal estimatedWeight) {}

    public record LandingRequest(String portName, String receiptNo, BigDecimal estimatedWeight) {}

    public record VerifyRequest(BigDecimal verifiedWeight) {}

    @GetMapping
    public List<VoyageView> list() {
        return voyageService.listVoyages().stream().map(VoyageView::of).toList();
    }

    @PostMapping
    public VoyageView createDraft(@RequestBody VoyageRequest req) {
        return VoyageView.of(voyageService.createDraft(req.voyageNo(), req.vesselCode(),
                req.speciesCode(), req.areaCode(), req.seasonCode(), req.estimatedWeight()));
    }

    @PostMapping("/{id}/declare")
    public VoyageView declare(@PathVariable Long id) {
        return VoyageView.of(voyageService.declare(id));
    }

    @PostMapping("/{id}/landings")
    public LandingView addLanding(@PathVariable Long id, @RequestBody LandingRequest req) {
        return LandingView.of(voyageService.addLanding(id, req.portName(), req.receiptNo(), req.estimatedWeight()));
    }

    @PostMapping("/landings/{landingId}/verify")
    public LandingView verify(@PathVariable Long landingId, @RequestBody VerifyRequest req) {
        return LandingView.of(voyageService.verifyLanding(landingId, req.verifiedWeight()));
    }

    @PostMapping("/{id}/close")
    public VoyageView close(@PathVariable Long id) {
        return VoyageView.of(voyageService.close(id));
    }

    @PostMapping("/{id}/cancel")
    public VoyageView cancel(@PathVariable Long id) {
        return VoyageView.of(voyageService.cancel(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteDraft(@PathVariable Long id) {
        voyageService.deleteDraft(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/{id}/landings")
    public List<LandingView> landings(@PathVariable Long id) {
        return voyageService.landingsOf(id).stream().map(LandingView::of).toList();
    }
}
