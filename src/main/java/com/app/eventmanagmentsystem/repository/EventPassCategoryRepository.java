package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.EventPassCategory;
import com.app.eventmanagmentsystem.model.enums.ActivationMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventPassCategoryRepository extends JpaRepository<EventPassCategory, UUID>, JpaSpecificationExecutor<EventPassCategory> {

    List<EventPassCategory> findByEventIdOrderByDisplayOrderAsc(UUID eventId);

    List<EventPassCategory> findByEventIdAndEnabledTrueOrderByDisplayOrderAsc(UUID eventId);

    Optional<EventPassCategory> findByEventIdAndCategoryId(UUID eventId, UUID categoryId);

    default Optional<EventPassCategory> findByEventIdAndPassCategoryId(UUID eventId, UUID categoryId) {
        return findByEventIdAndCategoryId(eventId, categoryId);
    }

    @Query("SELECT epc FROM EventPassCategory epc WHERE epc.event.id = :eventId AND LOWER(epc.category.code) = LOWER(:code)")
    Optional<EventPassCategory> findByEventIdAndCategoryCode(@Param("eventId") UUID eventId, @Param("code") String code);

    boolean existsByEventIdAndCategoryId(UUID eventId, UUID categoryId);

    List<EventPassCategory> findByEventIdAndActivationModeAndEnabledTrue(UUID eventId, ActivationMode activationMode);

    @Query("SELECT COUNT(p) FROM EventPass p WHERE p.eventCategory.id = :eventCategoryId")
    long countPassesReferencingEventCategory(@Param("eventCategoryId") UUID eventCategoryId);
}
