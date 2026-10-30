package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.Attendee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendeeRepository extends JpaRepository<Attendee, UUID> {

    Page<Attendee> findByEventId(UUID eventId, Pageable pageable);

    /**
     * Check for an existing attendee with the same email for the same event.
     * Prevents duplicate registrations per event.
     */
    @Query("SELECT COUNT(a) > 0 FROM Attendee a WHERE a.event.id = :eventId AND a.email = :email")
    boolean existsByEventAndEmail(@Param("eventId") UUID eventId, @Param("email") String email);

    Optional<Attendee> findByEventIdAndEmail(UUID eventId, String email);
}

