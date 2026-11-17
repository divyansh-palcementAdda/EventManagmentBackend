package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventPassRepository extends JpaRepository<EventPass, UUID>, JpaSpecificationExecutor<EventPass> {

    @Query("SELECT p FROM EventPass p WHERE p.tokenHash = :tokenHash")
    Optional<EventPass> findByTokenHash(@Param("tokenHash") String tokenHash);

    Optional<EventPass> findByPassNumber(String passNumber);

    /**
     * Pessimistic write lock — used during activation to prevent concurrent state changes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EventPass p WHERE p.tokenHash = :tokenHash")
    Optional<EventPass> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EventPass p WHERE p.passNumber = :passNumber")
    Optional<EventPass> findByPassNumberForUpdate(@Param("passNumber") String passNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EventPass p WHERE p.id = :id")
    Optional<EventPass> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT p FROM EventPass p LEFT JOIN FETCH p.event LEFT JOIN FETCH p.validDays WHERE p.id = :id")
    Optional<EventPass> findByIdWithEventAndDays(@Param("id") UUID id);

    @Query("SELECT p FROM EventPass p LEFT JOIN FETCH p.event LEFT JOIN FETCH p.attendee LEFT JOIN FETCH p.validDays WHERE p.id = :id")
    Optional<EventPass> findByIdWithEventAndDaysAndAttendee(@Param("id") UUID id);

    boolean existsByPassNumber(String passNumber);

    boolean existsByTokenHash(String tokenHash);

    Optional<EventPass> findByIdempotencyKey(String idempotencyKey);

    Page<EventPass> findByEventId(UUID eventId, Pageable pageable);

    Page<EventPass> findByEventIdAndStatus(UUID eventId, PassStatus status, Pageable pageable);

    @Query("SELECT COUNT(p) FROM EventPass p WHERE p.event.id = :eventId")
    long countByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT COUNT(p) FROM EventPass p WHERE p.event.id = :eventId AND p.status = :status")
    long countByEventIdAndStatus(@Param("eventId") UUID eventId, @Param("status") PassStatus status);

    @Query("SELECT p.category, p.status, COUNT(p) FROM EventPass p WHERE p.event.id = :eventId GROUP BY p.category, p.status")
    List<Object[]> countByEventIdGroupByCategoryAndStatus(@Param("eventId") UUID eventId);

    List<EventPass> findByEventIdAndSequenceNumberBetweenOrderBySequenceNumberAsc(UUID eventId, Long fromSeq, Long toSeq);

    List<EventPass> findByEventIdOrderBySequenceNumberAsc(UUID eventId);

    List<EventPass> findByEventIdAndStatusOrderBySequenceNumberAsc(UUID eventId, PassStatus status);

    List<EventPass> findByEventIdAndIdIn(UUID eventId, java.util.Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EventPass p WHERE p.id IN :ids")
    List<EventPass> findAllByIdForUpdate(@Param("ids") java.util.Collection<UUID> ids);

    Optional<EventPass> findByAttendeeId(UUID attendeeId);

    List<EventPass> findByAttendeeIdIn(java.util.Collection<UUID> attendeeIds);

    @Query("SELECT COUNT(p) FROM EventPass p WHERE p.attendee IS NOT NULL")
    long countWithAttendee();

    @Query("SELECT COUNT(p) FROM EventPass p WHERE p.event.id = :eventId AND p.attendee IS NOT NULL")
    long countWithAttendeeByEventId(@Param("eventId") UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM EventPass p WHERE p.event.id = :eventId AND UPPER(p.category) = UPPER(:categoryCode) AND p.sequenceNumber >= :fromSeq AND p.sequenceNumber <= :toSeq AND p.allotment IS NULL ORDER BY p.sequenceNumber ASC")
    List<EventPass> findUnallottedPassesForUpdate(
            @Param("eventId") UUID eventId,
            @Param("categoryCode") String categoryCode,
            @Param("fromSeq") Long fromSeq,
            @Param("toSeq") Long toSeq
    );

    @Query("SELECT COUNT(p) FROM EventPass p WHERE p.event.id = :eventId AND UPPER(p.category) = UPPER(:categoryCode) AND p.sequenceNumber >= :fromSeq AND p.sequenceNumber <= :toSeq")
    long countByEventIdAndCategoryAndSequenceNumberBetween(
            @Param("eventId") UUID eventId,
            @Param("categoryCode") String categoryCode,
            @Param("fromSeq") Long fromSeq,
            @Param("toSeq") Long toSeq
    );

    @Query("SELECT COUNT(p) FROM EventPass p WHERE p.event.id = :eventId AND UPPER(p.category) = UPPER(:categoryCode) AND p.sequenceNumber >= :fromSeq AND p.sequenceNumber <= :toSeq AND p.allotment IS NOT NULL")
    long countAllottedByEventIdAndCategoryAndSequenceNumberBetween(
            @Param("eventId") UUID eventId,
            @Param("categoryCode") String categoryCode,
            @Param("fromSeq") Long fromSeq,
            @Param("toSeq") Long toSeq
    );

    @Query("SELECT p FROM EventPass p WHERE p.event.id = :eventId AND UPPER(p.category) = UPPER(:categoryCode) ORDER BY p.sequenceNumber ASC")
    List<EventPass> findByEventIdAndCategoryOrderBySequenceNumberAsc(
            @Param("eventId") UUID eventId,
            @Param("categoryCode") String categoryCode
    );

    List<EventPass> findByAllotmentIdOrderBySequenceNumberAsc(UUID allotmentId);

    @Query("SELECT p FROM EventPass p LEFT JOIN FETCH p.attendee WHERE p.allotment.id IN :allotmentIds ORDER BY p.sequenceNumber ASC")
    List<EventPass> findByAllotmentIdInOrderBySequenceNumberAsc(@Param("allotmentIds") java.util.Collection<UUID> allotmentIds);

    @Query("SELECT COUNT(p) FROM EventPass p WHERE p.allotment.id = :allotmentId")
    long countByAllotmentId(@Param("allotmentId") UUID allotmentId);

    @Query("SELECT COUNT(p) FROM EventPass p WHERE p.allotment.id = :allotmentId AND p.status = 'ACTIVATED'")
    long countActivatedByAllotmentId(@Param("allotmentId") UUID allotmentId);
}


