// CategoryRepository.java
package com.storeMSsql.repo;
import com.storeMSsql.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CategoryRepository extends JpaRepository<Category, Long> { }