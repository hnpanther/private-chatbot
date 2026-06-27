package com.hnp.privatechatbot.repository;

import com.hnp.privatechatbot.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Data access for {@link Role} entities. */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}
