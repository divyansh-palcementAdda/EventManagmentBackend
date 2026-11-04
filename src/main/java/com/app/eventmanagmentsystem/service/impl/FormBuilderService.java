package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.FieldType;
import com.app.eventmanagmentsystem.payload.category.FormFieldDto;
import com.app.eventmanagmentsystem.payload.category.FormSchemaResponse;
import com.app.eventmanagmentsystem.payload.category.PublishFormVersionResponse;
import com.app.eventmanagmentsystem.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormBuilderService {

    private final FormFieldConfigRepository formFieldConfigRepository;
    private final RegistrationFormVersionRepository registrationFormVersionRepository;
    private final EventPassCategoryRepository eventPassCategoryRepository;
    private final AttendeeRegistrationAnswerRepository attendeeRegistrationAnswerRepository;
    private final ObjectMapper objectMapper;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+\\-()\\s]{7,20}$");

    @PreAuthorize("hasAuthority('REGISTRATION_FORM_VIEW') or hasAuthority('EVENT_VIEW') or hasAuthority('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public List<FormFieldDto> getDraftFields(UUID eventPassCategoryId) {
        EventPassCategory epc = eventPassCategoryRepository.findById(eventPassCategoryId)
                .orElseThrow(() -> new ResourceNotFoundException("EventPassCategory", eventPassCategoryId.toString()));

        List<FormFieldConfig> configs = formFieldConfigRepository.findByEventPassCategoryIdOrderByDisplayOrderAsc(eventPassCategoryId);
        if (configs.isEmpty()) {
            return getDefaultFieldsForCategory(epc.getPassCategory() != null ? epc.getPassCategory().getCode() : "OTHER");
        }

        return configs.stream().map(this::toDto).collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('REGISTRATION_FORM_UPDATE') or hasAuthority('EVENT_UPDATE') or hasAuthority('SUPER_ADMIN')")
    @Transactional
    public List<FormFieldDto> saveDraftFields(UUID eventPassCategoryId, List<FormFieldDto> fieldDtos) {
        EventPassCategory epc = eventPassCategoryRepository.findById(eventPassCategoryId)
                .orElseThrow(() -> new ResourceNotFoundException("EventPassCategory", eventPassCategoryId.toString()));

        if (fieldDtos == null || fieldDtos.isEmpty()) {
            throw new InvalidRequestException("At least one form field must be defined.");
        }

        // Validate uniqueness of field keys
        Set<String> keys = new HashSet<>();
        for (FormFieldDto dto : fieldDtos) {
            String k = dto.getFieldKey() != null ? dto.getFieldKey().trim().toLowerCase() : "";
            if (k.isEmpty()) {
                throw new InvalidRequestException("Field key cannot be empty.");
            }
            if (!keys.add(k)) {
                throw new InvalidRequestException("Duplicate field key: " + dto.getFieldKey());
            }
        }

        // Clear existing draft configs and replace with new
        formFieldConfigRepository.deleteByEventPassCategoryId(eventPassCategoryId);

        List<FormFieldConfig> savedConfigs = new ArrayList<>();
        int order = 1;
        for (FormFieldDto dto : fieldDtos) {
            FormFieldConfig config = new FormFieldConfig();
            config.setEventPassCategory(epc);
            config.setFieldKey(dto.getFieldKey().trim());
            config.setLabel(dto.getLabel().trim());
            config.setDescription(dto.getDescription());
            config.setPlaceholder(dto.getPlaceholder());
            config.setFieldType(dto.getFieldType() != null ? dto.getFieldType() : FieldType.TEXT);
            config.setRequired(dto.isRequired());
            config.setVisible(dto.isVisible());
            config.setDisplayOrder(dto.getDisplayOrder() > 0 ? dto.getDisplayOrder() : order++);
            config.setDefaultValue(dto.getDefaultValue());
            config.setValidationRules(dto.getValidationRules());
            config.setOptions(dto.getOptions());
            config.setActive(dto.isActive());
            savedConfigs.add(formFieldConfigRepository.save(config));
        }

        log.info("Saved {} draft form fields for EventPassCategory {}", savedConfigs.size(), eventPassCategoryId);
        return savedConfigs.stream().map(this::toDto).collect(Collectors.toList());
    }

    @PreAuthorize("hasAuthority('REGISTRATION_FORM_UPDATE') or hasAuthority('EVENT_UPDATE') or hasAuthority('SUPER_ADMIN')")
    @Transactional
    public void deleteField(UUID fieldConfigId) {
        if (!formFieldConfigRepository.existsById(fieldConfigId)) {
            throw new ResourceNotFoundException("FormFieldConfig", fieldConfigId.toString());
        }
        formFieldConfigRepository.deleteById(fieldConfigId);
    }

    @PreAuthorize("hasAuthority('REGISTRATION_FORM_PUBLISH') or hasAuthority('EVENT_UPDATE') or hasAuthority('SUPER_ADMIN')")
    @Transactional
    public PublishFormVersionResponse publishFormVersion(UUID eventPassCategoryId, String changelog) {
        EventPassCategory epc = eventPassCategoryRepository.findById(eventPassCategoryId)
                .orElseThrow(() -> new ResourceNotFoundException("EventPassCategory", eventPassCategoryId.toString()));

        List<FormFieldDto> draftFields = getDraftFields(eventPassCategoryId);
        if (draftFields.isEmpty()) {
            throw new InvalidRequestException("Cannot publish an empty form. Add at least one field.");
        }

        int maxVersion = registrationFormVersionRepository.findMaxVersionNumberByEventPassCategoryId(eventPassCategoryId);
        int nextVersion = maxVersion + 1;

        String schemaJson;
        try {
            schemaJson = objectMapper.writeValueAsString(draftFields);
        } catch (Exception e) {
            throw new InvalidRequestException("Failed to serialize form schema: " + e.getMessage());
        }

        RegistrationFormVersion version = new RegistrationFormVersion();
        version.setEventPassCategory(epc);
        version.setVersionNumber(nextVersion);
        version.setPublished(true);
        version.setSchemaSnapshot(schemaJson);

        RegistrationFormVersion savedVersion = registrationFormVersionRepository.save(version);

        epc.setPublishedFormVersion(savedVersion);
        eventPassCategoryRepository.save(epc);

        log.info("Published form version {} for EventPassCategory {}", nextVersion, eventPassCategoryId);

        return PublishFormVersionResponse.builder()
                .versionId(savedVersion.getId())
                .eventPassCategoryId(eventPassCategoryId)
                .versionNumber(savedVersion.getVersionNumber())
                .published(savedVersion.isPublished())
                .fieldCount(draftFields.size())
                .changelog(changelog)
                .publishedAt(savedVersion.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public FormSchemaResponse getPublishedFormSchema(UUID eventPassCategoryId) {
        EventPassCategory epc = eventPassCategoryRepository.findById(eventPassCategoryId)
                .orElseThrow(() -> new ResourceNotFoundException("EventPassCategory", eventPassCategoryId.toString()));

        RegistrationFormVersion version = epc.getPublishedFormVersion();
        if (version == null) {
            version = registrationFormVersionRepository
                    .findTopByEventPassCategoryIdAndPublishedTrueOrderByVersionNumberDesc(eventPassCategoryId)
                    .orElse(null);
        }

        if (version == null) {
            // Fallback to default fields
            List<FormFieldDto> defaultFields = getDefaultFieldsForCategory(
                    epc.getPassCategory() != null ? epc.getPassCategory().getCode() : "OTHER");
            return FormSchemaResponse.builder()
                    .eventPassCategoryId(eventPassCategoryId)
                    .versionNumber(0)
                    .published(false)
                    .fields(defaultFields)
                    .build();
        }

        List<FormFieldDto> fields = parseSchemaSnapshot(version.getSchemaSnapshot());
        return FormSchemaResponse.builder()
                .versionId(version.getId())
                .eventPassCategoryId(eventPassCategoryId)
                .versionNumber(version.getVersionNumber())
                .published(version.isPublished())
                .schemaSnapshot(version.getSchemaSnapshot())
                .fields(fields)
                .build();
    }

    @Transactional(readOnly = true)
    public List<RegistrationFormVersion> getVersionHistory(UUID eventPassCategoryId) {
        return registrationFormVersionRepository.findByEventPassCategoryIdOrderByVersionNumberDesc(eventPassCategoryId);
    }

    public List<FormFieldDto> parseSchemaSnapshot(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(schemaJson, new TypeReference<List<FormFieldDto>>() {});
        } catch (Exception e) {
            log.error("Failed to parse schema snapshot JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public void validateAnswersAgainstSchema(RegistrationFormVersion version, Map<String, Object> answers) {
        if (version == null || version.getSchemaSnapshot() == null) {
            return;
        }

        List<FormFieldDto> fields = parseSchemaSnapshot(version.getSchemaSnapshot());
        Map<String, Object> safeAnswers = answers != null ? answers : Collections.emptyMap();

        for (FormFieldDto field : fields) {
            if (!field.isVisible() || !field.isActive()) {
                continue;
            }

            Object value = safeAnswers.get(field.getFieldKey());
            String strVal = value != null ? String.valueOf(value).trim() : "";

            if (field.isRequired() && strVal.isEmpty()) {
                throw new InvalidRequestException(field.getLabel() + " is required.");
            }

            if (!strVal.isEmpty()) {
                validateFieldValue(field, strVal);
            }
        }
    }

    private void validateFieldValue(FormFieldDto field, String strVal) {
        switch (field.getFieldType()) {
            case EMAIL:
                if (!EMAIL_PATTERN.matcher(strVal).matches()) {
                    throw new InvalidRequestException(field.getLabel() + " must be a valid email address.");
                }
                break;
            case PHONE:
                if (!PHONE_PATTERN.matcher(strVal).matches()) {
                    throw new InvalidRequestException(field.getLabel() + " must be a valid phone number.");
                }
                break;
            case NUMBER:
                try {
                    Double.parseDouble(strVal);
                } catch (NumberFormatException e) {
                    throw new InvalidRequestException(field.getLabel() + " must be a numeric value.");
                }
                break;
            case SELECT:
            case RADIO:
                if (field.getOptions() != null && !field.getOptions().isBlank()) {
                    List<String> validOptions = Arrays.stream(field.getOptions().split(","))
                            .map(String::trim)
                            .map(String::toLowerCase)
                            .collect(Collectors.toList());
                    if (!validOptions.contains(strVal.toLowerCase())) {
                        throw new InvalidRequestException("Selected option for " + field.getLabel() + " is invalid.");
                    }
                }
                break;
            default:
                break;
        }
    }

    @Transactional
    public void saveAttendeeAnswers(Attendee attendee, EventPass pass, RegistrationFormVersion version, Map<String, Object> answers) {
        if (answers == null || answers.isEmpty()) {
            return;
        }

        List<AttendeeRegistrationAnswer> toSave = new ArrayList<>();
        answers.forEach((key, val) -> {
            if (val != null) {
                AttendeeRegistrationAnswer answer = new AttendeeRegistrationAnswer();
                answer.setPass(pass);
                answer.setAttendee(attendee);
                answer.setFormVersion(version);
                answer.setFieldKey(key);
                answer.setFieldValue(String.valueOf(val));
                toSave.add(answer);
            }
        });

        if (!toSave.isEmpty()) {
            attendeeRegistrationAnswerRepository.saveAll(toSave);
        }
    }

    private FormFieldDto toDto(FormFieldConfig config) {
        return FormFieldDto.builder()
                .id(config.getId())
                .fieldKey(config.getFieldKey())
                .label(config.getLabel())
                .description(config.getDescription())
                .placeholder(config.getPlaceholder())
                .fieldType(config.getFieldType())
                .required(config.isRequired())
                .visible(config.isVisible())
                .displayOrder(config.getDisplayOrder())
                .defaultValue(config.getDefaultValue())
                .validationRules(config.getValidationRules())
                .options(config.getOptions())
                .active(config.isActive())
                .build();
    }

    public List<FormFieldDto> getDefaultFieldsForCategory(String categoryCode) {
        List<FormFieldDto> list = new ArrayList<>();
        int order = 1;

        list.add(FormFieldDto.builder()
                .fieldKey("fullName")
                .label("Full Name")
                .placeholder("Enter your full name")
                .fieldType(FieldType.TEXT)
                .required(true)
                .visible(true)
                .displayOrder(order++)
                .active(true)
                .build());

        list.add(FormFieldDto.builder()
                .fieldKey("email")
                .label("Email Address")
                .placeholder("e.g. name@example.com")
                .fieldType(FieldType.EMAIL)
                .required(true)
                .visible(true)
                .displayOrder(order++)
                .active(true)
                .build());

        list.add(FormFieldDto.builder()
                .fieldKey("mobileNumber")
                .label("Mobile Number")
                .placeholder("e.g. +91 9876543210")
                .fieldType(FieldType.PHONE)
                .required(false)
                .visible(true)
                .displayOrder(order++)
                .active(true)
                .build());

        if ("STUDENT".equalsIgnoreCase(categoryCode)) {
            list.add(FormFieldDto.builder()
                    .fieldKey("college")
                    .label("College / University")
                    .placeholder("Enter your institution name")
                    .fieldType(FieldType.TEXT)
                    .required(true)
                    .visible(true)
                    .displayOrder(order++)
                    .active(true)
                    .build());

            list.add(FormFieldDto.builder()
                    .fieldKey("course")
                    .label("Course / Degree")
                    .placeholder("e.g. B.Tech Computer Science")
                    .fieldType(FieldType.TEXT)
                    .required(true)
                    .visible(true)
                    .displayOrder(order++)
                    .active(true)
                    .build());

            list.add(FormFieldDto.builder()
                    .fieldKey("yearSemester")
                    .label("Year / Semester")
                    .placeholder("e.g. 3rd Year / 5th Sem")
                    .fieldType(FieldType.TEXT)
                    .required(false)
                    .visible(true)
                    .displayOrder(order++)
                    .active(true)
                    .build());

            list.add(FormFieldDto.builder()
                    .fieldKey("studentId")
                    .label("Student ID / Roll No")
                    .placeholder("e.g. CS2024-042")
                    .fieldType(FieldType.TEXT)
                    .required(false)
                    .visible(true)
                    .displayOrder(order++)
                    .active(true)
                    .build());
        } else {
            list.add(FormFieldDto.builder()
                    .fieldKey("organization")
                    .label("Organization / Company")
                    .placeholder("e.g. Acme Corp or Self-employed")
                    .fieldType(FieldType.TEXT)
                    .required(false)
                    .visible(true)
                    .displayOrder(order++)
                    .active(true)
                    .build());

            list.add(FormFieldDto.builder()
                    .fieldKey("designation")
                    .label("Designation / Title")
                    .placeholder("e.g. Software Engineer")
                    .fieldType(FieldType.TEXT)
                    .required(false)
                    .visible(true)
                    .displayOrder(order++)
                    .active(true)
                    .build());
        }

        return list;
    }
}
