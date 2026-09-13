package com.coop.quota.config;

import com.coop.quota.domain.*;
import com.coop.quota.repo.*;
import com.coop.quota.service.QuotaService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 演示种子数据：虚构物种、海区、季节、船舶与初始配额。
 * 全部为虚构内容，不对应任何真实监管目录或许可。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final SpeciesRepository speciesRepo;
    private final SeaAreaRepository areaRepo;
    private final SeasonRepository seasonRepo;
    private final VesselRepository vesselRepo;
    private final QuotaAccountRepository accountRepo;
    private final QuotaService quotaService;

    public DataSeeder(SpeciesRepository speciesRepo, SeaAreaRepository areaRepo,
                      SeasonRepository seasonRepo, VesselRepository vesselRepo,
                      QuotaAccountRepository accountRepo, QuotaService quotaService) {
        this.speciesRepo = speciesRepo;
        this.areaRepo = areaRepo;
        this.seasonRepo = seasonRepo;
        this.vesselRepo = vesselRepo;
        this.accountRepo = accountRepo;
        this.quotaService = quotaService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (speciesRepo.count() > 0) {
            return;
        }
        Species bream = speciesRepo.save(new Species("CLOUD_BREAM", "蓝鳍云鲷"));
        Species eel = speciesRepo.save(new Species("STAR_EEL", "银鳞星鳗"));
        speciesRepo.save(new Species("ROSY_SHRIMP", "紫背霞虾"));

        SeaArea north = areaRepo.save(new SeaArea("NORTH_ISLE", "北屿海区"));
        SeaArea reef = areaRepo.save(new SeaArea("EAST_REEF", "东礁海区"));

        Season spring = seasonRepo.save(new Season("2026_SPRING", "2026 春汛",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 30)));
        Season autumn = seasonRepo.save(new Season("2026_AUTUMN", "2026 秋汛",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31)));

        Vessel v1 = vesselRepo.save(new Vessel("COOP-001", "渔协 001 号"));
        Vessel v2 = vesselRepo.save(new Vessel("COOP-002", "渔协 002 号"));
        Vessel v3 = vesselRepo.save(new Vessel("COOP-003", "渔协 003 号"));
        vesselRepo.save(new Vessel("COOP-004", "渔协 004 号"));

        long batch = 1;
        allocate(bream, north, autumn, v1, "8000.000", batch, "2026 秋汛初始核拨");
        allocate(bream, north, autumn, v2, "5000.000", batch, "2026 秋汛初始核拨");
        allocate(bream, reef, autumn, v1, "3000.000", batch, "2026 秋汛初始核拨");
        allocate(eel, north, autumn, v1, "2000.000", batch, "2026 秋汛初始核拨");
        allocate(bream, north, spring, v1, "6000.000", batch, "2026 春汛初始核拨");
        allocate(eel, reef, autumn, v3, "4000.000", batch, "2026 秋汛初始核拨");
    }

    private void allocate(Species s, SeaArea a, Season season, Vessel v, String kg, long batch, String note) {
        QuotaAccount account = accountRepo.save(new QuotaAccount(s, a, season, v));
        quotaService.allocate(account, new BigDecimal(kg), batch, note);
    }
}
