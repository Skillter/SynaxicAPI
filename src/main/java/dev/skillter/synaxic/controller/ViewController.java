package dev.skillter.synaxic.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/health")
    public String health() {
        return "forward:/health.html";
    }

    @GetMapping("/analytics")
    public String analytics() {
        return "forward:/analytics.html";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "forward:/dashboard.html";
    }

    @GetMapping("/v1/auth/login-success")
    public String loginSuccess() {
        return "forward:/login-success.html";
    }
}
