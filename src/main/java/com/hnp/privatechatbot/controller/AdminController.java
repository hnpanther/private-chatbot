package com.hnp.privatechatbot.controller;

import com.hnp.privatechatbot.dto.UserCreateRequest;
import com.hnp.privatechatbot.entity.ChatBot;
import com.hnp.privatechatbot.entity.Department;
import com.hnp.privatechatbot.service.AdminService;
import com.hnp.privatechatbot.service.UserService;
import com.hnp.privatechatbot.repository.RoleRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin panel controller — all routes require ROLE_ADMIN (enforced in SecurityConfig).
 *
 * Handles CRUD for:
 * - Users           (/admin/users)
 * - Departments     (/admin/departments)
 * - Chatbots        (/admin/chatbots)
 *
 * Dashboard aggregation is at GET /admin.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;
    private final RoleRepository roleRepository;

    @GetMapping
    public String dashboard(Model model) {
        log.debug("GET /admin (dashboard)");
        model.addAttribute("stats", adminService.getDashboardStats());
        return "admin/dashboard";
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public String users(Model model) {
        log.debug("GET /admin/users");
        model.addAttribute("users", userService.findAll());
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("departments", adminService.getAllDepartments());
        model.addAttribute("newUser", new UserCreateRequest());
        return "admin/users";
    }

    @PostMapping("/users/create")
    public String createUser(@Valid @ModelAttribute("newUser") UserCreateRequest req,
                             BindingResult br, RedirectAttributes ra, Model model) {
        log.info("POST /admin/users/create: username={}", req.getUsername());
        if (br.hasErrors()) {
            log.debug("Validation errors creating user: {}", br.getAllErrors());
            model.addAttribute("users", userService.findAll());
            model.addAttribute("roles", roleRepository.findAll());
            model.addAttribute("departments", adminService.getAllDepartments());
            return "admin/users";
        }
        try {
            userService.createUser(req);
            ra.addFlashAttribute("success", "User created successfully");
        } catch (Exception e) {
            log.warn("Error creating user: username={}, error={}", req.getUsername(), e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id, RedirectAttributes ra) {
        log.info("POST /admin/users/{}/toggle", id);
        try {
            userService.toggleUserEnabled(id);
            ra.addFlashAttribute("success", "User status changed");
        } catch (Exception e) {
            log.warn("Error toggling user id={}: {}", id, e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ── Departments ───────────────────────────────────────────────────────────

    @GetMapping("/departments")
    public String departments(Model model) {
        log.debug("GET /admin/departments");
        model.addAttribute("departments", adminService.getAllDepartments());
        model.addAttribute("newDept", new Department());
        return "admin/departments";
    }

    @PostMapping("/departments/save")
    public String saveDepartment(@ModelAttribute Department department, RedirectAttributes ra) {
        log.info("POST /admin/departments/save: id={}, name={}", department.getId(), department.getName());
        try {
            adminService.saveDepartment(department);
            ra.addFlashAttribute("success", "Department saved successfully");
        } catch (Exception e) {
            log.warn("Error saving department name={}: {}", department.getName(), e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @PostMapping("/departments/{id}/delete")
    public String deleteDepartment(@PathVariable Long id, RedirectAttributes ra) {
        log.info("POST /admin/departments/{}/delete", id);
        try {
            adminService.deleteDepartment(id);
            ra.addFlashAttribute("success", "Department deleted successfully");
        } catch (Exception e) {
            log.warn("Error deleting department id={}: {}", id, e.getMessage());
            ra.addFlashAttribute("error", "Delete failed: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    // ── ChatBots ──────────────────────────────────────────────────────────────

    @GetMapping("/chatbots")
    public String chatbots(Model model) {
        log.debug("GET /admin/chatbots");
        model.addAttribute("chatbots", adminService.getAllChatBots());
        model.addAttribute("departments", adminService.getAllDepartments());
        model.addAttribute("newBot", new ChatBot());
        return "admin/chatbots";
    }

    @PostMapping("/chatbots/save")
    public String saveChatBot(@ModelAttribute ChatBot chatBot,
                              @RequestParam Long departmentId,
                              RedirectAttributes ra) {
        log.info("POST /admin/chatbots/save: id={}, name={}, departmentId={}, provider={}",
                chatBot.getId(), chatBot.getName(), departmentId, chatBot.getLlmProvider());
        try {
            Department dept = adminService.getAllDepartments().stream()
                    .filter(d -> d.getId().equals(departmentId))
                    .findFirst()
                    .orElseThrow();
            chatBot.setDepartment(dept);

            // When editing: retain the stored API key if the form field was left blank
            // to avoid accidentally clearing a previously configured key.
            if (chatBot.getId() != null
                    && (chatBot.getLlmApiKey() == null || chatBot.getLlmApiKey().isBlank())) {
                adminService.getChatBotById(chatBot.getId())
                        .ifPresent(existing -> chatBot.setLlmApiKey(existing.getLlmApiKey()));
                log.debug("Retaining existing API key for chatbot id={}", chatBot.getId());
            }

            adminService.saveChatBot(chatBot);
            ra.addFlashAttribute("success", "ChatBot saved successfully");
        } catch (Exception e) {
            log.warn("Error saving chatbot name={}: {}", chatBot.getName(), e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/chatbots";
    }

    @PostMapping("/chatbots/{id}/toggle")
    public String toggleChatBot(@PathVariable Long id, RedirectAttributes ra) {
        log.info("POST /admin/chatbots/{}/toggle", id);
        adminService.toggleChatBotActive(id);
        ra.addFlashAttribute("success", "ChatBot status changed");
        return "redirect:/admin/chatbots";
    }

    @PostMapping("/chatbots/{id}/delete")
    public String deleteChatBot(@PathVariable Long id, RedirectAttributes ra) {
        log.info("POST /admin/chatbots/{}/delete", id);
        try {
            adminService.deleteChatBot(id);
            ra.addFlashAttribute("success", "ChatBot deleted successfully");
        } catch (Exception e) {
            log.warn("Error deleting chatbot id={}: {}", id, e.getMessage());
            ra.addFlashAttribute("error", "Delete failed: " + e.getMessage());
        }
        return "redirect:/admin/chatbots";
    }
}
