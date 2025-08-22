package dev.skillter.synaxic.repository;

import dev.skillter.synaxic.model.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByPrefix(String prefix);
    Optional<ApiKey> findByUser_Id(Long userId);
}