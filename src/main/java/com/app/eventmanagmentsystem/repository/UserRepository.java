package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.User;
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
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permissions WHERE u.email = :email")
    Optional<User> findByEmailWithRolesAndPermissions(@Param("email") String email);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r.name = 'GATEKEEPER' AND (:query IS NULL OR :query = '' OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<User> searchGatekeeperUsers(@Param("query") String query, Pageable pageable);

    @Query("SELECT DISTINCT u FROM User u JOIN FETCH u.roles r WHERE r.name = 'GATEKEEPER'")
    List<User> findAllGatekeeperUsers();

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.id = :roleId")
    int countUsersByRoleId(@Param("roleId") UUID roleId);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.id = :roleId")
    Page<User> findUsersByRoleId(@Param("roleId") UUID roleId, Pageable pageable);
}
