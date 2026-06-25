package org.example.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.backend.result.Result;
import org.example.backend.security.JwtAuthenticationFilter;
import org.example.backend.service.DashboardService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard/overview")
    public Result getOverview(HttpServletRequest request) {
        return Result.success("获取工作台概览成功", dashboardService.getOverview(resolveScopedManagerId(request)));
    }

    private Integer resolveScopedManagerId(HttpServletRequest request) {
        Object role = request.getAttribute(JwtAuthenticationFilter.AUTH_ROLE_ATTRIBUTE);
        if ("systemAdmin".equals(String.valueOf(role))) {
            return null;
        }

        Object rawUserId = request.getAttribute(JwtAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE);
        if (rawUserId == null) {
            return null;
        }

        try {
            return Integer.valueOf(String.valueOf(rawUserId));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
