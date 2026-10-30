package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventPassRepository extends JpaRepository<EventPass, UUID> {

    @Query("SELECT p FROM EventPass p WHERE p.tokenHash = :tokenHash")
    Optional<EventPass> findByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Pessimistic write lock — used during activation to prevent concurrent state changes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EventPass p WHERE p.tokenHash = :tokenHash")
    Optional<EventPass> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EventPass p WHERE p.id = :id")
    Optional<EventPass> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByPassNumber(String passNumber);

    boolean existsByTokenHash(String tokenHash);

    Optional<EventPass> findByIdempotencyKey(String idempotencyKey);

    Page<EventPass> findByEventId(UUID eventId, Pageable pageable);

    Page<EventPass> findByEventIdAndStatus(UUID eventId, PassStatus status, Pageable pageable);

    @Query("SELECT COUNT(p) FROM EventPass p WHERE p.event.id = :eventId")
    long countByEventId(@Param("eventId") UUID eventId);
}

