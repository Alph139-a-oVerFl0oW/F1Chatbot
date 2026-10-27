package com.f1app.f1data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.f1app.f1data.domain.Circuit;
import com.f1app.f1data.domain.Constructor;
import com.f1app.f1data.domain.ConstructorStanding;
import com.f1app.f1data.domain.Driver;
import com.f1app.f1data.domain.DriverStanding;
import com.f1app.f1data.domain.QualifyingResult;
import com.f1app.f1data.domain.Race;
import com.f1app.f1data.domain.Result;
import com.f1app.f1data.dto.ConstructorStandingRow;
import com.f1app.f1data.dto.ConstructorSummary;
import com.f1app.f1data.dto.DriverProfile;
import com.f1app.f1data.dto.DriverStandingRow;
import com.f1app.f1data.dto.DriverSummary;
import com.f1app.f1data.dto.Overview;
import com.f1app.f1data.dto.QualifyingRow;
import com.f1app.f1data.dto.RaceDetail;
import com.f1app.f1data.dto.RaceSummary;
import com.f1app.f1data.dto.ResultRow;
import com.f1app.f1data.repo.ConstructorRepository;
import com.f1app.f1data.repo.ConstructorStandingRepository;
import com.f1app.f1data.repo.DriverRepository;
import com.f1app.f1data.repo.DriverStandingRepository;
import com.f1app.f1data.repo.QualifyingRepository;
import com.f1app.f1data.repo.RaceRepository;
import com.f1app.f1data.repo.ResultRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the F1 data API.
 *
 * Every method is transactional and returns DTOs rather than entities. That is
 * deliberate: open-in-view is disabled, so entities become detached the moment
 * the method returns and any lazy association touched during JSON serialisation
 * would blow up. Mapping inside the transaction also stops Jackson walking the
 * object graph and emitting the entire database in one response.
 */
@Service
@Transactional(readOnly = true)
public class F1DataService {

    private final RaceRepository raceRepository;
    private final ResultRepository resultRepository;
    private final QualifyingRepository qualifyingRepository;
    private final DriverRepository driverRepository;
    private final ConstructorRepository constructorRepository;
    private final DriverStandingRepository driverStandingRepository;
    private final ConstructorStandingRepository constructorStandingRepository;

    public F1DataService(RaceRepository raceRepository,
                         ResultRepository resultRepository,
                         QualifyingRepository qualifyingRepository,
                         DriverRepository driverRepository,
                         ConstructorRepository constructorRepository,
                         DriverStandingRepository driverStandingRepository,
                         ConstructorStandingRepository constructorStandingRepository) {
        this.raceRepository = raceRepository;
        this.resultRepository = resultRepository;
        this.qualifyingRepository = qualifyingRepository;
        this.driverRepository = driverRepository;
        this.constructorRepository = constructorRepository;
        this.driverStandingRepository = driverStandingRepository;
        this.constructorStandingRepository = constructorStandingRepository;
    }

    // ------------------------------------------------------------------
    // Seasons and races
    // ------------------------------------------------------------------

    public List<Integer> seasons() {
        return raceRepository.findSeasons();
    }

    public List<RaceSummary> racesInSeason(int season) {
        return raceRepository.findBySeason(season).stream().map(this::toRaceSummary).toList();
    }

    public Optional<RaceDetail> raceDetail(int season, int round) {
        return raceRepository.findBySeasonAndRound(season, round).map(race -> new RaceDetail(
                toRaceSummary(race),
                resultRepository.findByRaceId(race.getId()).stream().map(this::toResultRow).toList(),
                qualifyingRepository.findByRaceId(race.getId()).stream().map(this::toQualifyingRow).toList()));
    }

    public Optional<RaceSummary> latestRace() {
        List<Race> races = raceRepository.findMostRecentCompleted(PageRequest.of(0, 1));
        return races.isEmpty() ? Optional.empty() : Optional.of(toRaceSummary(races.get(0)));
    }

    public List<RaceSummary> upcomingRaces(int limit) {
        return raceRepository.findUpcoming(PageRequest.of(0, limit))
                .stream().map(this::toRaceSummary).toList();
    }

    // ------------------------------------------------------------------
    // Drivers and constructors
    // ------------------------------------------------------------------

    public Page<DriverSummary> searchDrivers(String query, Pageable pageable) {
        return driverRepository.search(query == null ? "" : query, pageable).map(this::toDriverSummary);
    }

    public List<DriverSummary> driversInSeason(int season) {
        return driverRepository.findBySeason(season).stream().map(this::toDriverSummary).toList();
    }

    public Optional<DriverProfile> driverProfile(String driverRef) {
        return driverRepository.findByDriverRef(driverRef).map(driver -> {
            BigDecimal points = resultRepository.sumPoints(driverRef);
            return new DriverProfile(
                    toDriverSummary(driver),
                    resultRepository.countStarts(driverRef),
                    resultRepository.countWins(driverRef),
                    resultRepository.countPodiums(driverRef),
                    qualifyingRepository.countPoles(driverRef),
                    driverStandingRepository.findTitles(driverRef).size(),
                    points == null ? BigDecimal.ZERO : points,
                    resultRepository.firstSeason(driverRef),
                    resultRepository.lastSeason(driverRef));
        });
    }

    public List<ResultRow> driverSeasonResults(String driverRef, int season) {
        return resultRepository.findByDriverAndSeason(driverRef, season)
                .stream().map(this::toResultRow).toList();
    }

    public List<ConstructorSummary> constructorsInSeason(int season) {
        return constructorRepository.findBySeason(season).stream().map(this::toConstructorSummary).toList();
    }

    // ------------------------------------------------------------------
    // Standings
    // ------------------------------------------------------------------

    public List<DriverStandingRow> driverStandings(int season) {
        Integer round = driverStandingRepository.findLatestRound(season);
        if (round == null) {
            return List.of();
        }
        return driverStandingRepository.findBySeasonAndRound(season, round)
                .stream().map(this::toDriverStandingRow).toList();
    }

    public List<ConstructorStandingRow> constructorStandings(int season) {
        Integer round = constructorStandingRepository.findLatestRound(season);
        if (round == null) {
            return List.of();
        }
        return constructorStandingRepository.findBySeasonAndRound(season, round)
                .stream().map(this::toConstructorStandingRow).toList();
    }

    // ------------------------------------------------------------------
    // Dashboard
    // ------------------------------------------------------------------

    /**
     * One round trip for the dashboard. Six queries server-side beats six HTTP
     * requests from the browser, each paying connection and serialisation cost.
     */
    public Overview overview() {
        List<Integer> seasons = raceRepository.findSeasons();
        Integer latestSeason = seasons.isEmpty() ? null : seasons.get(0);

        RaceSummary latest = latestRace().orElse(null);
        List<ResultRow> podium = List.of();
        if (latest != null) {
            podium = resultRepository.findByRaceId(latest.id()).stream()
                    .map(this::toResultRow)
                    .filter(row -> row.position() != null && row.position() <= 3)
                    .toList();
        }

        Integer standingsSeason = latest != null ? latest.season() : latestSeason;

        return new Overview(
                latestSeason,
                raceRepository.count(),
                driverRepository.count(),
                constructorRepository.count(),
                latest,
                podium,
                standingsSeason == null ? List.of() : driverStandings(standingsSeason),
                standingsSeason == null ? List.of() : constructorStandings(standingsSeason));
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private RaceSummary toRaceSummary(Race race) {
        Circuit circuit = race.getCircuit();
        return new RaceSummary(
                race.getId(),
                race.getSeason(),
                race.getRound(),
                race.getName(),
                race.getRaceDate(),
                race.getRaceTime(),
                circuit == null ? null : circuit.getCircuitRef(),
                circuit == null ? null : circuit.getName(),
                circuit == null ? null : circuit.getLocality(),
                circuit == null ? null : circuit.getCountry());
    }

    private ResultRow toResultRow(Result result) {
        Driver driver = result.getDriver();
        Constructor constructor = result.getConstructor();
        return new ResultRow(
                result.getPosition(),
                result.getPositionText(),
                driver.getDriverRef(),
                driver.getFullName(),
                driver.getCode(),
                constructor.getConstructorRef(),
                constructor.getName(),
                result.getGrid(),
                result.getLaps(),
                result.getPoints(),
                result.getFinishTime(),
                result.getStatus() == null ? null : result.getStatus().getStatus(),
                result.getFastestLapTime());
    }

    private QualifyingRow toQualifyingRow(QualifyingResult qualifying) {
        Driver driver = qualifying.getDriver();
        return new QualifyingRow(
                qualifying.getPosition(),
                driver.getDriverRef(),
                driver.getFullName(),
                driver.getCode(),
                qualifying.getConstructor().getName(),
                qualifying.getQ1(),
                qualifying.getQ2(),
                qualifying.getQ3());
    }

    private DriverSummary toDriverSummary(Driver driver) {
        return new DriverSummary(
                driver.getDriverRef(),
                driver.getCode(),
                driver.getPermanentNumber(),
                driver.getFullName(),
                driver.getNationality(),
                driver.getDateOfBirth());
    }

    private ConstructorSummary toConstructorSummary(Constructor constructor) {
        return new ConstructorSummary(
                constructor.getConstructorRef(),
                constructor.getName(),
                constructor.getNationality());
    }

    private DriverStandingRow toDriverStandingRow(DriverStanding standing) {
        Driver driver = standing.getDriver();
        return new DriverStandingRow(
                standing.getPosition(),
                standing.getPositionText(),
                standing.getPoints(),
                standing.getWins(),
                driver.getDriverRef(),
                driver.getFullName(),
                driver.getCode(),
                driver.getNationality());
    }

    private ConstructorStandingRow toConstructorStandingRow(ConstructorStanding standing) {
        Constructor constructor = standing.getConstructor();
        return new ConstructorStandingRow(
                standing.getPosition(),
                standing.getPositionText(),
                standing.getPoints(),
                standing.getWins(),
                constructor.getConstructorRef(),
                constructor.getName(),
                constructor.getNationality());
    }
}
