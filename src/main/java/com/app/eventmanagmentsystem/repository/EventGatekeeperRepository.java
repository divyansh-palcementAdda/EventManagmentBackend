package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.EventGatekeeper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventGatekeeperRepository extends JpaRepository<EventGatekeeper, UUID> {

    /**
     * Check whether an active gatekeeper assignment exists for the given user + event.
     */
    @Query("SELECT COUNT(eg) > 0 FROM EventGatekeeper eg WHERE eg.event.id = :eventId AND eg.user.id = :userId AND eg.active = true")
    boolean existsActiveAssignment(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

    /**
     * Find the active assignment for a user + event (used for deactivation).
     */
    @Query("SELECT eg FROM EventGatekeeper eg WHERE eg.event.id = :eventId AND eg.user.id = :userId AND eg.active = true")
    Optional<EventGatekeeper> findActiveAssignment(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

    @Query("SELECT eg FROM EventGatekeeper eg JOIN FETCH eg.user WHERE eg.event.id = :eventId AND eg.active = true")
    List<EventGatekeeper> findActiveGatekeepersByEvent(@Param("eventId") UUID eventId);

    @Query("SELECT eg FROM EventGatekeeper eg JOIN FETCH eg.event WHERE eg.user.id = :userId AND eg.active = true")
    List<EventGatekeeper> findActiveEventsByGatekeeper(@Param("userId") UUID userId);

    @Query("SELECT eg FROM EventGatekeeper eg JOIN FETCH eg.user WHERE eg.event.id = :eventId ORDER BY eg.active DESC, eg.assignedAt DESC")
    List<EventGatekeeper> findAllAssignmentsByEvent(@Param("eventId") UUID eventId);

    @Query("SELECT DISTINCT eg.user.id FROM EventGatekeeper eg WHERE eg.event.id = :eventId AND eg.active = true")
    java.util.Set<UUID> findActiveGatekeeperUserIdsByEvent(@Param("eventId") UUID eventId);
}

