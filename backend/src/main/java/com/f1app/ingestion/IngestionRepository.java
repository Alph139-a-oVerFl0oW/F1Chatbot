package com.f1app.ingestion;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * All ingestion writes, as plain SQL upserts.
 *
 * Deliberately not JPA. Ingestion is a bulk, write-only, idempotent path where
 * ON CONFLICT DO UPDATE maps directly onto what we want and dirty-checking buys
 * nothing. Phase 3 adds JPA entities for the read side, where the object graph
 * actually helps.
 *
 * Every upsert keys on the upstream natural key, so re-running a season after a
 * rate-limit stall updates rows in place rather than duplicating them.
 */
@Repository
public class IngestionRepository {

    private final JdbcTemplate jdbc;

    // Ref -> surrogate id. Avoids a lookup per result row; a full backfill would
    // otherwise issue millions of redundant SELECTs.
    private final Map<String, Integer> driverCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> constructorCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> circuitCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> statusCache = new ConcurrentHashMap<>();

    public IngestionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clearCaches() {
        driverCache.clear();
        constructorCache.clear();
        circuitCache.clear();
        statusCache.clear();
    }

    // ------------------------------------------------------------------
    // Reference data
    // ------------------------------------------------------------------

    public void upsertSeason(int year, String url) {
        jdbc.update("""
                INSERT INTO seasons (year, url) VALUES (?, ?)
                ON CONFLICT (year) DO UPDATE SET url = EXCLUDED.url
                """, year, url);
    }

    public int upsertCircuit(JsonNode circuit) {
        String ref = JsonUtils.text(circuit, "circuitId");
        Integer cached = circuitCache.get(ref);
        if (cached != null) {
            return cached;
        }

        JsonNode location = circuit.path("Location");
        Integer id = jdbc.queryForObject("""
                INSERT INTO circuits (circuit_ref, name, locality, country, latitude, longitude, url)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (circuit_ref) DO UPDATE SET
                    name = EXCLUDED.name,
                    locality = EXCLUDED.locality,
                    country = EXCLUDED.country,
                    latitude = EXCLUDED.latitude,
                    longitude = EXCLUDED.longitude,
                    url = EXCLUDED.url
                RETURNING id
                """, Integer.class,
                ref,
                JsonUtils.text(circuit, "circuitName"),
                JsonUtils.text(location, "locality"),
                JsonUtils.text(location, "country"),
                JsonUtils.decimal(location, "lat"),
                JsonUtils.decimal(location, "long"),
                JsonUtils.text(circuit, "url"));

        circuitCache.put(ref, id);
        return id;
    }

    public int upsertDriver(JsonNode driver) {
        String ref = JsonUtils.text(driver, "driverId");
        Integer cached = driverCache.get(ref);
        if (cached != null) {
            return cached;
        }

        Integer id = jdbc.queryForObject("""
                INSERT INTO drivers (driver_ref, permanent_number, code, given_name,
                                     family_name, date_of_birth, nationality, url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (driver_ref) DO UPDATE SET
                    permanent_number = COALESCE(EXCLUDED.permanent_number, drivers.permanent_number),
                    code = COALESCE(EXCLUDED.code, drivers.code),
                    given_name = EXCLUDED.given_name,
                    family_name = EXCLUDED.family_name,
                    date_of_birth = COALESCE(EXCLUDED.date_of_birth, drivers.date_of_birth),
                    nationality = COALESCE(EXCLUDED.nationality, drivers.nationality),
                    url = COALESCE(EXCLUDED.url, drivers.url)
                RETURNING id
                """, Integer.class,
                ref,
                JsonUtils.integer(driver, "permanentNumber"),
                JsonUtils.text(driver, "code"),
                JsonUtils.text(driver, "givenName"),
                JsonUtils.text(driver, "familyName"),
                JsonUtils.date(driver, "dateOfBirth"),
                JsonUtils.text(driver, "nationality"),
                JsonUtils.text(driver, "url"));

        driverCache.put(ref, id);
        return id;
    }

    public int upsertConstructor(JsonNode constructor) {
        String ref = JsonUtils.text(constructor, "constructorId");
        Integer cached = constructorCache.get(ref);
        if (cached != null) {
            return cached;
        }

        Integer id = jdbc.queryForObject("""
                INSERT INTO constructors (constructor_ref, name, nationality, url)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (constructor_ref) DO UPDATE SET
                    name = EXCLUDED.name,
                    nationality = COALESCE(EXCLUDED.nationality, constructors.nationality),
                    url = COALESCE(EXCLUDED.url, constructors.url)
                RETURNING id
                """, Integer.class,
                ref,
                JsonUtils.text(constructor, "name"),
                JsonUtils.text(constructor, "nationality"),
                JsonUtils.text(constructor, "url"));

        constructorCache.put(ref, id);
        return id;
    }

    public void upsertStatus(int id, String status) {
        jdbc.update("""
                INSERT INTO statuses (id, status) VALUES (?, ?)
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status
                """, id, status);
        statusCache.put(status, id);
    }

    /**
     * The results endpoint reports status as free text ("Finished", "+1 Lap"),
     * not as the numeric id. Resolve against the statuses table, inventing an id
     * above the upstream range if we meet a value the status endpoint never gave us.
     */
    public Integer resolveStatusId(String statusText) {
        if (statusText == null) {
            return null;
        }
        Integer cached = statusCache.get(statusText);
        if (cached != null) {
            return cached;
        }

        List<Integer> found = jdbc.queryForList(
                "SELECT id FROM statuses WHERE status = ?", Integer.class, statusText);
        if (!found.isEmpty()) {
            statusCache.put(statusText, found.get(0));
            return found.get(0);
        }

        Integer id = jdbc.queryForObject("""
                INSERT INTO statuses (id, status)
                VALUES ((SELECT COALESCE(MAX(id), 1000) + 1 FROM statuses), ?)
                RETURNING id
                """, Integer.class, statusText);
        statusCache.put(statusText, id);
        return id;
    }

    // ------------------------------------------------------------------
    // Races
    // ------------------------------------------------------------------

    public int upsertRace(int season, int round, String name, int circuitId,
                          LocalDate raceDate, LocalTime raceTime, String url,
                          LocalDate fp1Date, LocalTime fp1Time,
                          LocalDate fp2Date, LocalTime fp2Time,
                          LocalDate fp3Date, LocalTime fp3Time,
                          LocalDate qualiDate, LocalTime qualiTime,
                          LocalDate sprintDate, LocalTime sprintTime) {
        return jdbc.queryForObject("""
                INSERT INTO races (season, round, name, circuit_id, race_date, race_time, url,
                                   fp1_date, fp1_time, fp2_date, fp2_time, fp3_date, fp3_time,
                                   qualifying_date, qualifying_time, sprint_date, sprint_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (season, round) DO UPDATE SET
                    name = EXCLUDED.name,
                    circuit_id = EXCLUDED.circuit_id,
                    race_date = COALESCE(EXCLUDED.race_date, races.race_date),
                    race_time = COALESCE(EXCLUDED.race_time, races.race_time),
                    url = COALESCE(EXCLUDED.url, races.url),
                    fp1_date = COALESCE(EXCLUDED.fp1_date, races.fp1_date),
                    fp1_time = COALESCE(EXCLUDED.fp1_time, races.fp1_time),
                    fp2_date = COALESCE(EXCLUDED.fp2_date, races.fp2_date),
                    fp2_time = COALESCE(EXCLUDED.fp2_time, races.fp2_time),
                    fp3_date = COALESCE(EXCLUDED.fp3_date, races.fp3_date),
                    fp3_time = COALESCE(EXCLUDED.fp3_time, races.fp3_time),
                    qualifying_date = COALESCE(EXCLUDED.qualifying_date, races.qualifying_date),
                    qualifying_time = COALESCE(EXCLUDED.qualifying_time, races.qualifying_time),
                    sprint_date = COALESCE(EXCLUDED.sprint_date, races.sprint_date),
                    sprint_time = COALESCE(EXCLUDED.sprint_time, races.sprint_time)
                RETURNING id
                """, Integer.class,
                season, round, name, circuitId, raceDate, raceTime, url,
                fp1Date, fp1Time, fp2Date, fp2Time, fp3Date, fp3Time,
                qualiDate, qualiTime, sprintDate, sprintTime);
    }

    public Integer findRaceId(int season, int round) {
        List<Integer> found = jdbc.queryForList(
                "SELECT id FROM races WHERE season = ? AND round = ?", Integer.class, season, round);
        return found.isEmpty() ? null : found.get(0);
    }

    // ------------------------------------------------------------------
    // Results
    // ------------------------------------------------------------------

    public void upsertResult(int raceId, int driverId, int constructorId, JsonNode result,
                             int positionOrder, Integer statusId) {
        JsonNode time = result.path("Time");
        JsonNode fastest = result.path("FastestLap");

        jdbc.update("""
                INSERT INTO results (race_id, driver_id, constructor_id, car_number, grid,
                                     position, position_text, position_order, points, laps,
                                     finish_time, finish_millis, fastest_lap, fastest_lap_rank,
                                     fastest_lap_time, fastest_lap_speed, status_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (race_id, driver_id) DO UPDATE SET
                    constructor_id = EXCLUDED.constructor_id,
                    car_number = EXCLUDED.car_number,
                    grid = EXCLUDED.grid,
                    position = EXCLUDED.position,
                    position_text = EXCLUDED.position_text,
                    position_order = EXCLUDED.position_order,
                    points = EXCLUDED.points,
                    laps = EXCLUDED.laps,
                    finish_time = EXCLUDED.finish_time,
                    finish_millis = EXCLUDED.finish_millis,
                    fastest_lap = EXCLUDED.fastest_lap,
                    fastest_lap_rank = EXCLUDED.fastest_lap_rank,
                    fastest_lap_time = EXCLUDED.fastest_lap_time,
                    fastest_lap_speed = EXCLUDED.fastest_lap_speed,
                    status_id = EXCLUDED.status_id
                """,
                raceId, driverId, constructorId,
                JsonUtils.integer(result, "number"),
                JsonUtils.integer(result, "grid"),
                JsonUtils.integer(result, "position"),
                JsonUtils.text(result, "positionText"),
                positionOrder,
                JsonUtils.decimalOrZero(result, "points"),
                JsonUtils.integer(result, "laps"),
                JsonUtils.text(time, "time"),
                JsonUtils.longValue(time, "millis"),
                JsonUtils.integer(fastest, "lap"),
                JsonUtils.integer(fastest, "rank"),
                JsonUtils.text(fastest.path("Time"), "time"),
                JsonUtils.decimal(fastest.path("AverageSpeed"), "speed"),
                statusId);
    }

    public void upsertSprintResult(int raceId, int driverId, int constructorId, JsonNode result,
                                   int positionOrder, Integer statusId) {
        JsonNode time = result.path("Time");
        JsonNode fastest = result.path("FastestLap");

        jdbc.update("""
                INSERT INTO sprint_results (race_id, driver_id, constructor_id, car_number, grid,
                                            position, position_text, position_order, points, laps,
                                            finish_time, finish_millis, fastest_lap,
                                            fastest_lap_time, status_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (race_id, driver_id) DO UPDATE SET
                    constructor_id = EXCLUDED.constructor_id,
                    car_number = EXCLUDED.car_number,
                    grid = EXCLUDED.grid,
                    position = EXCLUDED.position,
                    position_text = EXCLUDED.position_text,
                    position_order = EXCLUDED.position_order,
                    points = EXCLUDED.points,
                    laps = EXCLUDED.laps,
                    finish_time = EXCLUDED.finish_time,
                    finish_millis = EXCLUDED.finish_millis,
                    fastest_lap = EXCLUDED.fastest_lap,
                    fastest_lap_time = EXCLUDED.fastest_lap_time,
                    status_id = EXCLUDED.status_id
                """,
                raceId, driverId, constructorId,
                JsonUtils.integer(result, "number"),
                JsonUtils.integer(result, "grid"),
                JsonUtils.integer(result, "position"),
                JsonUtils.text(result, "positionText"),
                positionOrder,
                JsonUtils.decimalOrZero(result, "points"),
                JsonUtils.integer(result, "laps"),
                JsonUtils.text(time, "time"),
                JsonUtils.longValue(time, "millis"),
                JsonUtils.integer(fastest, "lap"),
                JsonUtils.text(fastest.path("Time"), "time"),
                statusId);
    }

    public void upsertQualifying(int raceId, int driverId, int constructorId, JsonNode qualifying) {
        jdbc.update("""
                INSERT INTO qualifying (race_id, driver_id, constructor_id, car_number,
                                        position, q1, q2, q3)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (race_id, driver_id) DO UPDATE SET
                    constructor_id = EXCLUDED.constructor_id,
                    car_number = EXCLUDED.car_number,
                    position = EXCLUDED.position,
                    q1 = EXCLUDED.q1,
                    q2 = EXCLUDED.q2,
                    q3 = EXCLUDED.q3
                """,
                raceId, driverId, constructorId,
                JsonUtils.integer(qualifying, "number"),
                JsonUtils.integer(qualifying, "position"),
                JsonUtils.text(qualifying, "Q1"),
                JsonUtils.text(qualifying, "Q2"),
                JsonUtils.text(qualifying, "Q3"));
    }

    public void upsertDriverStanding(int raceId, int driverId, JsonNode standing) {
        jdbc.update("""
                INSERT INTO driver_standings (race_id, driver_id, points, position, position_text, wins)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (race_id, driver_id) DO UPDATE SET
                    points = EXCLUDED.points,
                    position = EXCLUDED.position,
                    position_text = EXCLUDED.position_text,
                    wins = EXCLUDED.wins
                """,
                raceId, driverId,
                JsonUtils.decimalOrZero(standing, "points"),
                JsonUtils.integer(standing, "position"),
                JsonUtils.text(standing, "positionText"),
                JsonUtils.integerOrZero(standing, "wins"));
    }

    public void upsertConstructorStanding(int raceId, int constructorId, JsonNode standing) {
        jdbc.update("""
                INSERT INTO constructor_standings (race_id, constructor_id, points, position, position_text, wins)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (race_id, constructor_id) DO UPDATE SET
                    points = EXCLUDED.points,
                    position = EXCLUDED.position,
                    position_text = EXCLUDED.position_text,
                    wins = EXCLUDED.wins
                """,
                raceId, constructorId,
                JsonUtils.decimalOrZero(standing, "points"),
                JsonUtils.integer(standing, "position"),
                JsonUtils.text(standing, "positionText"),
                JsonUtils.integerOrZero(standing, "wins"));
    }

    // ------------------------------------------------------------------
    // Checkpoints
    // ------------------------------------------------------------------

    public boolean isComplete(String resource, Integer season) {
        List<String> status = jdbc.queryForList("""
                SELECT status FROM ingestion_checkpoints
                WHERE resource = ? AND season IS NOT DISTINCT FROM ?
                """, String.class, resource, season);
        return !status.isEmpty() && "DONE".equals(status.get(0));
    }

    public void markInProgress(String resource, Integer season) {
        jdbc.update("""
                INSERT INTO ingestion_checkpoints (resource, season, status, started_at, updated_at)
                VALUES (?, ?, 'IN_PROGRESS', now(), now())
                ON CONFLICT (resource, season) DO UPDATE SET
                    status = 'IN_PROGRESS',
                    started_at = now(),
                    updated_at = now(),
                    error_message = NULL
                """, resource, season);
    }

    public void markDone(String resource, Integer season, int records) {
        jdbc.update("""
                UPDATE ingestion_checkpoints
                SET status = 'DONE', records = ?, completed_at = now(), updated_at = now()
                WHERE resource = ? AND season IS NOT DISTINCT FROM ?
                """, records, resource, season);
    }

    public void markFailed(String resource, Integer season, String error) {
        String trimmed = error == null ? null
                : error.substring(0, Math.min(error.length(), 500));
        jdbc.update("""
                UPDATE ingestion_checkpoints
                SET status = 'FAILED', error_message = ?, updated_at = now()
                WHERE resource = ? AND season IS NOT DISTINCT FROM ?
                """, trimmed, resource, season);
    }

    public List<Map<String, Object>> checkpointSummary() {
        return jdbc.queryForList("""
                SELECT resource, status, count(*) AS entries, sum(records) AS records
                FROM ingestion_checkpoints
                GROUP BY resource, status
                ORDER BY resource, status
                """);
    }

    public Map<String, Object> rowCounts() {
        return jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM seasons) AS seasons,
                       (SELECT count(*) FROM circuits) AS circuits,
                       (SELECT count(*) FROM drivers) AS drivers,
                       (SELECT count(*) FROM constructors) AS constructors,
                       (SELECT count(*) FROM races) AS races,
                       (SELECT count(*) FROM results) AS results,
                       (SELECT count(*) FROM qualifying) AS qualifying,
                       (SELECT count(*) FROM sprint_results) AS sprint_results,
                       (SELECT count(*) FROM driver_standings) AS driver_standings,
                       (SELECT count(*) FROM constructor_standings) AS constructor_standings
                """);
    }

    public List<Integer> seasonsInDatabase() {
        return jdbc.queryForList("SELECT year FROM seasons ORDER BY year", Integer.class);
    }
}
