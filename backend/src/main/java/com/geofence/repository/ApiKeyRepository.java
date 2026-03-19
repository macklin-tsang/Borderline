package com.geofence.repository;

import com.geofence.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    // O(1) B-tree lookup by prefix — first step of the two-step validation
    List<ApiKey> findByKeyPrefixAndActiveTrue(String keyPrefix);

    List<ApiKey> findByUserIdAndActiveTrue(UUID userId);

    Optional<ApiKey> findByIdAndUserId(UUID id, UUID userId);
}
