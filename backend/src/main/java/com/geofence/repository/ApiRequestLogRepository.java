package com.geofence.repository;

import com.geofence.model.ApiRequestLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, Long> {

    // Recent logs for a user's API keys — for the security dashboard
    @Query("""
            SELECT l FROM ApiRequestLog l
            WHERE l.apiKeyId IN (
                SELECT k.id FROM ApiKey k WHERE k.userId = :userId
            )
            ORDER BY l.createdAt DESC
            """)
    List<ApiRequestLog> findRecentByUserId(@Param("userId") UUID userId, Pageable pageable);

    // Scheduled cleanup — delete logs older than 30 days
    @Modifying
    @Query("DELETE FROM ApiRequestLog l WHERE l.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
