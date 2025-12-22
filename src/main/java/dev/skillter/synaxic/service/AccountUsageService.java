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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final RateLimitService rateLimitService;

    private static final List<String> KEY_COLORS = Arrays.asList(
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A", "#98D8C8",
        "#6C5CE7", "#00B894", "#FDCB6E", "#E17055", "#74B9FF"
    );

    @Transactional(readOnly = true)
    public AccountUsageDto getAccountUsage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Instant currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS);

        String accountKey = "account:" + userId;
        var rateLimitStatus = rateLimitService.getStatus(accountKey, RateLimitService.RateLimitTier.ACCOUNT);

        long accountLimit = rateLimitStatus.getLimit();
        long availableTokens = rateLimitStatus.getRemainingTokens();
        long usedRequests = Math.max(0, accountLimit - availableTokens);

        Double usagePercentage = accountLimit > 0 ? ((double) usedRequests / accountLimit) * 100 : 0.0;

        List<AccountUsageDto.KeyUsageBreakdown> keyBreakdown = getKeyUsageBreakdown(userId, currentHour);

        return AccountUsageDto.builder()
                .accountId(userId)
                .accountRateLimit(accountLimit)
                .accountRequestsUsed(usedRequests)
                .remainingRequests(availableTokens)
                .usagePercentage(Math.min(100.0, usagePercentage))
                .rateLimitResetTime(user.getRateLimitResetTime())
                .keyUsageBreakdown(keyBreakdown)
                .build();
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordApiKeyUsage(Long apiKeyId, String keyPrefix) {
        try {
            Instant currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS);

            ApiKeyUsage usage = apiKeyUsageRepository.findCurrentHourUsage(apiKeyId, currentHour)
                    .orElseGet(() -> {
                        Optional<ApiKey> keyOpt = apiKeyRepository.findById(apiKeyId);
                        if (keyOpt.isEmpty()) return null;
                        
                        return ApiKeyUsage.builder()
                                .apiKey(keyOpt.get())
                                .periodStart(currentHour)
                                .periodType("hourly")
                                .requestCount(0L)
                                .build();
                    });

            if (usage != null) {
                usage.setRequestCount(usage.getRequestCount() + 1);
                usage.setLastUpdated(Instant.now());
                apiKeyUsageRepository.save(usage);

                apiKeyRepository.findById(apiKeyId).ifPresent(key -> {
                    key.setLastUsedAt(Instant.now());
                    apiKeyRepository.save(key);
                });
            }
        } catch (Exception e) {
            log.error("Failed to record usage for API key {}: {}", keyPrefix, e.getMessage());
        }
    }

    private List<AccountUsageDto.KeyUsageBreakdown> getKeyUsageBreakdown(Long userId, Instant currentHour) {
        List<ApiKey> userKeys = apiKeyRepository.findAllByUser_Id(userId);
        List<AccountUsageDto.KeyUsageBreakdown> breakdown = new ArrayList<>();

        Long totalRequests = apiKeyUsageRepository.getTotalRequestsForUserInCurrentHour(userId, currentHour);

        for (int i = 0; i < userKeys.size(); i++) {
            ApiKey key = userKeys.get(i);
            Optional<ApiKeyUsage> usage = apiKeyUsageRepository.findCurrentHourUsage(key.getId(), currentHour);
            Long requestCount = usage.map(ApiKeyUsage::getRequestCount).orElse(0L);

            Double percentage = (totalRequests != null && totalRequests > 0) 
                    ? (requestCount.doubleValue() / totalRequests) * 100 
                    : 0.0;

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
        userRepository.findById(userId).ifPresent(user -> {
            user.setAccountRequestsUsed(0L);
            user.setRateLimitResetTime(nextReset);
            userRepository.save(user);
        });
    }

    @Transactional(readOnly = true)
    public Long getTotalRequestsForUser(Long userId) {
        return apiKeyUsageRepository.getTotalRequestsForUser(userId);
    }

    @Transactional(readOnly = true)
    public Long getTodayRequestsForUser(Long userId) {
        Instant todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        return apiKeyUsageRepository.getTodayRequestsForUser(userId, todayStart);
    }
}

