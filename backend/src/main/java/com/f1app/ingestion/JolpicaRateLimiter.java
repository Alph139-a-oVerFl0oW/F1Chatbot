package com.f1app.ingestion;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dual sliding-window limiter: a short burst window and a long sustained window.
 * acquire() blocks the calling thread until both windows have room.
 *
 * Deliberately simple and synchronous — the backfill is a single background
 * thread, and a blocking limiter is far easier to reason about than a reactive
 * one when you are trying to work out why an overnight run stalled.
 */
@Component
public class JolpicaRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(JolpicaRateLimiter.class);

    private final int perSecond;
    private final int perHour;
    private final Deque<Instant> secondWindow = new ArrayDeque<>();
    private final Deque<Instant> hourWindow = new ArrayDeque<>();

    public JolpicaRateLimiter(JolpicaProperties properties) {
        this.perSecond = properties.getRequestsPerSecond();
        this.perHour = properties.getRequestsPerHour();
    }

    /** Blocks until a request slot is free. */
    public synchronized void acquire() throws InterruptedException {
        while (true) {
            Instant now = Instant.now();
            prune(secondWindow, now.minusSeconds(1));
            prune(hourWindow, now.minus(Duration.ofHours(1)));

            if (secondWindow.size() < perSecond && hourWindow.size() < perHour) {
                secondWindow.addLast(now);
                hourWindow.addLast(now);
                return;
            }

            long waitMillis;
            if (hourWindow.size() >= perHour) {
                Instant oldest = hourWindow.peekFirst();
                waitMillis = Duration.between(now, oldest.plus(Duration.ofHours(1))).toMillis() + 100;
                log.info("Hourly rate limit reached, sleeping {}s", waitMillis / 1000);
            } else {
                Instant oldest = secondWindow.peekFirst();
                waitMillis = Duration.between(now, oldest.plusSeconds(1)).toMillis() + 10;
            }

            if (waitMillis > 0) {
                wait(waitMillis);
            }
        }
    }

    /** Called after an HTTP 429 so the limiter treats the window as spent. */
    public synchronized void penalise(int seconds) {
        Instant now = Instant.now();
        for (int i = 0; i < perSecond; i++) {
            secondWindow.addLast(now.plusSeconds(seconds));
        }
    }

    private void prune(Deque<Instant> window, Instant cutoff) {
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
            window.removeFirst();
        }
    }
}
