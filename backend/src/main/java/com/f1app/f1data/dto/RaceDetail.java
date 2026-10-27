package com.f1app.f1data.dto;

import java.util.List;

public record RaceDetail(
        RaceSummary race,
        List<ResultRow> results,
        List<QualifyingRow> qualifying) {
}
