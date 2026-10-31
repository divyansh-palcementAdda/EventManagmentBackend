package com.app.eventmanagmentsystem.repository;

import com.app.eventmanagmentsystem.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID>, JpaSpecificationExecutor<Permission> {

    Optional<Permission> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT DISTINCT p.resource FROM Permission p WHERE p.resource IS NOT NULL ORDER BY p.resource")
    List<String> findDistinctResources();

    @Query("SELECT p FROM Permission p JOIN Role r ON p MEMBER OF r.permissions WHERE r.id = :roleId")
    List<Permission> findPermissionsByRoleId(@Param("roleId") UUID roleId);
}
