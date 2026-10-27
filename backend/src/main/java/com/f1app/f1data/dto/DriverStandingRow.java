package com.f1app.f1data.dto;

import java.math.BigDecimal;

public record DriverStandingRow(
        Integer position,
        String positionText,
        BigDecimal points,
        Integer wins,
        String driverRef,
        String driverName,
        String driverCode,
        String nationality) {
}
