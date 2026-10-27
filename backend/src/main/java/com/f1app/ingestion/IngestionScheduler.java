package com.f1app.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the current season current. Runs with force=true so finished races,
 * updated standings and post-race classification changes overwrite what the
 * previous run stored.
 */
@Component
public class IngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

    private final BackfillService backfillService;
    private final boolean enabled;

    public IngestionScheduler(BackfillService backfillService,
                              @Value("${app.jolpica.scheduled-refresh-enabled:true}") boolean enabled) {
        this.backfillService = backfillService;
        this.enabled = enabled;
    }

    /** Monday 03:15 UTC — after every race weekend, outside peak API hours. */
    @Scheduled(cron = "${app.jolpica.refresh-cron:0 15 3 * * MON}", zone = "UTC")
    public void refreshCurrentSeason() {
        if (!enabled) {
            return;
        }
        if (backfillService.isRunning()) {
            log.info("Skipping scheduled refresh, a backfill is running");
            return;
        }
        int season = backfillService.currentSeason();
        log.info("Scheduled refresh of season {}", season);
        backfillService.backfillAsync(season, season, true);
    }
}
