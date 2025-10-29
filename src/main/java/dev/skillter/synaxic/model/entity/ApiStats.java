package dev.skillter.synaxic.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "api_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "counter_name", unique = true, nullable = false)
    private String counterName;

    @Column(name = "counter_value", nullable = false)
    private Long counterValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PreUpdate
    @PrePersist
    public void updateTimestamp() {
        this.updatedAt = Instant.now();
    }
}
