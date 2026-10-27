package com.f1app.f1data.dto;

import java.math.BigDecimal;

public record ResultRow(
        Integer position,
        String positionText,
        String driverRef,
        String driverName,
        String driverCode,
        String constructorRef,
        String constructorName,
        Integer grid,
        Integer laps,
        BigDecimal points,
        String time,
        String status,
        String fastestLapTime) {
}
