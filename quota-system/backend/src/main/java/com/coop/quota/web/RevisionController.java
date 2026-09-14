package com.coop.quota.web;

import com.coop.quota.domain.ClassificationRevision;
import com.coop.quota.dto.ComponentInput;
import com.coop.quota.dto.RevisionView;
import com.coop.quota.repo.ClassificationRevisionRepository;
import com.coop.quota.repo.LandingComponentRepository;
import com.coop.quota.service.RevisionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RevisionController {

    private final RevisionService revisionService;
    private final ClassificationRevisionRepository revisionRepo;
    private final LandingComponentRepository componentRepo;

    public RevisionController(RevisionService revisionService,
                              ClassificationRevisionRepository revisionRepo,
                              LandingComponentRepository componentRepo) {
        this.revisionService = revisionService;
        this.revisionRepo = revisionRepo;
        this.componentRepo = componentRepo;
    }

    public record RevisionRequest(String reason, List<ComponentInput> components) {}

    /** 某卸货批次的当前生效分类 */
    @GetMapping("/landings/{id}/components")
    public List<RevisionView.Line> currentComponents(@PathVariable Long id) {
        return revisionService.currentComponents(id).stream()
                .map(c -> new RevisionView.Line(c.getSpecies().getCode(),
                        c.getSpecies().getName(), c.getWeight()))
                .toList();
    }

    /** 某卸货批次的全部修订记录 */
    @GetMapping("/landings/{id}/revisions")
    public List<RevisionView> revisions(@PathVariable Long id) {
        return revisionRepo.findByLandingIdOrderByIdAsc(id).stream()
                .map(r -> RevisionView.of(r, componentRepo.findByRevisionId(r.getId())))
                .toList();
    }

    /** 应用分类修订（合计必须等于批次核实重量） */
    @PostMapping("/landings/{id}/revisions")
    public RevisionView apply(@PathVariable Long id, @RequestBody RevisionRequest req) {
        ClassificationRevision r = revisionService.applyRevision(id, req.components(), req.reason());
        return RevisionView.of(r, componentRepo.findByRevisionId(r.getId()));
    }

    /** 推翻修订：全额冲回调整，分类回退 */
    @PostMapping("/revisions/{id}/overturn")
    public RevisionView overturn(@PathVariable Long id) {
        ClassificationRevision r = revisionService.overturn(id);
        return RevisionView.of(r, componentRepo.findByRevisionId(r.getId()));
    }
}
