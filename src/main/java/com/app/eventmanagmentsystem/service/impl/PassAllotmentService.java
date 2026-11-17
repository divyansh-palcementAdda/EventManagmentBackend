package com.app.eventmanagmentsystem.service.impl;

import com.app.eventmanagmentsystem.exceptions.InvalidRequestException;
import com.app.eventmanagmentsystem.exceptions.ResourceNotFoundException;
import com.app.eventmanagmentsystem.model.*;
import com.app.eventmanagmentsystem.model.enums.AuditOutcome;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import com.app.eventmanagmentsystem.payload.allotment.*;
import com.app.eventmanagmentsystem.repository.*;
import com.app.eventmanagmentsystem.service.interfaces.AuditLogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing internal pass allotments, sequence capacity tracking,
 * and category-wise allotment analytics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassAllotmentService {

    private static final int DEFAULT_SEQUENCE_CHUNK_SIZE = 100;

    private final EventRepository eventRepository;
    private final EventPassRepository passRepository;
    private final PassAllotmentRepository passAllotmentRepository;
    private final PassCategoryRepository passCategoryRepository;
    private final EventPassCategoryRepository eventPassCategoryRepository;
    private final CheckInRepository checkInRepository;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;

    /**
     * Discovers and returns available pass sequences for a selected category in an event.
     */
    @PreAuthorize("hasAuthority('PASS_ALLOT_VIEW') or hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public List<PassSequenceOptionResponse> getSequencesForCategory(UUID eventId, String categoryCode) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }

        if (categoryCode == null || categoryCode.isBlank()) {
            return Collections.emptyList();
        }

        String normalizedCode = categoryCode.trim().toUpperCase();
        List<EventPass> passes = passRepository.findByEventIdAndCategoryOrderBySequenceNumberAsc(eventId, normalizedCode);

        if (passes.isEmpty()) {
            return Collections.emptyList();
        }

        String categoryName = resolveCategoryDisplayName(eventId, normalizedCode);
        List<PassSequenceOptionResponse> options = new ArrayList<>();

        // Group into contiguous blocks of up to DEFAULT_SEQUENCE_CHUNK_SIZE
        List<EventPass> currentChunk = new ArrayList<>();
        Long lastSeq = null;

        for (EventPass pass : passes) {
            Long seq = pass.getSequenceNumber();
            if (seq == null) continue;

            boolean isContiguous = (lastSeq == null) || (seq.equals(lastSeq + 1));
            boolean reachedLimit = currentChunk.size() >= DEFAULT_SEQUENCE_CHUNK_SIZE;

            if (!isContiguous || reachedLimit) {
                if (!currentChunk.isEmpty()) {
                    options.add(buildSequenceOption(currentChunk, normalizedCode, categoryName));
                    currentChunk = new ArrayList<>();
                }
            }

            currentChunk.add(pass);
            lastSeq = seq;
        }

        if (!currentChunk.isEmpty()) {
            options.add(buildSequenceOption(currentChunk, normalizedCode, categoryName));
        }

        return options;
    }

    private PassSequenceOptionResponse buildSequenceOption(List<EventPass> chunk, String categoryCode, String categoryName) {
        EventPass first = chunk.get(0);
        EventPass last = chunk.get(chunk.size() - 1);
        Long fromSeq = first.getSequenceNumber();
        Long toSeq = last.getSequenceNumber();
        long total = chunk.size();
        long allotted = chunk.stream().filter(p -> p.getAllotment() != null).count();
        long available = total - allotted;

        String displayName = first.getPassNumber() + " to " + last.getPassNumber();

        return PassSequenceOptionResponse.builder()
                .sequenceId(fromSeq + "-" + toSeq)
                .displayName(displayName)
                .categoryCode(categoryCode)
                .categoryName(categoryName)
                .fromSequence(fromSeq)
                .toSequence(toSeq)
                .totalPasses(total)
                .allottedPasses(allotted)
                .availablePasses(available)
                .build();
    }

    /**
     * Inspects a specific sequence range to return capacity, already allotted, and available counts.
     */
    @PreAuthorize("hasAuthority('PASS_ALLOT_VIEW') or hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public PassSequenceSummaryResponse getSequenceSummary(UUID eventId, String categoryCode, Long fromSequence, Long toSequence) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }

        if (categoryCode == null || categoryCode.isBlank()) {
            throw new InvalidRequestException("Category code is required");
        }
        if (fromSequence == null || toSequence == null) {
            throw new InvalidRequestException("Starting and ending sequence numbers are required");
        }
        if (fromSequence > toSequence) {
            throw new InvalidRequestException("Starting sequence cannot be greater than ending sequence");
        }

        String normalizedCode = categoryCode.trim().toUpperCase();
        long totalCapacity = passRepository.countByEventIdAndCategoryAndSequenceNumberBetween(eventId, normalizedCode, fromSequence, toSequence);
        if (totalCapacity == 0) {
            throw new InvalidRequestException("No passes found for category " + normalizedCode + " in sequence range " + fromSequence + " to " + toSequence);
        }

        long alreadyAllotted = passRepository.countAllottedByEventIdAndCategoryAndSequenceNumberBetween(eventId, normalizedCode, fromSequence, toSequence);
        long availableToAllot = Math.max(0, totalCapacity - alreadyAllotted);

        List<EventPass> sample = passRepository.findByEventIdAndSequenceNumberBetweenOrderBySequenceNumberAsc(eventId, fromSequence, toSequence);
        String displayName = sample.isEmpty()
                ? ("#" + fromSequence + " to #" + toSequence)
                : (sample.get(0).getPassNumber() + " to " + sample.get(sample.size() - 1).getPassNumber());

        String categoryName = resolveCategoryDisplayName(eventId, normalizedCode);

        return PassSequenceSummaryResponse.builder()
                .sequenceId(fromSequence + "-" + toSequence)
                .displayName(displayName)
                .categoryCode(normalizedCode)
                .categoryName(categoryName)
                .fromSequence(fromSequence)
                .toSequence(toSequence)
                .totalCapacity(totalCapacity)
                .alreadyAllotted(alreadyAllotted)
                .availableToAllot(availableToAllot)
                .build();
    }

    /**
     * Concurrency-safe, transactional pass allotment with pessimistic locking.
     */
    @PreAuthorize("hasAuthority('PASS_ALLOT_MANAGE') or hasAuthority('PASS_GENERATE')")
    @Transactional
    public PassAllotmentResponse createAllotment(UUID eventId, CreatePassAllotmentRequest request) {
        // 1. Lock event row to serialize allocation mutations
        Event event = entityManager.find(Event.class, eventId, LockModeType.PESSIMISTIC_WRITE);
        if (event == null) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }

        // 2. Validate input
        String allottedTo = request.getAllottedTo() != null ? request.getAllottedTo().trim() : "";
        if (allottedTo.isBlank()) {
            throw new InvalidRequestException("Allotted to (person or department name) cannot be empty.");
        }

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new InvalidRequestException("Allotment quantity must be a positive number.");
        }

        if (request.getFromSequence() == null || request.getToSequence() == null) {
            throw new InvalidRequestException("Sequence boundaries (fromSequence and toSequence) are required.");
        }

        if (request.getFromSequence() > request.getToSequence()) {
            throw new InvalidRequestException("Starting sequence number cannot be greater than ending sequence number.");
        }

        String normalizedCode = request.getCategoryCode() != null ? request.getCategoryCode().trim().toUpperCase() : "";
        if (normalizedCode.isBlank()) {
            throw new InvalidRequestException("Category code is required.");
        }

        // Verify category belongs to or is configured for the event
        validateCategoryForEvent(event, normalizedCode);

        // 3. Atomically acquire unallotted passes in the sequence range with PESSIMISTIC_WRITE lock
        List<EventPass> availablePasses = passRepository.findUnallottedPassesForUpdate(
                eventId, normalizedCode, request.getFromSequence(), request.getToSequence());

        if (availablePasses.size() < request.getQuantity()) {
            String seqLabel = (request.getSequenceIdentifier() != null && !request.getSequenceIdentifier().isBlank())
                    ? request.getSequenceIdentifier()
                    : ("#" + request.getFromSequence() + " to #" + request.getToSequence());
            throw new InvalidRequestException(String.format(
                    "Cannot allot %d passes. Only %d unallotted passes remain in sequence %s.",
                    request.getQuantity(), availablePasses.size(), seqLabel));
        }

        // 4. Select exact passes to allot
        List<EventPass> passesToAllot = availablePasses.subList(0, request.getQuantity());

        String resolvedSeqIdentifier = request.getSequenceIdentifier();
        if (resolvedSeqIdentifier == null || resolvedSeqIdentifier.isBlank()) {
            resolvedSeqIdentifier = passesToAllot.get(0).getPassNumber() + " to " + passesToAllot.get(passesToAllot.size() - 1).getPassNumber();
        }

        // 5. Create PassAllotment record
        PassAllotment allotment = new PassAllotment();
        allotment.setEvent(event);
        allotment.setCategoryCode(normalizedCode);
        allotment.setPassCategory(resolvePassCategory(normalizedCode, event));
        allotment.setEventCategory(resolveEventPassCategory(normalizedCode, event));
        allotment.setSequenceIdentifier(resolvedSeqIdentifier);
        allotment.setFromSequence(request.getFromSequence());
        allotment.setToSequence(request.getToSequence());
        allotment.setAllottedTo(allottedTo);
        allotment.setAllottedQuantity(request.getQuantity());
        allotment.setRemarks(request.getRemarks() != null ? request.getRemarks().trim() : null);

        PassAllotment savedAllotment = passAllotmentRepository.save(allotment);

        // 6. Link each pass to the allotment
        for (EventPass pass : passesToAllot) {
            pass.setAllotment(savedAllotment);
            passRepository.save(pass);
        }

        auditLogService.log("PASS_ALLOT", "PassAllotment", savedAllotment.getId().toString(),
                event.getId().toString(), AuditOutcome.SUCCESS, null,
                String.format("Allotted %d %s passes (sequence %s) to %s",
                        request.getQuantity(), normalizedCode, resolvedSeqIdentifier, allottedTo));

        log.info("Successfully allotted {} {} passes to {} in event {}",
                request.getQuantity(), normalizedCode, allottedTo, event.getEventCode());

        return PassAllotmentResponse.builder()
                .id(savedAllotment.getId())
                .eventId(event.getId())
                .categoryCode(normalizedCode)
                .sequenceIdentifier(resolvedSeqIdentifier)
                .fromSequence(savedAllotment.getFromSequence())
                .toSequence(savedAllotment.getToSequence())
                .allottedTo(allottedTo)
                .allottedQuantity(savedAllotment.getAllottedQuantity())
                .remarks(savedAllotment.getRemarks())
                .createdAt(savedAllotment.getCreatedAt())
                .createdByName(savedAllotment.getCreatedByName())
                .build();
    }

    /**
     * Returns event-wide allotment statistics and a recipient-grouped summary table.
     */
    @PreAuthorize("hasAuthority('PASS_ALLOT_VIEW') or hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public PassAllotmentSummaryResponse getAllotmentsSummary(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }

        List<PassAllotment> allotments = passAllotmentRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        if (allotments.isEmpty()) {
            return PassAllotmentSummaryResponse.builder()
                    .overallStats(PassAllotmentOverallStats.builder()
                            .totalAllotted(0)
                            .totalActivated(0)
                            .totalAvailable(0)
                            .totalCheckedIn(0)
                            .build())
                    .recipients(Collections.emptyList())
                    .build();
        }

        List<UUID> allotmentIds = allotments.stream().map(PassAllotment::getId).collect(Collectors.toList());
        List<EventPass> allottedPasses = passRepository.findByAllotmentIdInOrderBySequenceNumberAsc(allotmentIds);
        Set<UUID> checkedInPassIds = checkInRepository.findCheckedInPassIdsByEventId(eventId);

        // Overall event-level allotment stats
        long totalAllotted = allottedPasses.size();
        long totalActivated = allottedPasses.stream().filter(p -> p.getStatus() == PassStatus.ACTIVATED).count();
        long totalAvailable = Math.max(0, totalAllotted - totalActivated);
        long totalCheckedIn = allottedPasses.stream()
                .filter(p -> p.getStatus() == PassStatus.ACTIVATED && checkedInPassIds.contains(p.getId()))
                .count();

        PassAllotmentOverallStats overallStats = PassAllotmentOverallStats.builder()
                .totalAllotted(totalAllotted)
                .totalActivated(totalActivated)
                .totalAvailable(totalAvailable)
                .totalCheckedIn(totalCheckedIn)
                .build();

        // Group passes by recipient (case-insensitive name preserved)
        Map<String, List<EventPass>> passesByRecipient = allottedPasses.stream()
                .filter(p -> p.getAllotment() != null && p.getAllotment().getAllottedTo() != null)
                .collect(Collectors.groupingBy(p -> p.getAllotment().getAllottedTo().trim()));

        List<PassAllotmentRecipientSummary> recipients = new ArrayList<>();

        for (Map.Entry<String, List<EventPass>> entry : passesByRecipient.entrySet()) {
            String recipient = entry.getKey();
            List<EventPass> personPasses = entry.getValue();

            long personAllotted = personPasses.size();
            long personActivated = personPasses.stream().filter(p -> p.getStatus() == PassStatus.ACTIVATED).count();
            long personAvailable = Math.max(0, personAllotted - personActivated);
            long personCheckedIn = personPasses.stream()
                    .filter(p -> p.getStatus() == PassStatus.ACTIVATED && checkedInPassIds.contains(p.getId()))
                    .count();

            // Group recipient passes by category
            Map<String, List<EventPass>> passesByCat = personPasses.stream()
                    .collect(Collectors.groupingBy(p -> p.getCategory() != null ? p.getCategory().toUpperCase() : "OTHER"));

            List<PassAllotmentCategoryDetail> catDetails = new ArrayList<>();
            List<String> distStrings = new ArrayList<>();

            for (Map.Entry<String, List<EventPass>> catEntry : passesByCat.entrySet()) {
                String catCode = catEntry.getKey();
                List<EventPass> catPasses = catEntry.getValue();

                long cAllotted = catPasses.size();
                long cActivated = catPasses.stream().filter(p -> p.getStatus() == PassStatus.ACTIVATED).count();
                long cAvailable = Math.max(0, cAllotted - cActivated);
                long cCheckedIn = catPasses.stream()
                        .filter(p -> p.getStatus() == PassStatus.ACTIVATED && checkedInPassIds.contains(p.getId()))
                        .count();

                String catName = resolveCategoryDisplayName(eventId, catCode);
                distStrings.add(catName + ": " + cAllotted);

                List<UUID> matchingAllotmentIds = catPasses.stream()
                        .map(p -> p.getAllotment().getId())
                        .distinct()
                        .collect(Collectors.toList());

                catDetails.add(PassAllotmentCategoryDetail.builder()
                        .categoryCode(catCode)
                        .categoryName(catName)
                        .allotted(cAllotted)
                        .activated(cActivated)
                        .available(cAvailable)
                        .checkedIn(cCheckedIn)
                        .allotmentIds(matchingAllotmentIds)
                        .build());
            }

            // Sort categories alphabetically by name
            catDetails.sort(Comparator.comparing(PassAllotmentCategoryDetail::getCategoryName));

            recipients.add(PassAllotmentRecipientSummary.builder()
                    .allottedTo(recipient)
                    .totalAllotted(personAllotted)
                    .categoryDistribution(String.join(", ", distStrings))
                    .totalActivated(personActivated)
                    .totalAvailable(personAvailable)
                    .totalCheckedIn(personCheckedIn)
                    .categories(catDetails)
                    .build());
        }

        // Sort recipients alphabetically
        recipients.sort(Comparator.comparing(PassAllotmentRecipientSummary::getAllottedTo, String.CASE_INSENSITIVE_ORDER));

        return PassAllotmentSummaryResponse.builder()
                .overallStats(overallStats)
                .recipients(recipients)
                .build();
    }

    /**
     * Returns category-wise analytics with paginated pass details for a specific recipient.
     */
    @PreAuthorize("hasAuthority('PASS_ALLOT_VIEW') or hasAuthority('PASS_VIEW')")
    @Transactional(readOnly = true)
    public PassAllotmentAnalyticsResponse getCategoryAnalytics(
            UUID eventId,
            String allottedTo,
            String categoryCode,
            String search,
            PassStatus activationStatus,
            String checkInStatus,
            Pageable pageable) {

        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId.toString());
        }

        if (allottedTo == null || allottedTo.isBlank()) {
            throw new InvalidRequestException("Allotted to person name is required.");
        }
        if (categoryCode == null || categoryCode.isBlank()) {
            throw new InvalidRequestException("Category code is required.");
        }

        String normalizedCode = categoryCode.trim().toUpperCase();
        String normalizedRecipient = allottedTo.trim();

        List<PassAllotment> matchingAllotments = passAllotmentRepository.findByEventIdAndAllottedToIgnoreCase(eventId, normalizedRecipient)
                .stream()
                .filter(a -> a.getCategoryCode().equalsIgnoreCase(normalizedCode))
                .collect(Collectors.toList());

        String categoryName = resolveCategoryDisplayName(eventId, normalizedCode);

        if (matchingAllotments.isEmpty()) {
            return PassAllotmentAnalyticsResponse.builder()
                    .allottedTo(normalizedRecipient)
                    .categoryCode(normalizedCode)
                    .categoryName(categoryName)
                    .summary(PassAllotmentAnalyticsSummary.builder()
                            .totalAllotted(0)
                            .activated(0)
                            .available(0)
                            .checkedIn(0)
                            .activatedNotCheckedIn(0)
                            .build())
                    .passes(new PageImpl<>(Collections.emptyList(), pageable, 0))
                    .build();
        }

        List<UUID> allotmentIds = matchingAllotments.stream().map(PassAllotment::getId).collect(Collectors.toList());
        List<EventPass> allCategoryPasses = passRepository.findByAllotmentIdInOrderBySequenceNumberAsc(allotmentIds);

        List<UUID> passIds = allCategoryPasses.stream().map(EventPass::getId).collect(Collectors.toList());
        Map<UUID, CheckIn> checkInsByPassId = passIds.isEmpty() ? Collections.emptyMap() :
                checkInRepository.findByPassIdIn(passIds).stream()
                        .collect(Collectors.toMap(c -> c.getPass().getId(), c -> c, (c1, c2) -> c1));

        // Exact category counts
        long totalAllotted = allCategoryPasses.size();
        long activated = allCategoryPasses.stream().filter(p -> p.getStatus() == PassStatus.ACTIVATED).count();
        long available = Math.max(0, totalAllotted - activated);
        long checkedIn = allCategoryPasses.stream().filter(p -> checkInsByPassId.containsKey(p.getId())).count();
        long activatedNotCheckedIn = Math.max(0, activated - checkedIn);

        PassAllotmentAnalyticsSummary summary = PassAllotmentAnalyticsSummary.builder()
                .totalAllotted(totalAllotted)
                .activated(activated)
                .available(available)
                .checkedIn(checkedIn)
                .activatedNotCheckedIn(activatedNotCheckedIn)
                .build();

        // Apply search and status filters
        List<EventPass> filtered = allCategoryPasses.stream()
                .filter(p -> {
                    if (search != null && !search.isBlank()) {
                        String s = search.trim().toLowerCase();
                        boolean matchPass = p.getPassNumber() != null && p.getPassNumber().toLowerCase().contains(s);
                        boolean matchAttendee = false;
                        if (p.getAttendee() != null) {
                            String name = p.getAttendee().getFullName();
                            String email = p.getAttendee().getEmail();
                            matchAttendee = (name != null && name.toLowerCase().contains(s))
                                    || (email != null && email.toLowerCase().contains(s));
                        }
                        if (!matchPass && !matchAttendee) {
                            return false;
                        }
                    }

                    if (activationStatus != null && p.getStatus() != activationStatus) {
                        return false;
                    }

                    if (checkInStatus != null && !checkInStatus.isBlank() && !"ALL".equalsIgnoreCase(checkInStatus)) {
                        boolean isChecked = checkInsByPassId.containsKey(p.getId());
                        if ("CHECKED_IN".equalsIgnoreCase(checkInStatus) && !isChecked) return false;
                        if ("NOT_CHECKED_IN".equalsIgnoreCase(checkInStatus) && isChecked) return false;
                    }

                    return true;
                })
                .collect(Collectors.toList());

        // Apply pagination
        int totalElements = filtered.size();
        int fromIndex = (int) pageable.getOffset();
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), totalElements);

        List<PassAllotmentItemResponse> pagedItems;
        if (fromIndex >= totalElements) {
            pagedItems = Collections.emptyList();
        } else {
            pagedItems = filtered.subList(fromIndex, toIndex).stream()
                    .map(p -> {
                        CheckIn ci = checkInsByPassId.get(p.getId());
                        Attendee att = p.getAttendee();
                        return PassAllotmentItemResponse.builder()
                                .passId(p.getId())
                                .passNumber(p.getPassNumber())
                                .sequenceNumber(p.getSequenceNumber())
                                .category(p.getCategory())
                                .categoryName(categoryName)
                                .status(p.getStatus() != null ? p.getStatus().name() : "NOT_ACTIVATED")
                                .attendeeName(att != null ? att.getFullName() : null)
                                .attendeeEmail(att != null ? att.getEmail() : null)
                                .checkedIn(ci != null)
                                .firstCheckInTime(ci != null ? ci.getCheckedInAt() : null)
                                .activatedAt(p.getActivatedAt())
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        Page<PassAllotmentItemResponse> page = new PageImpl<>(pagedItems, pageable, totalElements);

        return PassAllotmentAnalyticsResponse.builder()
                .allottedTo(normalizedRecipient)
                .categoryCode(normalizedCode)
                .categoryName(categoryName)
                .summary(summary)
                .passes(page)
                .build();
    }

    private void validateCategoryForEvent(Event event, String categoryCode) {
        // Check dynamic event-category config
        Optional<EventPassCategory> epc = eventPassCategoryRepository.findByEventIdAndCategoryCode(event.getId(), categoryCode);
        if (epc.isPresent()) {
            if (!epc.get().isActive()) {
                throw new InvalidRequestException("Category " + categoryCode + " is disabled for this event.");
            }
            return;
        }

        // Check global or event-scoped PassCategory
        Optional<PassCategory> pc = passCategoryRepository.findByCodeIgnoreCase(categoryCode);
        if (pc.isPresent()) {
            PassCategory c = pc.get();
            if (c.getEvent() != null && !c.getEvent().getId().equals(event.getId())) {
                throw new InvalidRequestException("Category " + categoryCode + " does not belong to this event.");
            }
            return;
        }

        // Fallback check against passes existing in event with this category
        long count = passRepository.countByEventIdAndCategoryAndSequenceNumberBetween(event.getId(), categoryCode, 1L, Long.MAX_VALUE);
        if (count == 0) {
            throw new InvalidRequestException("Category " + categoryCode + " is not configured or present in event " + event.getEventCode());
        }
    }

    private PassCategory resolvePassCategory(String categoryCode, Event event) {
        Optional<EventPassCategory> epc = eventPassCategoryRepository.findByEventIdAndCategoryCode(event.getId(), categoryCode);
        if (epc.isPresent() && epc.get().getPassCategory() != null) {
            return epc.get().getPassCategory();
        }
        return passCategoryRepository.findByCodeIgnoreCase(categoryCode).orElse(null);
    }

    private EventPassCategory resolveEventPassCategory(String categoryCode, Event event) {
        return eventPassCategoryRepository.findByEventIdAndCategoryCode(event.getId(), categoryCode).orElse(null);
    }

    private String resolveCategoryDisplayName(UUID eventId, String categoryCode) {
        Optional<EventPassCategory> epc = eventPassCategoryRepository.findByEventIdAndCategoryCode(eventId, categoryCode);
        if (epc.isPresent() && epc.get().getPassCategory() != null) {
            return epc.get().getPassCategory().getName();
        }
        Optional<PassCategory> pc = passCategoryRepository.findByCodeIgnoreCase(categoryCode);
        if (pc.isPresent()) {
            return pc.get().getName();
        }
        return categoryCode.substring(0, 1).toUpperCase() + categoryCode.substring(1).toLowerCase();
    }
}
