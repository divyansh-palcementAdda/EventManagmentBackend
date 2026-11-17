package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.PassAllotment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PassAllotmentRepository extends JpaRepository<PassAllotment, UUID>, JpaSpecificationExecutor<PassAllotment> {

    List<PassAllotment> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    List<PassAllotment> findByEventIdAndAllottedToIgnoreCase(UUID eventId, String allottedTo);

    List<PassAllotment> findByEventIdAndCategoryCode(UUID eventId, String categoryCode);

    @Query("SELECT DISTINCT p.allottedTo FROM PassAllotment p WHERE p.event.id = :eventId ORDER BY p.allottedTo ASC")
    List<String> findDistinctAllottedToByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT p FROM PassAllotment p WHERE p.event.id = :eventId AND p.categoryCode = :categoryCode AND p.fromSequence = :fromSeq AND p.toSequence = :toSeq")
    List<PassAllotment> findByEventAndCategoryAndSequence(
            @Param("eventId") UUID eventId,
            @Param("categoryCode") String categoryCode,
            @Param("fromSeq") Long fromSeq,
            @Param("toSeq") Long toSeq
    );
}
