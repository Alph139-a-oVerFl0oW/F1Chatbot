package com.f1app.f1data;

import java.util.List;

import com.f1app.f1data.dto.ConstructorStandingRow;
import com.f1app.f1data.dto.ConstructorSummary;
import com.f1app.f1data.dto.DriverProfile;
import com.f1app.f1data.dto.DriverStandingRow;
import com.f1app.f1data.dto.DriverSummary;
import com.f1app.f1data.dto.Overview;
import com.f1app.f1data.dto.RaceDetail;
import com.f1app.f1data.dto.RaceSummary;
import com.f1app.f1data.dto.ResultRow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api")
public class F1DataController {

    private static final int MAX_PAGE_SIZE = 100;

    private final F1DataService service;

    public F1DataController(F1DataService service) {
        this.service = service;
    }

    /** Single call that backs the dashboard. */
    @GetMapping("/overview")
    public Overview overview() {
        return service.overview();
    }

    @GetMapping("/seasons")
    public List<Integer> seasons() {
        return service.seasons();
    }

    @GetMapping("/races")
    public List<RaceSummary> races(@RequestParam int season) {
        return service.racesInSeason(season);
    }

    @GetMapping("/races/upcoming")
    public List<RaceSummary> upcoming(@RequestParam(defaultValue = "5") int limit) {
        return service.upcomingRaces(Math.min(limit, 20));
    }

    @GetMapping("/races/{season}/{round}")
    public RaceDetail race(@PathVariable int season, @PathVariable int round) {
        return service.raceDetail(season, round)
                .orElseThrow(() -> notFound("No race found for " + season + " round " + round));
    }

    @GetMapping("/drivers")
    public Page<DriverSummary> drivers(@RequestParam(required = false, defaultValue = "") String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.searchDrivers(q, PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
    }

    @GetMapping("/drivers/season/{season}")
    public List<DriverSummary> driversInSeason(@PathVariable int season) {
        return service.driversInSeason(season);
    }

    @GetMapping("/drivers/{driverRef}")
    public DriverProfile driver(@PathVariable String driverRef) {
        return service.driverProfile(driverRef)
                .orElseThrow(() -> notFound("No driver with ref " + driverRef));
    }

    @GetMapping("/drivers/{driverRef}/results")
    public List<ResultRow> driverResults(@PathVariable String driverRef, @RequestParam int season) {
        return service.driverSeasonResults(driverRef, season);
    }

    @GetMapping("/constructors")
    public List<ConstructorSummary> constructors(@RequestParam int season) {
        return service.constructorsInSeason(season);
    }

    @GetMapping("/standings/drivers")
    public List<DriverStandingRow> driverStandings(@RequestParam int season) {
        return service.driverStandings(season);
    }

    @GetMapping("/standings/constructors")
    public List<ConstructorStandingRow> constructorStandings(@RequestParam int season) {
        return service.constructorStandings(season);
    }

    /** Kept so the frontend health badge keeps working unchanged. */
    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.noContent().build();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
