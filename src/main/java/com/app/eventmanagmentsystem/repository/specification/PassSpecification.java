package com.app.eventmanagmentsystem.repository.specification;

import com.app.eventmanagmentsystem.model.Attendee;
import com.app.eventmanagmentsystem.model.EventDay;
import com.app.eventmanagmentsystem.model.EventPass;
import com.app.eventmanagmentsystem.model.enums.PassCategory;
import com.app.eventmanagmentsystem.model.enums.PassStatus;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class PassSpecification {

    public static Specification<EventPass> filterPasses(
            UUID eventId,
            String search,
            PassStatus status,
            PassCategory category,
            Long fromSequence,
            Long toSequence,
            EventDay eventDay,
            String checkInStatus,
            Collection<UUID> checkedInPassIds) {

        return (Root<EventPass> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Mandatory Event ID
            predicates.add(cb.equal(root.get("event").get("id"), eventId));

            // 2. Search query (matches passNumber, sequenceNumber, attendee name, email)
            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Join<EventPass, Attendee> attendeeJoin = root.join("attendee", JoinType.LEFT);

                List<Predicate> searchPredicates = new ArrayList<>();
                searchPredicates.add(cb.like(cb.lower(root.get("passNumber")), searchPattern));
                searchPredicates.add(cb.like(cb.lower(attendeeJoin.get("fullName")), searchPattern));
                searchPredicates.add(cb.like(cb.lower(attendeeJoin.get("email")), searchPattern));

                // If query is an integer, also check sequence_number
                try {
                    long seq = Long.parseLong(search.trim());
                    searchPredicates.add(cb.equal(root.get("sequenceNumber"), seq));
                } catch (NumberFormatException ignored) {}

                predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
            }

            // 3. Status filter
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 3b. Category filter
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            // 4. Sequence range
            if (fromSequence != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sequenceNumber"), fromSequence));
            }
            if (toSequence != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sequenceNumber"), toSequence));
            }

            // 5. Specific Event Day filter
            if (eventDay != null) {
                // Either explicitly in validDays OR validDays is empty (valid for all days)
                Predicate inValidDays = cb.isMember(eventDay, root.get("validDays"));
                Predicate emptyValidDays = cb.isEmpty(root.get("validDays"));
                predicates.add(cb.or(inValidDays, emptyValidDays));
            }

            // 6. Check-in status filter
            if ("CHECKED_IN".equalsIgnoreCase(checkInStatus)) {
                if (checkedInPassIds == null || checkedInPassIds.isEmpty()) {
                    predicates.add(cb.disjunction()); // No passes are checked in
                } else {
                    predicates.add(root.get("id").in(checkedInPassIds));
                }
            } else if ("NOT_CHECKED_IN".equalsIgnoreCase(checkInStatus)) {
                if (checkedInPassIds != null && !checkedInPassIds.isEmpty()) {
                    predicates.add(cb.not(root.get("id").in(checkedInPassIds)));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

