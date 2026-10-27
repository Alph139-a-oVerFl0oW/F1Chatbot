package com.f1app.ingestion;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Null-tolerant accessors for Ergast JSON. Upstream omits fields freely
 * (no lap times before 1996, no driver codes before 2007, no session schedule
 * before 2021), so every getter here returns null rather than throwing.
 */
final class JsonUtils {

    private JsonUtils() {
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String s = value.asText().trim();
        return s.isEmpty() ? null : s;
    }

    static Integer integer(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Long longValue(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Double decimal(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return null;
        }
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static double decimalOrZero(JsonNode node, String field) {
        Double d = decimal(node, field);
        return d == null ? 0d : d;
    }

    static int integerOrZero(JsonNode node, String field) {
        Integer i = integer(node, field);
        return i == null ? 0 : i;
    }

    /** Ergast dates are ISO: "2024-09-01". */
    static LocalDate date(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Ergast times carry a trailing Z: "13:00:00Z". Stored as UTC wall time. */
    static LocalTime time(JsonNode node, String field) {
        String s = text(node, field);
        if (s == null) {
            return null;
        }
        if (s.endsWith("Z")) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            return LocalTime.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
