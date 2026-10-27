package com.f1app.f1data.dto;

import java.math.BigDecimal;

public record DriverProfile(
        DriverSummary driver,
        long starts,
        long wins,
        long podiums,
        long poles,
        long championships,
        BigDecimal careerPoints,
        Integer firstSeason,
        Integer lastSeason) {
}
