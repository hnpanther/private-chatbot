package com.hnp.privatechatbot.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Spring Security role (e.g. ROLE_ADMIN, ROLE_USER, ROLE_DEPT_ADMIN).
 * Seeded by {@link com.hnp.privatechatbot.config.DataInitializer} on first startup.
 */
@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Role authority string — must include the "ROLE_" prefix expected by Spring Security. */
    @Column(unique = true, nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
