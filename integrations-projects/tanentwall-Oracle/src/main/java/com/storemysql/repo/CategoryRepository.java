// CategoryRepository.java
package com.storeoracle.repo;
import com.storeoracle.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CategoryRepository extends JpaRepository<Category, Long> { }