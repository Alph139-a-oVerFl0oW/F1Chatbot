package com.f1app.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the Jolpica-F1 client. Defaults sit just under the published
 * limits (4 req/sec burst, 500 req/hour sustained).
 */
@ConfigurationProperties(prefix = "app.jolpica")
public class JolpicaProperties {

    private String baseUrl = "https://api.jolpi.ca/ergast/f1";
    private String userAgent = "f1-chatbot/0.1";

    /** Burst ceiling. Upstream allows 4; we leave headroom. */
    private int requestsPerSecond = 3;

    /** Sustained ceiling. Upstream allows 500/hour. */
    private int requestsPerHour = 450;

    /** Results per page. Upstream max is 1000. */
    private int pageSize = 100;

    /** Earliest season to backfill. F1 records start in 1950. */
    private int firstSeason = 1950;

    /** Seconds to wait after an HTTP 429 before retrying. */
    private int throttleBackoffSeconds = 60;

    private int maxRetries = 3;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public int getRequestsPerSecond() { return requestsPerSecond; }
    public void setRequestsPerSecond(int requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }

    public int getRequestsPerHour() { return requestsPerHour; }
    public void setRequestsPerHour(int requestsPerHour) { this.requestsPerHour = requestsPerHour; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public int getFirstSeason() { return firstSeason; }
    public void setFirstSeason(int firstSeason) { this.firstSeason = firstSeason; }

    public int getThrottleBackoffSeconds() { return throttleBackoffSeconds; }
    public void setThrottleBackoffSeconds(int throttleBackoffSeconds) { this.throttleBackoffSeconds = throttleBackoffSeconds; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
}
