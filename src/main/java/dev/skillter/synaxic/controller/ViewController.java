package dev.skillter.synaxic.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/")
    public String index(HttpServletRequest request) {
        return "forward:/index.html";
    }

    @GetMapping("/health")
    public String health(HttpServletRequest request) {
        return "forward:/health.html";
    }

    @GetMapping("/analytics")
    public String analytics(HttpServletRequest request) {
        return "forward:/analytics.html";
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request) {
        return "forward:/dashboard.html";
    }

    @GetMapping("/v1/auth/login-success")
    public String loginSuccess(HttpServletRequest request) {
        return "forward:/login-success.html";
    }

    @GetMapping("/privacy-policy")
    public String privacyPolicy(HttpServletRequest request) {
        return "forward:/privacy-policy.html";
    }

    @GetMapping("/terms-of-service")
    public String termsOfService(HttpServletRequest request) {
        return "forward:/terms-of-service.html";
    }

    @GetMapping("/fair-use-policy")
    public String fairUsePolicy(HttpServletRequest request) {
        return "forward:/fair-use-policy.html";
    }
}
