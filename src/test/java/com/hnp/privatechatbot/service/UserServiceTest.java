package com.hnp.privatechatbot.service;

import com.hnp.privatechatbot.dto.UserCreateRequest;
import com.hnp.privatechatbot.entity.Department;
import com.hnp.privatechatbot.entity.Role;
import com.hnp.privatechatbot.entity.User;
import com.hnp.privatechatbot.repository.DepartmentRepository;
import com.hnp.privatechatbot.repository.RoleRepository;
import com.hnp.privatechatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserService userService;

    private User existingUser;
    private Role adminRole;
    private Department dept;

    @BeforeEach
    void setUp() {
        adminRole = new Role("ROLE_ADMIN", "Administrator");
        adminRole.setId(1L);

        dept = new Department();
        dept.setId(10L);
        dept.setName("IT");

        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setUsername("alice");
        existingUser.setPassword("$2a$hashed");
        existingUser.setFullName("Alice Smith");
        existingUser.setEnabled(true);
        existingUser.setRoles(Set.of(adminRole));
        existingUser.setDepartments(Set.of(dept));
    }

    // ── loadUserByUsername ─────────────────────────────────────────────────────

    @Test
    void loadUserByUsername_existingEnabledUser_returnsCorrectUserDetails() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));

        UserDetails details = userService.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsername_disabledUser_returnsDisabledDetails() {
        existingUser.setEnabled(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));

        UserDetails details = userService.loadUserByUsername("alice");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void loadUserByUsername_unknownUsername_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    // ── createUser ─────────────────────────────────────────────────────────────

    @Test
    void createUser_validRequest_savesWithEncodedPassword() {
        UserCreateRequest req = buildCreateRequest("bob", "secret123", "Bob");
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$encoded");
        User saved = new User();
        saved.setId(2L);
        saved.setUsername("bob");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        User result = userService.createUser(req);

        assertThat(result.getId()).isEqualTo(2L);
        verify(passwordEncoder).encode("secret123");
        verify(userRepository).save(argThat(u -> "$2a$encoded".equals(u.getPassword())));
    }

    @Test
    void createUser_withRolesAndDepartments_assignsThem() {
        UserCreateRequest req = buildCreateRequest("bob", "secret123", "Bob");
        req.setRoleIds(Set.of(1L));
        req.setDepartmentIds(Set.of(10L));

        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(adminRole));
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(dept));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.createUser(req);

        assertThat(result.getRoles()).containsExactly(adminRole);
        assertThat(result.getDepartments()).containsExactly(dept);
    }

    @Test
    void createUser_duplicateUsername_throwsIllegalArgumentException() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        UserCreateRequest req = buildCreateRequest("alice", "pass123", "Alice");

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alice");

        verify(userRepository, never()).save(any());
    }

    // ── updateUser ─────────────────────────────────────────────────────────────

    @Test
    void updateUser_withNewPassword_encodesAndSaves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode("newpass")).thenReturn("$2a$new");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserCreateRequest req = buildUpdateRequest("Alice Updated", "newpass");
        userService.updateUser(1L, req);

        verify(passwordEncoder).encode("newpass");
        verify(userRepository).save(argThat(u -> "$2a$new".equals(u.getPassword())));
    }

    @Test
    void updateUser_withBlankPassword_keepsExistingPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserCreateRequest req = buildUpdateRequest("Alice Updated", "");
        userService.updateUser(1L, req);

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository).save(argThat(u -> "$2a$hashed".equals(u.getPassword())));
    }

    @Test
    void updateUser_withNullPassword_keepsExistingPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserCreateRequest req = buildUpdateRequest("Alice Updated", null);
        userService.updateUser(1L, req);

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateUser_withEmptyRoleIds_clearsRoles() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserCreateRequest req = buildUpdateRequest("Alice", "");
        req.setRoleIds(Set.of());
        User result = userService.updateUser(1L, req);

        assertThat(result.getRoles()).isEmpty();
    }

    @Test
    void updateUser_withNullRoleIds_clearsRoles() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserCreateRequest req = buildUpdateRequest("Alice", "");
        req.setRoleIds(null);
        User result = userService.updateUser(1L, req);

        assertThat(result.getRoles()).isEmpty();
    }

    @Test
    void updateUser_notFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(99L, buildUpdateRequest("X", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── toggleUserEnabled ──────────────────────────────────────────────────────

    @Test
    void toggleUserEnabled_fromTrue_setsDisabled() {
        existingUser.setEnabled(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.toggleUserEnabled(1L);

        verify(userRepository).save(argThat(u -> !u.isEnabled()));
    }

    @Test
    void toggleUserEnabled_fromFalse_setsEnabled() {
        existingUser.setEnabled(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.toggleUserEnabled(1L);

        verify(userRepository).save(argThat(User::isEnabled));
    }

    @Test
    void toggleUserEnabled_notFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.toggleUserEnabled(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private UserCreateRequest buildCreateRequest(String username, String password, String fullName) {
        UserCreateRequest r = new UserCreateRequest();
        r.setUsername(username);
        r.setPassword(password);
        r.setFullName(fullName);
        return r;
    }

    private UserCreateRequest buildUpdateRequest(String fullName, String password) {
        UserCreateRequest r = new UserCreateRequest();
        r.setUsername("irrelevant");
        r.setFullName(fullName);
        r.setPassword(password);
        return r;
    }
}
