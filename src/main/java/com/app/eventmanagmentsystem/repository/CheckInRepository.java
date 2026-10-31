package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.CheckIn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    List<CheckIn> findByPassId(UUID passId);

    boolean existsByPassId(UUID passId);

    boolean existsByPassIdAndEventDayId(UUID passId, UUID eventDayId);

    Optional<CheckIn> findByPassIdAndEventDayId(UUID passId, UUID eventDayId);

    Page<CheckIn> findByEventId(UUID eventId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM CheckIn c WHERE c.event.id = :eventId")
    long countByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT COUNT(c) FROM CheckIn c WHERE c.event.id = :eventId AND c.eventDay.id = :eventDayId")
    long countByEventIdAndEventDayId(@Param("eventId") UUID eventId, @Param("eventDayId") UUID eventDayId);

    @Query("SELECT DISTINCT c.pass.id FROM CheckIn c WHERE c.event.id = :eventId")
    Set<UUID> findCheckedInPassIdsByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT c FROM CheckIn c WHERE c.pass.id IN :passIds ORDER BY c.checkedInAt DESC")
    List<CheckIn> findByPassIdIn(@Param("passIds") Collection<UUID> passIds);
}
