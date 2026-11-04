package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.RegistrationFormVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistrationFormVersionRepository extends JpaRepository<RegistrationFormVersion, UUID> {

    List<RegistrationFormVersion> findByEventPassCategoryIdOrderByVersionNumberDesc(UUID eventPassCategoryId);

    Optional<RegistrationFormVersion> findTopByEventPassCategoryIdAndPublishedTrueOrderByVersionNumberDesc(UUID eventPassCategoryId);

    Optional<RegistrationFormVersion> findByEventPassCategoryIdAndVersionNumber(UUID eventPassCategoryId, int versionNumber);

    @Query("SELECT COALESCE(MAX(rfv.versionNumber), 0) FROM RegistrationFormVersion rfv WHERE rfv.eventPassCategory.id = :epcId")
    int findMaxVersionNumberByEventPassCategoryId(@Param("epcId") UUID epcId);
}
