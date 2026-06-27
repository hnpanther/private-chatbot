package com.hnp.privatechatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/**
 * Form DTO for creating or updating a user from the admin panel.
 * When used for an update, a blank {@code password} is treated as "no change".
 */
@Data
public class UserCreateRequest {

    @NotBlank
    @Size(min = 3, max = 100)
    private String username;

    /** Plain-text password — will be BCrypt-encoded before persistence. */
    @NotBlank
    @Size(min = 6)
    private String password;

    @NotBlank
    private String fullName;

    private String email;

    /** IDs of {@link com.hnp.privatechatbot.entity.Role} records to assign. */
    private Set<Long> roleIds;

    /** IDs of {@link com.hnp.privatechatbot.entity.Department} records to assign. */
    private Set<Long> departmentIds;
}
