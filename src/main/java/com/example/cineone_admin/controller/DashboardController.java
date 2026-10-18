package com.example.cineone_admin.controller;

import com.example.cineone_admin.dto.dashboard.DashboardSummaryDTO;
import com.example.cineone_admin.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping({"", "/", "/dashboard", "/admin"})
    public String showDashboard(Model model) {
        DashboardSummaryDTO summary = dashboardService.getDashboardSummary();
        model.addAttribute("summary", summary);
        model.addAttribute("activeMenu", "dashboard");
        return "dashboard";
    }
}