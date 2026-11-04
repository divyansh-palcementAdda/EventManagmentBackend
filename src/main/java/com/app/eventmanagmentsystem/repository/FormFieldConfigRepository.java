package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.FormFieldConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormFieldConfigRepository extends JpaRepository<FormFieldConfig, UUID> {

    List<FormFieldConfig> findByEventPassCategoryIdOrderByDisplayOrderAsc(UUID eventPassCategoryId);

    List<FormFieldConfig> findByEventPassCategoryIdAndVisibleTrueOrderByDisplayOrderAsc(UUID eventPassCategoryId);

    Optional<FormFieldConfig> findByEventPassCategoryIdAndFieldKey(UUID eventPassCategoryId, String fieldKey);

    void deleteByEventPassCategoryId(UUID eventPassCategoryId);
}
