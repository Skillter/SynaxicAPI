package dev.skillter.synaxic.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountUsageDto {
    private Long accountId;
    private Long accountRateLimit;
    private Long accountRequestsUsed;
    private Long remainingRequests;
    private Instant rateLimitResetTime;
    private java.util.List<KeyUsageBreakdown> keyUsageBreakdown;
    private Double usagePercentage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KeyUsageBreakdown {
        private Long keyId;
        private String keyPrefix;
        private String keyName;
        private Long requestCount;
        private Double percentageOfTotal;
        private String color; // For visualization
        private Instant lastUsed;
    }
}