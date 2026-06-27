package com.hnp.privatechatbot.controller;

import com.hnp.privatechatbot.config.DataInitializer;
import com.hnp.privatechatbot.entity.ChatBot;
import com.hnp.privatechatbot.entity.Department;
import com.hnp.privatechatbot.service.AdminService;
import com.hnp.privatechatbot.service.LlmService;
import com.hnp.privatechatbot.service.UserService;
import com.hnp.privatechatbot.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AdminControllerTest {

    @Autowired WebApplicationContext wac;

    @MockitoBean AdminService adminService;
    @MockitoBean UserService userService;
    @MockitoBean RoleRepository roleRepository;
    @MockitoBean LlmService llmService;
    @MockitoBean DataInitializer dataInitializer;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // ── Authorization ──────────────────────────────────────────────────────────

    @Test
    void adminPages_unauthenticated_redirectToLogin() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminPages_regularUser_returns403() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    // ── Dashboard ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDashboard_admin_returns200() throws Exception {
        when(adminService.getDashboardStats()).thenReturn(Map.of(
                "users", 5L, "departments", 2L, "chatbots", 3L,
                "sessions", 10L, "messages", 50L));

        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"));
    }

    // ── Users ──────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUsers_admin_returns200WithModel() throws Exception {
        when(userService.findAll()).thenReturn(List.of());
        when(roleRepository.findAll()).thenReturn(List.of());
        when(adminService.getAllDepartments()).thenReturn(List.of());

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attributeExists("users", "roles", "departments"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_validInput_redirectsToUsers() throws Exception {
        mockMvc.perform(post("/admin/users/create").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "newuser")
                        .param("password", "secret123")
                        .param("fullName", "New User"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_validationError_redirectsWithErrorFlash() throws Exception {
        // username too short, password too short, fullName blank → validation fails
        mockMvc.perform(post("/admin/users/create").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "x")
                        .param("password", "p")
                        .param("fullName", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUser_admin_redirectsToUsers() throws Exception {
        mockMvc.perform(post("/admin/users/1/update").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("fullName", "Updated Name"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void toggleUser_admin_redirectsToUsers() throws Exception {
        mockMvc.perform(post("/admin/users/1/toggle").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
    }

    // ── Departments ────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDepartments_admin_returns200() throws Exception {
        when(adminService.getAllDepartments()).thenReturn(List.of());

        mockMvc.perform(get("/admin/departments"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/departments"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void saveDepartment_admin_redirectsToDepartments() throws Exception {
        Department dept = new Department();
        dept.setId(1L);
        dept.setName("Finance");
        when(adminService.saveDepartment(any())).thenReturn(dept);

        mockMvc.perform(post("/admin/departments/save").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Finance"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteDepartment_admin_redirectsToDepartments() throws Exception {
        mockMvc.perform(post("/admin/departments/1/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/departments"));
    }

    // ── ChatBots ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void getChatbots_admin_returns200() throws Exception {
        when(adminService.getAllChatBots()).thenReturn(List.of());
        when(adminService.getAllDepartments()).thenReturn(List.of());

        mockMvc.perform(get("/admin/chatbots"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/chatbots"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void saveChatBot_admin_redirectsToChatbots() throws Exception {
        Department dept = new Department();
        dept.setId(1L);
        dept.setName("HR");
        when(adminService.getAllDepartments()).thenReturn(List.of(dept));
        ChatBot saved = new ChatBot();
        saved.setId(5L);
        when(adminService.saveChatBot(any())).thenReturn(saved);

        mockMvc.perform(post("/admin/chatbots/save").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "New Bot")
                        .param("departmentId", "1")
                        .param("llmProvider", "GLOBAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/chatbots"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void toggleChatBot_admin_redirectsToChatbots() throws Exception {
        mockMvc.perform(post("/admin/chatbots/10/toggle").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/chatbots"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteChatBot_admin_redirectsToChatbots() throws Exception {
        mockMvc.perform(post("/admin/chatbots/10/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/chatbots"));
    }

    // ── CSRF ───────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void postWithoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/admin/users/1/toggle"))
                .andExpect(status().isForbidden());
    }
}
