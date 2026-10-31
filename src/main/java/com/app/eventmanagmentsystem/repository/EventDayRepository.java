package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.EventDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventDayRepository extends JpaRepository<EventDay, UUID> {

    List<EventDay> findByEventIdOrderByDayNumberAsc(UUID eventId);

    Optional<EventDay> findByEventIdAndDayNumber(UUID eventId, int dayNumber);

    Optional<EventDay> findByEventIdAndEventDate(UUID eventId, LocalDate eventDate);

    boolean existsByEventId(UUID eventId);
}

