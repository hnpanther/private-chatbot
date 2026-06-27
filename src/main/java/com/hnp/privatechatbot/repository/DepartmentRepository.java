package com.hnp.privatechatbot.repository;

import com.hnp.privatechatbot.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Data access for {@link Department} entities. */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByActiveTrue();
}
