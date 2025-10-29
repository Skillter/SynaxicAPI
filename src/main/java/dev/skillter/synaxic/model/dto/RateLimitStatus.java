package dev.skillter.synaxic.model.dto;

import dev.skillter.synaxic.service.RateLimitService;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RateLimitStatus {
    private String key;
    private RateLimitService.RateLimitTier tier;
    private long limit;
    private long remainingTokens;
    private boolean isConsumed;
}
