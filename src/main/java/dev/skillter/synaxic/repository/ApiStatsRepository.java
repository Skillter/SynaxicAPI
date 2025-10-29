package dev.skillter.synaxic.repository;

import dev.skillter.synaxic.model.entity.ApiStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiStatsRepository extends JpaRepository<ApiStats, Long> {
    Optional<ApiStats> findByCounterName(String counterName);
}
