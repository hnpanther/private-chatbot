package com.hnp.privatechatbot.service;

import com.hnp.privatechatbot.dto.UserCreateRequest;
import com.hnp.privatechatbot.entity.Department;
import com.hnp.privatechatbot.entity.Role;
import com.hnp.privatechatbot.entity.User;
import com.hnp.privatechatbot.repository.DepartmentRepository;
import com.hnp.privatechatbot.repository.RoleRepository;
import com.hnp.privatechatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages application users and implements Spring Security's {@link UserDetailsService}
 * so that authentication can resolve a username to a set of granted authorities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Called by Spring Security during authentication.
     * Loads the user record and maps its roles to Spring Security authority objects.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user for authentication: username={}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Authentication failed — user not found: username={}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());

        log.debug("User loaded: username={}, enabled={}, roles={}", username, user.isEnabled(), authorities);
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(), user.getPassword(), user.isEnabled(),
                true, true, true, authorities
        );
    }

    /**
     * Retrieves the full {@link User} entity by username (not a UserDetails object).
     * Used by controllers after authentication to access department/role data.
     */
    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        log.debug("Finding user entity: username={}", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /** Creates a new user from the admin form data. Throws if the username is already taken. */
    @Transactional
    public User createUser(UserCreateRequest req) {
        log.info("Creating user: username={}, fullName={}", req.getUsername(), req.getFullName());
        if (userRepository.existsByUsername(req.getUsername())) {
            log.warn("Create user failed — username already exists: {}", req.getUsername());
            throw new IllegalArgumentException("Username already in use: " + req.getUsername());
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setFullName(req.getFullName());
        user.setEmail(req.getEmail());

        if (req.getRoleIds() != null && !req.getRoleIds().isEmpty()) {
            Set<Role> roles = req.getRoleIds().stream()
                    .map(id -> roleRepository.findById(id).orElseThrow())
                    .collect(Collectors.toSet());
            user.setRoles(roles);
            log.debug("Assigned roles {} to new user {}", req.getRoleIds(), req.getUsername());
        }

        if (req.getDepartmentIds() != null && !req.getDepartmentIds().isEmpty()) {
            Set<Department> departments = req.getDepartmentIds().stream()
                    .map(id -> departmentRepository.findById(id).orElseThrow())
                    .collect(Collectors.toSet());
            user.setDepartments(departments);
            log.debug("Assigned departments {} to new user {}", req.getDepartmentIds(), req.getUsername());
        }

        User saved = userRepository.save(user);
        log.info("User created successfully: id={}, username={}", saved.getId(), saved.getUsername());
        return saved;
    }

    /** Updates display name, email, password (optional), roles and departments of an existing user. */
    @Transactional
    public User updateUser(Long userId, UserCreateRequest req) {
        log.info("Updating user: id={}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setFullName(req.getFullName());
        user.setEmail(req.getEmail());

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.getPassword()));
            log.info("Password changed for user id={}", userId);
        }

        // Always replace roles and departments so unchecking all boxes clears them.
        Set<Long> roleIds = req.getRoleIds() != null ? req.getRoleIds() : Set.of();
        user.setRoles(roleIds.stream()
                .map(id -> roleRepository.findById(id).orElseThrow())
                .collect(Collectors.toSet()));
        log.debug("Roles updated for user id={}: {}", userId, roleIds);

        Set<Long> deptIds = req.getDepartmentIds() != null ? req.getDepartmentIds() : Set.of();
        user.setDepartments(deptIds.stream()
                .map(id -> departmentRepository.findById(id).orElseThrow())
                .collect(Collectors.toSet()));
        log.debug("Departments updated for user id={}: {}", userId, deptIds);

        User saved = userRepository.save(user);
        log.info("User updated: id={}, username={}", saved.getId(), saved.getUsername());
        return saved;
    }

    /** Toggles the enabled/disabled state of a user account. */
    @Transactional
    public void toggleUserEnabled(Long userId) {
        log.info("Toggling enabled state for user id={}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        log.info("User id={} is now enabled={}", userId, user.isEnabled());
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        log.debug("Fetching all users");
        return userRepository.findAll();
    }
}
