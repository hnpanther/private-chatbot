package com.hnp.privatechatbot.repository;

import com.hnp.privatechatbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Data access for {@link User} entities. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /** Used at registration time to prevent duplicate usernames. */
    boolean existsByUsername(String username);
}
