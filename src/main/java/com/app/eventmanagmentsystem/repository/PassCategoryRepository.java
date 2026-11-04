package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.PassCategory;
import com.app.eventmanagmentsystem.model.enums.CategoryScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PassCategoryRepository extends JpaRepository<PassCategory, UUID>, JpaSpecificationExecutor<PassCategory> {

    Optional<PassCategory> findByCode(String code);

    default Optional<PassCategory> findByCodeIgnoreCase(String code) {
        return findByCode(code != null ? code.toUpperCase() : null);
    }

    Optional<PassCategory> findByScopeAndCode(CategoryScope scope, String code);

    Optional<PassCategory> findByScopeAndCodeAndEventId(CategoryScope scope, String code, UUID eventId);

    boolean existsByScopeAndCode(CategoryScope scope, String code);

    boolean existsByScopeAndCodeAndEventId(CategoryScope scope, String code, UUID eventId);

    List<PassCategory> findByScopeAndActiveTrueOrderByDisplayOrderAsc(CategoryScope scope);

    List<PassCategory> findByEventIdOrderByDisplayOrderAsc(UUID eventId);

    @Query("SELECT c FROM PassCategory c WHERE c.active = true AND (c.scope = 'GLOBAL' OR c.event.id = :eventId) ORDER BY c.displayOrder ASC")
    List<PassCategory> findAvailableForEvent(@Param("eventId") UUID eventId);

    @Query("SELECT COUNT(p) FROM EventPass p WHERE p.passCategory.id = :categoryId")
    long countPassesReferencingCategory(@Param("categoryId") UUID categoryId);
}
