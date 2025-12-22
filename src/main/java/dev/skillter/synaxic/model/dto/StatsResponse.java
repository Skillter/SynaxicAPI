package dev.skillter.synaxic.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {
    private long totalRequests;
    private long totalUsers;
    private long requestsToday;
    private String currentUTCDate;
}
