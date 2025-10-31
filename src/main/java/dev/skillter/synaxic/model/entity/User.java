package dev.skillter.synaxic.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "api_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_sub", unique = true, nullable = false)
    private String googleSub;

    @Column(nullable = false)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder.Default
    @Column(name = "account_rate_limit", nullable = false)
    private Long accountRateLimit = 10000L;

    @Builder.Default
    @Column(name = "account_requests_used", nullable = false)
    private Long accountRequestsUsed = 0L;

    @Column(name = "rate_limit_reset_time")
    private Instant rateLimitResetTime;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ApiKey> apiKeys;
}