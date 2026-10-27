package com.f1app.f1data.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record RaceSummary(
        Integer id,
        Integer season,
        Integer round,
        String name,
        LocalDate date,
        LocalTime time,
        String circuitRef,
        String circuitName,
        String locality,
        String country) {
}
