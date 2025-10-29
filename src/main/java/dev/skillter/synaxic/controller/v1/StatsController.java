package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.StatsResponse;
import dev.skillter.synaxic.repository.UserRepository;
import dev.skillter.synaxic.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Stats", description = "Public statistics endpoints")
public class StatsController {

    private final MetricsService metricsService;
    private final UserRepository userRepository;

    @GetMapping("/stats")
    @Operation(
            summary = "Get public statistics",
            description = "Returns total API requests (persistent) and registered users count"
    )
    public ResponseEntity<StatsResponse> getStats() {
        // Get total requests from Redis (persists across restarts)
        long totalRequests = metricsService.getTotalApiRequests();

        // Get total users from database
        long totalUsers = userRepository.count();

        StatsResponse stats = StatsResponse.builder()
                .totalRequests(totalRequests)
                .totalUsers(totalUsers)
                .build();

        return ResponseEntity.ok(stats);
    }
}
