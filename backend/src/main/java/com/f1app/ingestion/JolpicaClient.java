package com.f1app.ingestion;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over the Jolpica-F1 (Ergast-compatible) REST API.
 *
 * Responses are handled as JsonNode rather than mapped to DTOs: the Ergast
 * envelope nests differently for every endpoint, and hand-written DTOs for all
 * of them would be several hundred lines that break whenever upstream adds a
 * field. Navigation helpers live in JsonUtils.
 */
@Component
public class JolpicaClient {

    private static final Logger log = LoggerFactory.getLogger(JolpicaClient.class);

    private final RestClient restClient;
    private final JolpicaRateLimiter rateLimiter;
    private final JolpicaProperties properties;

    public JolpicaClient(JolpicaProperties properties, JolpicaRateLimiter rateLimiter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("User-Agent", properties.getUserAgent())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * Fetches a single page. Path is relative to the base URL and must not
     * contain query parameters, e.g. "/2024/results.json".
     */
    public JsonNode fetch(String path, int limit, int offset) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                rateLimiter.acquire();
                JsonNode body = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(path)
                                .queryParam("limit", limit)
                                .queryParam("offset", offset)
                                .build())
                        .retrieve()
                        .body(JsonNode.class);

                if (body == null || !body.has("MRData")) {
                    throw new IngestionException("Unexpected response shape for " + path);
                }
                return body.get("MRData");

            } catch (HttpClientErrorException.TooManyRequests e) {
                int backoff = properties.getThrottleBackoffSeconds();
                log.warn("HTTP 429 on {} (attempt {}), backing off {}s", path, attempt, backoff);
                rateLimiter.penalise(backoff);
                sleep(backoff * 1000L);
                if (attempt > properties.getMaxRetries()) {
                    throw new IngestionException("Rate limited repeatedly on " + path, e);
                }

            } catch (HttpClientErrorException.NotFound e) {
                // Season with no data for this resource (e.g. sprint before 2021)
                log.debug("404 for {} - treating as empty", path);
                return null;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IngestionException("Interrupted while rate limiting", e);

            } catch (IngestionException e) {
                throw e;

            } catch (RuntimeException e) {
                if (attempt > properties.getMaxRetries()) {
                    throw new IngestionException("Failed fetching " + path, e);
                }
                log.warn("Error on {} (attempt {}): {}", path, attempt, e.getMessage());
                sleep(2000L * attempt);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestionException("Interrupted while backing off", e);
        }
    }

    /**
     * Walks every page of a resource and returns the concatenated arrays found
     * at the given path inside the Ergast table wrapper.
     *
     * @param path      e.g. "/2024/results.json"
     * @param tableName e.g. "RaceTable"
     * @param arrayName e.g. "Races"
     */
    public List<JsonNode> fetchAll(String path, String tableName, String arrayName) {
        List<JsonNode> collected = new ArrayList<>();
        int offset = 0;
        int limit = properties.getPageSize();
        int total = Integer.MAX_VALUE;

        while (offset < total) {
            JsonNode mrData = fetch(path, limit, offset);
            if (mrData == null) {
                break;
            }

            total = mrData.path("total").asInt(0);
            JsonNode array = mrData.path(tableName).path(arrayName);
            if (!array.isArray() || array.isEmpty()) {
                break;
            }
            array.forEach(collected::add);

            // The number of returned elements is not always the number of rows
            // counted by "total" (results are grouped under races), so advance
            // by the requested page size rather than by array length.
            offset += limit;
        }

        log.debug("Fetched {} elements from {}", collected.size(), path);
        return collected;
    }
}
