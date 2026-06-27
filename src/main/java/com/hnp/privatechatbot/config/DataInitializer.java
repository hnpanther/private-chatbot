package com.hnp.privatechatbot.config;

import com.hnp.privatechatbot.entity.Role;
import com.hnp.privatechatbot.entity.User;
import com.hnp.privatechatbot.repository.RoleRepository;
import com.hnp.privatechatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Seeds the database with mandatory roles and a default admin account on first startup.
 * All operations are idempotent — safe to run on every application start.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.debug("DataInitializer: seeding roles and default admin account");

        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> {
                    log.info("Creating role: ROLE_ADMIN");
                    return roleRepository.save(new Role("ROLE_ADMIN", "System administrator"));
                });

        roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> {
                    log.info("Creating role: ROLE_USER");
                    return roleRepository.save(new Role("ROLE_USER", "Regular user"));
                });

        roleRepository.findByName("ROLE_DEPT_ADMIN")
                .orElseGet(() -> {
                    log.info("Creating role: ROLE_DEPT_ADMIN");
                    return roleRepository.save(new Role("ROLE_DEPT_ADMIN", "Department administrator"));
                });

        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setFullName("System Administrator");
            admin.setEmail("admin@localhost");
            admin.setRoles(Set.of(adminRole));
            userRepository.save(admin);
            log.warn("Default admin account created — username: admin  password: admin123 — change this in production!");
        } else {
            log.debug("Admin account already exists, skipping seed");
        }
    }
}
