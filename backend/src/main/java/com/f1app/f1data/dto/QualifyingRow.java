package com.f1app.f1data.dto;

public record QualifyingRow(
        Integer position,
        String driverRef,
        String driverName,
        String driverCode,
        String constructorName,
        String q1,
        String q2,
        String q3) {
}
