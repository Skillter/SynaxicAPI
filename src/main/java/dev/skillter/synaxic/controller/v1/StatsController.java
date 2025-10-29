package dev.skillter.synaxic.controller.v1;

import dev.skillter.synaxic.model.dto.StatsResponse;
import dev.skillter.synaxic.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
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

    private final MeterRegistry meterRegistry;
    private final UserRepository userRepository;

    @GetMapping("/stats")
    @Operation(
            summary = "Get public statistics",
            description = "Returns total API requests and registered users count"
    )
    public ResponseEntity<StatsResponse> getStats() {
        // Get total requests from Micrometer metrics
        double totalRequests = Search.in(meterRegistry)
                .name("synaxic.api.requests.total")
                .counters()
                .stream()
                .mapToDouble(counter -> counter.count())
                .sum();

        // Get total users from database
        long totalUsers = userRepository.count();

        StatsResponse stats = StatsResponse.builder()
                .totalRequests((long) totalRequests)
                .totalUsers(totalUsers)
                .build();

        return ResponseEntity.ok(stats);
    }
}
