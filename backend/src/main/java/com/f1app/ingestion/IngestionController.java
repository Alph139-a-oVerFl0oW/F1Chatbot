package com.f1app.ingestion;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator endpoints for driving the backfill. Open in Phase 1/2; Phase 5 will
 * put these behind an admin role.
 */
@RestController
@RequestMapping("/api/admin/ingestion")
public class IngestionController {

    private final BackfillService backfillService;
    private final IngestionRepository repository;

    public IngestionController(BackfillService backfillService, IngestionRepository repository) {
        this.backfillService = backfillService;
        this.repository = repository;
    }

    /**
     * Kicks off a backfill in the background and returns immediately.
     * Defaults to the full history; narrow it while testing:
     *   POST /api/admin/ingestion/backfill?from=2023&to=2024
     */
    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> backfill(
            @RequestParam(required = false) Integer from,
            @RequestParam(required = false) Integer to,
            @RequestParam(defaultValue = "false") boolean force) {

        if (backfillService.isRunning()) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", "ALREADY_RUNNING",
                    "message", "A backfill is already in progress"));
        }

        int fromSeason = from != null ? from : backfillService.firstSeason();
        int toSeason = to != null ? to : backfillService.currentSeason();

        backfillService.backfillAsync(fromSeason, toSeason, force);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "STARTED");
        body.put("from", fromSeason);
        body.put("to", toSeason);
        body.put("force", force);
        body.put("hint", "Poll GET /api/admin/ingestion/status or tail the backend logs");
        return ResponseEntity.accepted().body(body);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("running", backfillService.isRunning());
        body.put("rowCounts", repository.rowCounts());
        body.put("checkpoints", repository.checkpointSummary());
        return body;
    }
}
