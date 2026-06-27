package com.hnp.privatechatbot.entity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityTest {

    @Test
    void hasRole_whenUserHasRole_returnsTrue() {
        Role admin = new Role("ROLE_ADMIN", "Admin");
        admin.setId(1L);
        User user = new User();
        user.setRoles(Set.of(admin));

        assertThat(user.hasRole("ROLE_ADMIN")).isTrue();
    }

    @Test
    void hasRole_whenUserDoesNotHaveRole_returnsFalse() {
        Role userRole = new Role("ROLE_USER", "User");
        userRole.setId(1L);
        User user = new User();
        user.setRoles(Set.of(userRole));

        assertThat(user.hasRole("ROLE_ADMIN")).isFalse();
    }

    @Test
    void hasRole_whenNoRoles_returnsFalse() {
        User user = new User();

        assertThat(user.hasRole("ROLE_ADMIN")).isFalse();
    }

    @Test
    void hasRole_isCaseSensitive() {
        Role admin = new Role("ROLE_ADMIN", "Admin");
        admin.setId(1L);
        User user = new User();
        user.setRoles(Set.of(admin));

        assertThat(user.hasRole("role_admin")).isFalse();
        assertThat(user.hasRole("ROLE_ADMIN")).isTrue();
    }
}
