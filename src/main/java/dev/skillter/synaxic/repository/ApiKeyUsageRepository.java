package dev.skillter.synaxic.repository;

import dev.skillter.synaxic.model.entity.ApiKeyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyUsageRepository extends JpaRepository<ApiKeyUsage, Long> {

    @Query("SELECT a FROM ApiKeyUsage a WHERE a.apiKey.id = :keyId AND a.periodType = 'hourly' " +
           "AND a.periodStart >= :hourStart ORDER BY a.periodStart DESC")
    Optional<ApiKeyUsage> findCurrentHourUsage(@Param("keyId") Long keyId, @Param("hourStart") Instant hourStart);

    @Query("SELECT a FROM ApiKeyUsage a WHERE a.apiKey.user.id = :userId AND a.periodType = 'hourly' " +
           "AND a.periodStart >= :hourStart")
    List<ApiKeyUsage> findAllByUserForCurrentHour(@Param("userId") Long userId, @Param("hourStart") Instant hourStart);

    @Query("SELECT COALESCE(SUM(a.requestCount), 0) FROM ApiKeyUsage a WHERE a.apiKey.user.id = :userId " +
           "AND a.periodType = 'hourly' AND a.periodStart >= :hourStart")
    Long getTotalRequestsForUserInCurrentHour(@Param("userId") Long userId, @Param("hourStart") Instant hourStart);

    // Complex query removed - will be handled in service layer for better maintainability

    @Query("SELECT COUNT(DISTINCT a.apiKey.id) FROM ApiKeyUsage a WHERE a.apiKey.user.id = :userId " +
           "AND a.periodType = 'hourly' AND a.periodStart >= :hourStart AND a.requestCount > 0")
    Integer getActiveKeysCountForUser(@Param("userId") Long userId, @Param("hourStart") Instant hourStart);

    @Query("SELECT COALESCE(SUM(a.requestCount), 0) FROM ApiKeyUsage a WHERE a.apiKey.user.id = :userId " +
           "AND a.periodType = 'hourly'")
    Long getTotalRequestsForUser(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(a.requestCount), 0) FROM ApiKeyUsage a WHERE a.apiKey.user.id = :userId " +
           "AND a.periodType = 'hourly' AND a.periodStart >= :todayStart")
    Long getTodayRequestsForUser(@Param("userId") Long userId, @Param("todayStart") Instant todayStart);

    @Query("SELECT COALESCE(SUM(a.requestCount), 0) FROM ApiKeyUsage a WHERE a.apiKey.id = :keyId " +
           "AND a.periodType = 'hourly' AND a.periodStart >= :todayStart")
    Long getTodayRequestsForApiKey(@Param("keyId") Long keyId, @Param("todayStart") Instant todayStart);

    @Query("SELECT COALESCE(SUM(a.requestCount), 0) FROM ApiKeyUsage a WHERE a.apiKey.id = :keyId " +
           "AND a.periodType = 'hourly'")
    Long getTotalRequestsForApiKey(@Param("keyId") Long keyId);
}