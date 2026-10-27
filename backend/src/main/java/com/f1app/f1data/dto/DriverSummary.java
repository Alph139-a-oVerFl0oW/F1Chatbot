package com.f1app.f1data.dto;

import java.time.LocalDate;

public record DriverSummary(
        String driverRef,
        String code,
        Integer permanentNumber,
        String name,
        String nationality,
        LocalDate dateOfBirth) {
}
