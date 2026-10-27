package com.f1app.chatbot;

import java.util.List;

import com.f1app.f1data.F1DataService;
import com.f1app.f1data.dto.ConstructorStandingRow;
import com.f1app.f1data.dto.DriverProfile;
import com.f1app.f1data.dto.DriverStandingRow;
import com.f1app.f1data.dto.DriverSummary;
import com.f1app.f1data.dto.RaceDetail;
import com.f1app.f1data.dto.RaceSummary;
import com.f1app.f1data.dto.ResultRow;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * The agent's tools.
 *
 * Deliberately typed and narrow rather than a generic "run this SQL" tool: that
 * would let the model invent columns, emit unbounded scans, and open an
 * injection path. These take primitives and go through the Phase 3 read layer.
 *
 * TOKEN BUDGET - every tool schema below is serialised into EVERY request, and
 * one question costs two calls (pick the tool, then answer with its result). At
 * nine tools, verbose descriptions pushed ~1,700 tokens per call against Groq's
 * 12,000 TPM free-tier ceiling, capping throughput at roughly three questions a
 * minute. Descriptions are therefore one line each and result rows are capped:
 * nobody asks who finished 22nd. Measure with the eval harness before expanding
 * either.
 */
@Component
public class F1Tools {

    private static final Logger log = LoggerFactory.getLogger(F1Tools.class);

    /** Enough for a full points-scoring field plus a couple of retirements. */
    private static final int MAX_ROWS = 10;

    private final F1DataService f1DataService;

    public F1Tools(F1DataService f1DataService) {
        this.f1DataService = f1DataService;
    }

    @Tool("Drivers' championship standings for a season: position, driver, wins, points.")
    public String driverStandings(@P("Season year") int season) {
        log.info("tool: driverStandings({})", season);
        List<DriverStandingRow> rows = f1DataService.driverStandings(season);
        if (rows.isEmpty()) {
            return "No drivers' standings for " + season + ".";
        }

        StringBuilder sb = new StringBuilder("Drivers' championship " + season)
                .append(progressLabel(season)).append(":\n");
        rows.stream().limit(MAX_ROWS).forEach(row -> sb
                .append(row.positionText()).append(". ")
                .append(row.driverName())
                .append(" ").append(row.points()).append("pts ")
                .append(row.wins()).append("w\n"));
        return sb.toString();
    }

    @Tool("Constructors' championship standings for a season. Did not exist before 1958.")
    public String constructorStandings(@P("Season year") int season) {
        log.info("tool: constructorStandings({})", season);
        List<ConstructorStandingRow> rows = f1DataService.constructorStandings(season);
        if (rows.isEmpty()) {
            return season < 1958
                    ? "The constructors' championship did not exist in " + season + "; it began in 1958."
                    : "No constructors' standings for " + season + ".";
        }

        StringBuilder sb = new StringBuilder("Constructors' championship " + season)
                .append(progressLabel(season)).append(":\n");
        rows.stream().limit(MAX_ROWS).forEach(row -> sb
                .append(row.positionText()).append(". ")
                .append(row.constructorName())
                .append(" ").append(row.points()).append("pts ")
                .append(row.wins()).append("w\n"));
        return sb.toString();
    }

    @Tool("Race classification with grid positions (pole = grid 1). Get the round from seasonSchedule.")
    public String raceResults(@P("Season year") int season, @P("Round number") int round) {
        log.info("tool: raceResults({}, {})", season, round);
        RaceDetail detail = f1DataService.raceDetail(season, round).orElse(null);
        if (detail == null) {
            return "No race for " + season + " round " + round + ".";
        }

        RaceSummary race = detail.race();
        StringBuilder sb = new StringBuilder(race.name() + " " + season
                + " (" + race.circuitName() + ", " + race.date() + "):\n");

        for (ResultRow row : detail.results().stream().limit(MAX_ROWS).toList()) {
            sb.append(row.positionText()).append(". ")
              .append(row.driverName())
              .append(" (").append(row.constructorName()).append(") grid ")
              .append(row.grid())
              .append(" ").append(row.time() != null ? row.time() : row.status())
              .append("\n");
        }
        return sb.toString();
    }

    @Tool("Races in a season with round numbers and circuits. Use to turn a race name into a round.")
    public String seasonSchedule(@P("Season year") int season) {
        log.info("tool: seasonSchedule({})", season);
        List<RaceSummary> races = f1DataService.racesInSeason(season);
        if (races.isEmpty()) {
            return "No races for " + season + ".";
        }

        StringBuilder sb = new StringBuilder(season + ": " + races.size() + " races\n");
        for (RaceSummary race : races) {
            sb.append(race.round()).append(". ")
              .append(race.name())
              .append(" - ").append(race.circuitName())
              .append(", ").append(race.country()).append("\n");
        }
        return sb.toString();
    }

    @Tool("Look up a driver's driverRef by name. Required before driverCareer or driverSeason.")
    public String findDriver(@P("Driver name or surname") String name) {
        log.info("tool: findDriver({})", name);
        List<DriverSummary> drivers = f1DataService
                .searchDrivers(name, PageRequest.of(0, 5)).getContent();
        if (drivers.isEmpty()) {
            return "No driver matching '" + name + "'.";
        }

        StringBuilder sb = new StringBuilder();
        for (DriverSummary driver : drivers) {
            sb.append(driver.name()).append(" = ").append(driver.driverRef()).append("\n");
        }
        return sb.toString();
    }

    @Tool("Career totals for a driver: starts, wins, podiums, poles, titles, points. Needs driverRef.")
    public String driverCareer(@P("driverRef from findDriver") String driverRef) {
        log.info("tool: driverCareer({})", driverRef);
        DriverProfile profile = f1DataService.driverProfile(driverRef).orElse(null);
        if (profile == null) {
            return "No driver '" + driverRef + "'. Use findDriver first.";
        }

        return profile.driver().name()
                + ": " + profile.starts() + " starts, "
                + profile.wins() + " wins, "
                + profile.podiums() + " podiums, "
                + profile.poles() + " poles, "
                + profile.championships() + " titles, "
                + profile.careerPoints() + " points, "
                + profile.firstSeason() + "-" + profile.lastSeason() + "\n";
    }

    @Tool("One driver's results across a season, round by round. Needs driverRef.")
    public String driverSeason(@P("driverRef from findDriver") String driverRef,
                               @P("Season year") int season) {
        log.info("tool: driverSeason({}, {})", driverRef, season);
        List<ResultRow> results = f1DataService.driverSeasonResults(driverRef, season);
        if (results.isEmpty()) {
            return "No results for '" + driverRef + "' in " + season + ".";
        }

        StringBuilder sb = new StringBuilder(driverRef + " " + season + ":\n");
        for (ResultRow row : results) {
            sb.append("P").append(row.positionText())
              .append(" (").append(row.constructorName()).append(") ")
              .append(row.points()).append("pts\n");
        }
        return sb.toString();
    }

    @Tool("The most recent race with results, and its podium.")
    public String latestRace() {
        log.info("tool: latestRace()");
        RaceSummary race = f1DataService.latestRace().orElse(null);
        if (race == null) {
            return "No races with results.";
        }

        RaceDetail detail = f1DataService.raceDetail(race.season(), race.round()).orElse(null);
        StringBuilder sb = new StringBuilder("Latest: " + race.name()
                + " (" + race.season() + " round " + race.round() + ", " + race.date() + ")\n");
        if (detail != null) {
            detail.results().stream()
                    .filter(row -> row.position() != null && row.position() <= 3)
                    .forEach(row -> sb.append(row.position()).append(". ")
                            .append(row.driverName())
                            .append(" (").append(row.constructorName()).append(")\n"));
        }
        return sb.toString();
    }

    @Tool("Range of seasons held in the database.")
    public String availableSeasons() {
        List<Integer> seasons = f1DataService.seasons();
        if (seasons.isEmpty()) {
            return "No seasons loaded.";
        }
        return "Data covers " + seasons.get(seasons.size() - 1)
                + "-" + seasons.get(0) + ".";
    }

    /**
     * Standings for a season still in progress are not final, and the model will
     * report them as "who won" unless the data says otherwise. Cheaper and more
     * reliable to state it in the tool output than to hope the system prompt holds.
     */
    private String progressLabel(int season) {
        RaceSummary latest = f1DataService.latestRace().orElse(null);
        if (latest == null || latest.season() == null || latest.season() != season) {
            return "";
        }
        List<RaceSummary> all = f1DataService.racesInSeason(season);
        if (all.isEmpty() || latest.round() == null) {
            return "";
        }
        Integer finalRound = all.get(all.size() - 1).round();
        if (finalRound != null && !finalRound.equals(latest.round())) {
            return " (IN PROGRESS after round " + latest.round() + " of " + finalRound + ", NOT final)";
        }
        return "";
    }
}
