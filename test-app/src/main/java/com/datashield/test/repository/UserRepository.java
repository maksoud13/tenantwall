package com.datashield.test.repository;

import com.datashield.test.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // استعلام 1: JPA هيترجمه لـ SQL (هيتعترض)
    List<User> findByTenantId(String tenantId);

    // استعلام 2: استعلام Native مكتوب بالأحرف (ده الـ Agent هيمسكه بسهولة)
    @Query(value = "SELECT * FROM users WHERE name = :name", nativeQuery = true)
    List<User> findUsersByNameNative(@Param("name") String name);
}