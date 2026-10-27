package com.f1app.f1data.dto;

import java.util.List;

/** Everything the dashboard needs in one request. */
public record Overview(
        Integer latestSeason,
        long totalRaces,
        long totalDrivers,
        long totalConstructors,
        RaceSummary latestRace,
        List<ResultRow> latestPodium,
        List<DriverStandingRow> driverStandings,
        List<ConstructorStandingRow> constructorStandings) {
}
