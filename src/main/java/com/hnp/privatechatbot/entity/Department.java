package com.hnp.privatechatbot.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Organisational department.  Chatbots are assigned to exactly one department;
 * users belong to one or more departments and can only access chatbots in their own.
 */
@Entity
@Table(name = "departments")
@Data
@NoArgsConstructor
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Persian display name. */
    @Column(nullable = false, length = 100)
    private String name;

    /** Optional English name (used for API / reporting). */
    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
    private List<ChatBot> chatBots = new ArrayList<>();
}
