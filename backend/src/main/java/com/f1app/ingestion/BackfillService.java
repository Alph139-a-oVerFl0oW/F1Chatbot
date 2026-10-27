package com.f1app.ingestion;

import java.time.Year;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Walks the Jolpica API season by season and upserts everything into Postgres.
 *
 * Each (resource, season) pair is checkpointed, so an interrupted run resumes
 * where it stopped instead of re-fetching from 1950. At roughly 450 requests an
 * hour, a full history backfill takes on the order of an hour — the checkpoints
 * are what make that survivable.
 *
 * Per-lap data (laps, pit stops) is intentionally not fetched: those endpoints
 * are per-race only and would take days at this rate limit.
 */
@Service
public class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);

    static final String RES_REFERENCE = "reference";
    static final String RES_RACES = "races";
    static final String RES_RESULTS = "results";
    static final String RES_QUALIFYING = "qualifying";
    static final String RES_SPRINT = "sprint";
    static final String RES_DRIVER_STANDINGS = "driver_standings";
    static final String RES_CONSTRUCTOR_STANDINGS = "constructor_standings";

    private final JolpicaClient client;
    private final IngestionRepository repository;
    private final JolpicaProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BackfillService(JolpicaClient client, IngestionRepository repository,
                           JolpicaProperties properties) {
        this.client = client;
        this.repository = repository;
        this.properties = properties;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Runs on a background thread so the HTTP request that triggers it returns
     * immediately. Only one backfill may run at a time.
     */
    @Async("ingestionExecutor")
    public void backfillAsync(int fromSeason, int toSeason, boolean force) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Backfill already running, ignoring request");
            return;
        }
        try {
            backfill(fromSeason, toSeason, force);
        } catch (RuntimeException e) {
            log.error("Backfill aborted: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }

    public void backfill(int fromSeason, int toSeason, boolean force) {
        long started = System.currentTimeMillis();
        log.info("Backfill starting: seasons {}..{} (force={})", fromSeason, toSeason, force);

        ingestReferenceData(force);

        for (int season = fromSeason; season <= toSeason; season++) {
            ingestSeason(season, force);
        }

        long seconds = (System.currentTimeMillis() - started) / 1000;
        log.info("Backfill finished in {}s. Row counts: {}", seconds, repository.rowCounts());
    }

    // ------------------------------------------------------------------
    // Reference data: seasons, circuits, drivers, constructors, statuses
    // ------------------------------------------------------------------

    private void ingestReferenceData(boolean force) {
        if (!force && repository.isComplete(RES_REFERENCE, null)) {
            log.info("Reference data already ingested, skipping");
            return;
        }

        repository.markInProgress(RES_REFERENCE, null);
        try {
            int count = 0;

            for (JsonNode season : client.fetchAll("/seasons.json", "SeasonTable", "Seasons")) {
                Integer year = JsonUtils.integer(season, "season");
                if (year != null) {
                    repository.upsertSeason(year, JsonUtils.text(season, "url"));
                    count++;
                }
            }

            for (JsonNode circuit : client.fetchAll("/circuits.json", "CircuitTable", "Circuits")) {
                repository.upsertCircuit(circuit);
                count++;
            }

            for (JsonNode driver : client.fetchAll("/drivers.json", "DriverTable", "Drivers")) {
                repository.upsertDriver(driver);
                count++;
            }

            for (JsonNode constructor : client.fetchAll("/constructors.json", "ConstructorTable", "Constructors")) {
                repository.upsertConstructor(constructor);
                count++;
            }

            for (JsonNode status : client.fetchAll("/status.json", "StatusTable", "Status")) {
                Integer id = JsonUtils.integer(status, "statusId");
                String text = JsonUtils.text(status, "status");
                if (id != null && text != null) {
                    repository.upsertStatus(id, text);
                    count++;
                }
            }

            repository.markDone(RES_REFERENCE, null, count);
            log.info("Reference data ingested: {} records", count);

        } catch (RuntimeException e) {
            repository.markFailed(RES_REFERENCE, null, e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // Per-season resources
    // ------------------------------------------------------------------

    public void ingestSeason(int season, boolean force) {
        ingestRaces(season, force);
        ingestResults(season, force);
        ingestQualifying(season, force);
        ingestSprint(season, force);
        ingestDriverStandings(season, force);
        ingestConstructorStandings(season, force);
    }

    private void ingestRaces(int season, boolean force) {
        run(RES_RACES, season, force, () -> {
            int count = 0;
            for (JsonNode race : client.fetchAll("/" + season + "/races.json", "RaceTable", "Races")) {
                upsertRaceNode(season, race);
                count++;
            }
            return count;
        });
    }

    private void ingestResults(int season, boolean force) {
        run(RES_RESULTS, season, force, () -> {
            int count = 0;
            for (JsonNode race : client.fetchAll("/" + season + "/results.json", "RaceTable", "Races")) {
                int raceId = upsertRaceNode(season, race);
                JsonNode results = race.path("Results");
                int index = 0;
                for (JsonNode result : results) {
                    index++;
                    int driverId = repository.upsertDriver(result.path("Driver"));
                    int constructorId = repository.upsertConstructor(result.path("Constructor"));
                    Integer statusId = repository.resolveStatusId(JsonUtils.text(result, "status"));
                    Integer position = JsonUtils.integer(result, "position");
                    int positionOrder = position != null ? position : index;
                    repository.upsertResult(raceId, driverId, constructorId, result, positionOrder, statusId);
                    count++;
                }
            }
            return count;
        });
    }

    private void ingestQualifying(int season, boolean force) {
        run(RES_QUALIFYING, season, force, () -> {
            int count = 0;
            for (JsonNode race : client.fetchAll("/" + season + "/qualifying.json", "RaceTable", "Races")) {
                int raceId = upsertRaceNode(season, race);
                for (JsonNode qualifying : race.path("QualifyingResults")) {
                    int driverId = repository.upsertDriver(qualifying.path("Driver"));
                    int constructorId = repository.upsertConstructor(qualifying.path("Constructor"));
                    repository.upsertQualifying(raceId, driverId, constructorId, qualifying);
                    count++;
                }
            }
            return count;
        });
    }

    private void ingestSprint(int season, boolean force) {
        run(RES_SPRINT, season, force, () -> {
            int count = 0;
            for (JsonNode race : client.fetchAll("/" + season + "/sprint.json", "RaceTable", "Races")) {
                int raceId = upsertRaceNode(season, race);
                int index = 0;
                for (JsonNode result : race.path("SprintResults")) {
                    index++;
                    int driverId = repository.upsertDriver(result.path("Driver"));
                    int constructorId = repository.upsertConstructor(result.path("Constructor"));
                    Integer statusId = repository.resolveStatusId(JsonUtils.text(result, "status"));
                    Integer position = JsonUtils.integer(result, "position");
                    int positionOrder = position != null ? position : index;
                    repository.upsertSprintResult(raceId, driverId, constructorId, result, positionOrder, statusId);
                    count++;
                }
            }
            return count;
        });
    }

    /**
     * Standings are stored as the end-of-season snapshot only. Per-round history
     * would mean one request per round (about 1,100 extra requests) — worth doing
     * later for recent seasons if the charts in Phase 7 need the progression.
     */
    private void ingestDriverStandings(int season, boolean force) {
        run(RES_DRIVER_STANDINGS, season, force, () -> {
            int count = 0;
            List<JsonNode> lists = client.fetchAll(
                    "/" + season + "/driverstandings.json", "StandingsTable", "StandingsLists");
            for (JsonNode list : lists) {
                Integer round = JsonUtils.integer(list, "round");
                if (round == null) {
                    continue;
                }
                Integer raceId = repository.findRaceId(season, round);
                if (raceId == null) {
                    log.warn("No race {}/{} for driver standings, skipping", season, round);
                    continue;
                }
                for (JsonNode standing : list.path("DriverStandings")) {
                    int driverId = repository.upsertDriver(standing.path("Driver"));
                    repository.upsertDriverStanding(raceId, driverId, standing);
                    count++;
                }
            }
            return count;
        });
    }

    private void ingestConstructorStandings(int season, boolean force) {
        run(RES_CONSTRUCTOR_STANDINGS, season, force, () -> {
            int count = 0;
            List<JsonNode> lists = client.fetchAll(
                    "/" + season + "/constructorstandings.json", "StandingsTable", "StandingsLists");
            for (JsonNode list : lists) {
                Integer round = JsonUtils.integer(list, "round");
                if (round == null) {
                    continue;
                }
                Integer raceId = repository.findRaceId(season, round);
                if (raceId == null) {
                    continue;
                }
                for (JsonNode standing : list.path("ConstructorStandings")) {
                    int constructorId = repository.upsertConstructor(standing.path("Constructor"));
                    repository.upsertConstructorStanding(raceId, constructorId, standing);
                    count++;
                }
            }
            return count;
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * The results, qualifying and sprint endpoints each embed the full race
     * object, so races get upserted from whichever endpoint sees them first.
     */
    private int upsertRaceNode(int season, JsonNode race) {
        int circuitId = repository.upsertCircuit(race.path("Circuit"));
        Integer round = JsonUtils.integer(race, "round");
        if (round == null) {
            throw new IngestionException("Race without a round in season " + season);
        }

        JsonNode fp1 = race.path("FirstPractice");
        JsonNode fp2 = race.path("SecondPractice");
        JsonNode fp3 = race.path("ThirdPractice");
        JsonNode quali = race.path("Qualifying");
        JsonNode sprint = race.path("Sprint");

        return repository.upsertRace(
                season, round,
                JsonUtils.text(race, "raceName"),
                circuitId,
                JsonUtils.date(race, "date"),
                JsonUtils.time(race, "time"),
                JsonUtils.text(race, "url"),
                JsonUtils.date(fp1, "date"), JsonUtils.time(fp1, "time"),
                JsonUtils.date(fp2, "date"), JsonUtils.time(fp2, "time"),
                JsonUtils.date(fp3, "date"), JsonUtils.time(fp3, "time"),
                JsonUtils.date(quali, "date"), JsonUtils.time(quali, "time"),
                JsonUtils.date(sprint, "date"), JsonUtils.time(sprint, "time"));
    }

    private void run(String resource, int season, boolean force, IngestStep step) {
        if (!force && repository.isComplete(resource, season)) {
            return;
        }
        repository.markInProgress(resource, season);
        try {
            int count = step.execute();
            repository.markDone(resource, season, count);
            if (count > 0) {
                log.info("{} {}: {} records", resource, season, count);
            }
        } catch (RuntimeException e) {
            repository.markFailed(resource, season, e.getMessage());
            log.error("{} {} failed: {}", resource, season, e.getMessage());
            throw e;
        }
    }

    public int currentSeason() {
        return Year.now().getValue();
    }

    public int firstSeason() {
        return properties.getFirstSeason();
    }

    @FunctionalInterface
    private interface IngestStep {
        int execute();
    }
}
