package com.hnp.privatechatbot.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the login page.
 * Actual credential verification is handled by Spring Security's form-login filter;
 * this controller only populates error/logout flash messages for the Thymeleaf template.
 */
@Controller
@Slf4j
public class AuthController {

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            Model model) {
        if (error != null) {
            log.debug("Login page rendered with authentication error");
            model.addAttribute("errorMsg", "Invalid username or password");
        }
        if (logout != null) {
            log.debug("Login page rendered after successful logout");
            model.addAttribute("logoutMsg", "You have been signed out");
        }
        return "auth/login";
    }
}
