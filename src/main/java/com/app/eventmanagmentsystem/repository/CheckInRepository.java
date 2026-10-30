package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.CheckIn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    Optional<CheckIn> findByPassId(UUID passId);

    boolean existsByPassId(UUID passId);

    Page<CheckIn> findByEventId(UUID eventId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM CheckIn c WHERE c.event.id = :eventId")
    long countByEventId(@Param("eventId") UUID eventId);
}

