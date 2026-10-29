// SupplierRepository.java
package com.storemysql.repo;
import com.storemysql.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SupplierRepository extends JpaRepository<Supplier, Long> { }