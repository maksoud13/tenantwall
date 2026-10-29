// CategoryRepository.java
package com.storemysql.repo;
import com.storemysql.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CategoryRepository extends JpaRepository<Category, Long> { }