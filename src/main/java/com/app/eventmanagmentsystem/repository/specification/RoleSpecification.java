package com.app.eventmanagmentsystem.repository.specification;

import com.app.eventmanagmentsystem.model.Role;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class RoleSpecification {

    public static Specification<Role> filterRoles(String search, Boolean systemDefined) {
        return (Root<Role> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), searchPattern),
                        cb.like(cb.lower(root.get("description")), searchPattern)
                ));
            }

            if (systemDefined != null) {
                predicates.add(cb.equal(root.get("systemDefined"), systemDefined));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

