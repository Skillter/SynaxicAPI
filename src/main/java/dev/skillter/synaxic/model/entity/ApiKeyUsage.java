package dev.skillter.synaxic.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "api_key_usage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_id", nullable = false)
    private ApiKey apiKey;

    @Builder.Default
    @Column(name = "request_count", nullable = false)
    private Long requestCount = 0L;

    @CreationTimestamp
    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Builder.Default
    @Column(name = "period_type", nullable = false)
    private String periodType = "hourly";

    @Column(name = "last_updated")
    private Instant lastUpdated;
}