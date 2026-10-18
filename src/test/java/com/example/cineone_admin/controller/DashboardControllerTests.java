package com.example.cineone_admin.controller;

import com.example.cineone_admin.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardControllerTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Cấu hình ViewResolver giả lập đường dẫn template để tránh lỗi Circular view path
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");

        DashboardService dashboardService = new DashboardService();
        DashboardController controller = new DashboardController(dashboardService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    @DisplayName("GET /dashboard phải trả về HTTP 200, view dashboard và model summary")
    void shouldReturnDashboardPage() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("summary"))
                .andExpect(model().attribute("activeMenu", "dashboard"));
    }

    @Test
    @DisplayName("GET / (Trang gốc) phải nạp đúng dashboard view")
    void shouldReturnDashboardOnRoot() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }
}