package dev.skillter.synaxic.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyRequestTrackerService {

    private final RedissonClient redissonClient;

    private static final String DAILY_REQUESTS_KEY_PREFIX = "synaxic:daily_requests:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Increments the global daily request counter for the current date in UTC.
     * This counter tracks all API requests regardless of user and resets at midnight UTC.
     */
    public long incrementDailyRequests() {
        String todayKey = getTodayKey();
        RAtomicLong counter = redissonClient.getAtomicLong(todayKey);
        long newValue = counter.incrementAndGet();

        // Set expiration to 7 days to automatically clean up old counters
        counter.expireAsync(java.time.Duration.ofDays(7));

        log.debug("Incremented daily requests counter for key: {} to {}", todayKey, newValue);
        return newValue;
    }

    /**
     * Gets the total number of requests for today (UTC date).
     *
     * @return Number of requests made today, or 0 if no counter exists
     */
    public long getTodayRequests() {
        String todayKey = getTodayKey();
        RAtomicLong counter = redissonClient.getAtomicLong(todayKey);
        return counter.get();
    }

    /**
     * Gets the number of requests for a specific date.
     * This method is used for timezone-aware calculations on the frontend.
     *
     * @param date The date to get requests for (in yyyy-MM-dd format)
     * @return Number of requests for that date, or 0 if no counter exists
     */
    public long getRequestsForDate(String date) {
        String dateKey = DAILY_REQUESTS_KEY_PREFIX + date;
        RAtomicLong counter = redissonClient.getAtomicLong(dateKey);
        return counter.get();
    }

    /**
     * Gets the current UTC date as a string key for Redis.
     *
     * @return Today's date in yyyy-MM-dd format
     */
    private String getTodayKey() {
        LocalDate todayUTC = LocalDate.now(ZoneId.of("UTC"));
        return DAILY_REQUESTS_KEY_PREFIX + todayUTC.format(DATE_FORMATTER);
    }

    /**
     * Gets the current UTC date string for frontend comparisons.
     * This helps the frontend determine if it needs to reset its counter based on user's timezone.
     *
     * @return Current UTC date in yyyy-MM-dd format
     */
    public String getCurrentUTCDate() {
        return LocalDate.now(ZoneId.of("UTC")).format(DATE_FORMATTER);
    }
}