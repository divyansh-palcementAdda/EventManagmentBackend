package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.Event;
import com.app.eventmanagmentsystem.model.enums.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findByEventCode(String eventCode);

    boolean existsByEventCode(String eventCode);

    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    /**
     * Atomically increments pass_sequence_counter and returns the new value.
     * Called inside a locked transaction to generate unique sequential pass numbers.
     */
    @Modifying
    @Query("UPDATE Event e SET e.passSequenceCounter = e.passSequenceCounter + 1 WHERE e.id = :id")
    void incrementPassSequenceCounter(@Param("id") UUID id);

    @Query("SELECT e.passSequenceCounter FROM Event e WHERE e.id = :id")
    long getPassSequenceCounter(@Param("id") UUID id);
}

