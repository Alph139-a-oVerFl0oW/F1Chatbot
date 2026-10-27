package com.f1app.f1data.dto;

import java.math.BigDecimal;

public record ConstructorStandingRow(
        Integer position,
        String positionText,
        BigDecimal points,
        Integer wins,
        String constructorRef,
        String constructorName,
        String nationality) {
}
