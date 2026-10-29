// SupplierRepository.java
package com.storeMSsql.repo;
import com.storeMSsql.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SupplierRepository extends JpaRepository<Supplier, Long> { }