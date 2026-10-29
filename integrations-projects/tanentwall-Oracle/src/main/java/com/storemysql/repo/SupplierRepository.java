// SupplierRepository.java
package com.storeoracle.repo;
import com.storeoracle.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SupplierRepository extends JpaRepository<Supplier, Long> { }