package com.app.eventmanagmentsystem.repository.specification;

import com.app.eventmanagmentsystem.model.Attendee;
import com.app.eventmanagmentsystem.model.enums.RegistrationStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AttendeeSpecification {

    public static Specification<Attendee> filterAttendees(
            UUID eventId,
            String search,
            RegistrationStatus status,
            String checkInStatus,
            Collection<UUID> checkedInAttendeeIds) {

        return (Root<Attendee> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (eventId != null) {
                predicates.add(cb.equal(root.get("event").get("id"), eventId));
            }

            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), searchPattern),
                        cb.like(cb.lower(root.get("email")), searchPattern),
                        cb.like(cb.lower(root.get("mobileNumber")), searchPattern),
                        cb.like(cb.lower(root.get("organization")), searchPattern)
                ));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("registrationStatus"), status));
            }

            if ("CHECKED_IN".equalsIgnoreCase(checkInStatus)) {
                if (checkedInAttendeeIds == null || checkedInAttendeeIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("id").in(checkedInAttendeeIds));
                }
            } else if ("NOT_CHECKED_IN".equalsIgnoreCase(checkInStatus)) {
                if (checkedInAttendeeIds != null && !checkedInAttendeeIds.isEmpty()) {
                    predicates.add(cb.not(root.get("id").in(checkedInAttendeeIds)));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

