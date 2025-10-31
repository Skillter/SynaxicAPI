package dev.skillter.synaxic.service;

import dev.skillter.synaxic.model.dto.AccountUsageDto;
import dev.skillter.synaxic.model.entity.ApiKey;
import dev.skillter.synaxic.model.entity.ApiKeyUsage;
import dev.skillter.synaxic.model.entity.User;
import dev.skillter.synaxic.repository.ApiKeyRepository;
import dev.skillter.synaxic.repository.ApiKeyUsageRepository;
import dev.skillter.synaxic.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountUsageService {

    private final ApiKeyUsageRepository apiKeyUsageRepository;
    private final UserRepository userRepository;
    private final ApiKeyRepository apiKeyRepository;

    // Color palette for key visualization
    private static final List<String> KEY_COLORS = Arrays.asList(
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A", "#98D8C8",
        "#6C5CE7", "#00B894", "#FDCB6E", "#E17055", "#74B9FF"
    );

    @Transactional
    public AccountUsageDto getAccountUsage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Instant currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS);

        // Get total requests for this hour
        Long totalRequests = apiKeyUsageRepository.getTotalRequestsForUserInCurrentHour(userId, currentHour);

        // Calculate usage percentage
        Double usagePercentage = (totalRequests.doubleValue() / user.getAccountRateLimit()) * 100;

        // Get key usage breakdown
        List<AccountUsageDto.KeyUsageBreakdown> keyBreakdown = getKeyUsageBreakdown(userId, currentHour);

        return AccountUsageDto.builder()
                .accountId(userId)
                .accountRateLimit(user.getAccountRateLimit())
                .accountRequestsUsed(totalRequests)
                .remainingRequests(Math.max(0, user.getAccountRateLimit() - totalRequests))
                .usagePercentage(Math.min(100.0, usagePercentage))
                .rateLimitResetTime(user.getRateLimitResetTime())
                .keyUsageBreakdown(keyBreakdown)
                .build();
    }

    @Transactional
    public void recordApiKeyUsage(Long apiKeyId, String keyPrefix) {
        Instant currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS);

        ApiKeyUsage usage = apiKeyUsageRepository.findCurrentHourUsage(apiKeyId, currentHour)
                .orElse(ApiKeyUsage.builder()
                        .apiKey(apiKeyRepository.findById(apiKeyId).orElse(null))
                        .periodStart(currentHour)
                        .periodType("hourly")
                        .requestCount(0L)
                        .build());

        usage.setRequestCount(usage.getRequestCount() + 1);
        usage.setLastUpdated(Instant.now());

        apiKeyUsageRepository.save(usage);

        log.debug("Recorded usage for API key {}: {} requests in current hour", keyPrefix, usage.getRequestCount());
    }

    private List<AccountUsageDto.KeyUsageBreakdown> getKeyUsageBreakdown(Long userId, Instant currentHour) {
        // Get all API keys for the user
        List<ApiKey> userKeys = apiKeyRepository.findAllByUser_Id(userId);
        List<AccountUsageDto.KeyUsageBreakdown> breakdown = new ArrayList<>();

        // Get total requests for percentage calculation
        Long totalRequests = apiKeyUsageRepository.getTotalRequestsForUserInCurrentHour(userId, currentHour);

        for (int i = 0; i < userKeys.size(); i++) {
            ApiKey key = userKeys.get(i);
            Optional<ApiKeyUsage> usage = apiKeyUsageRepository.findCurrentHourUsage(key.getId(), currentHour);
            Long requestCount = usage.map(ApiKeyUsage::getRequestCount).orElse(0L);

            Double percentage = totalRequests > 0 ? (requestCount.doubleValue() / totalRequests) * 100 : 0.0;

            AccountUsageDto.KeyUsageBreakdown keyBreakdown = AccountUsageDto.KeyUsageBreakdown.builder()
                    .keyId(key.getId())
                    .keyPrefix(key.getPrefix())
                    .keyName(key.getKeyName())
                    .requestCount(requestCount)
                    .percentageOfTotal(percentage)
                    .color(KEY_COLORS.get(i % KEY_COLORS.size()))
                    .lastUsed(key.getLastUsedAt())
                    .build();

            breakdown.add(keyBreakdown);
        }

        return breakdown;
    }

    @Transactional
    public void resetAccountUsage(Long userId) {
        Instant nextReset = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setAccountRequestsUsed(0L);
        user.setRateLimitResetTime(nextReset);

        userRepository.save(user);

        log.info("Reset usage for user {}. Next reset at {}", userId, nextReset);
    }

    public boolean isAccountRateLimitExceeded(Long userId) {
        AccountUsageDto usage = getAccountUsage(userId);
        return usage.getAccountRequestsUsed() >= usage.getAccountRateLimit();
    }
}