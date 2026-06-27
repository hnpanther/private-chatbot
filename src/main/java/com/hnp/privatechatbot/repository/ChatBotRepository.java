package com.hnp.privatechatbot.repository;

import com.hnp.privatechatbot.entity.ChatBot;
import com.hnp.privatechatbot.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Data access for {@link ChatBot} entities. */
public interface ChatBotRepository extends JpaRepository<ChatBot, Long> {

    /** All active chatbots sorted by department name — used by the admin listing. */
    List<ChatBot> findByActiveTrueOrderByDepartmentNameAsc();

    List<ChatBot> findByDepartmentAndActiveTrue(Department department);

    /**
     * Active chatbots whose department is in the given set.
     * Used to restrict non-admin users to their own department's bots.
     */
    @Query("SELECT cb FROM ChatBot cb WHERE cb.active = true AND cb.department IN :departments ORDER BY cb.department.name ASC")
    List<ChatBot> findByDepartmentsAndActiveTrue(@Param("departments") java.util.Set<Department> departments);
}
