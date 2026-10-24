package com.example.cineone_admin.controller;

import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.repository.*;
import com.example.cineone_admin.service.ProviderService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:provider14;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@Transactional
@WithMockUser(roles = "ADMIN")
class ProviderControllerIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired DataProviderRepository providers;
    @Autowired ProviderMappingRepository mappings;
    @Autowired ProviderService service;
    MockMvc mvc;
    DataProvider active, stopped;
    ProviderMapping mapping;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        active = provider("CGV", "CGV", "ACTIVE");
        stopped = provider("LOTTE", "Lotte", "INACTIVE");
        mapping = mapping(active, "CINEMA", 101L, "CGV-8891", "ACTIVE");
        mapping(stopped, "MOVIE", 102L, "LOT-100", "ERROR");
    }

    @Test void rendersAllScreensWithRealThymeleaf() throws Exception {
        mvc.perform(get("/providers")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Thêm nhà cung cấp")));
        mvc.perform(get("/providers/new")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Lưu nhà cung cấp")));
        mvc.perform(get("/providers/{id}/edit", active.getProviderId())).andExpect(status().isOk());
        mvc.perform(get("/providers/{id}", active.getProviderId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("CGV")));
        mvc.perform(get("/mappings")).andExpect(status().isOk())
                .andExpect(content().string(containsString("CGV-8891")))
                .andExpect(content().string(not(containsString("LOT-100"))));
        mvc.perform(get("/mappings/{id}", mapping.getMappingId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("CGV-8891")));
        String preview = System.getProperty("provider.preview.dir");
        if (preview != null) {
            var directory = java.nio.file.Path.of(preview);
            java.nio.file.Files.createDirectories(directory);
            String[] routes = {"/providers", "/providers/new", "/providers/" + active.getProviderId(),
                    "/providers/" + active.getProviderId() + "/edit", "/mappings", "/mappings/" + mapping.getMappingId()};
            for (int i = 0; i < routes.length; i++) {
                var html = mvc.perform(get(routes[i])).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                java.nio.file.Files.writeString(directory.resolve("screen-" + i + ".html"), html);
            }
        }
    }

    @Test void combinesFiltersAndCanInspectStoppedProvider() throws Exception {
        mvc.perform(get("/mappings").param("providerId", stopped.getProviderId().toString())
                        .param("entity", "MOVIE").param("status", "ERROR").param("q", "102")
                        .param("includeStopped", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("LOT-100")))
                .andExpect(content().string(not(containsString("CGV-8891"))));
        assertEquals(0, service.searchMappings(active.getProviderId(),
                com.example.cineone_admin.enums.ProviderEntityType.MOVIE, "", "", 0, true).getTotalElements());
        mvc.perform(get("/mappings").param("q", "cgv-8891")).andExpect(status().isOk())
                .andExpect(content().string(containsString("CGV-8891")));
    }

    @Test void searchTreatsWildcardsAsLiteralAndPaginationKeepsFilters() throws Exception {
        assertEquals(0, service.search("%", 0).getTotalElements());
        assertEquals(0, service.searchMappings(null, null, "", "_", 0, true).getTotalElements());
        for (int i = 0; i < 12; i++) mapping(active, "ROOM", 200L + i, "ROOM-" + i, "ACTIVE");
        mvc.perform(get("/mappings").param("q", "ROOM-").param("entity", "ROOM")
                        .param("status", "ACTIVE").param("includeStopped", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(containsString("q=ROOM-")));
        mvc.perform(get("/mappings").param("page", "99")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Không tìm thấy mapping")));
    }

    @Test void stopAndReactivatePreserveMappingsAndTheirState() throws Exception {
        mvc.perform(post("/providers/{id}/status", active.getProviderId()).with(csrf()).param("enabled", "false"))
                .andExpect(status().is3xxRedirection());
        assertEquals("INACTIVE", providers.findById(active.getProviderId()).orElseThrow().getStatus());
        assertEquals(0, service.searchMappings(null, null, "", "", 0, false).getTotalElements());
        assertEquals(2, mappings.count());
        assertEquals("ACTIVE", mappings.findById(mapping.getMappingId()).orElseThrow().getStatus());
        mvc.perform(post("/providers/{id}/status", active.getProviderId()).with(csrf()).param("enabled", "true"))
                .andExpect(status().is3xxRedirection());
        assertEquals(1, service.searchMappings(null, null, "", "", 0, false).getTotalElements());
        assertEquals(mapping.getMappingId(), service.mapping(mapping.getMappingId()).mappingId());
    }

    @Test void addsNormalizesAndEditsProvider() throws Exception {
        mvc.perform(post("/providers").with(csrf()).param("code", " beta ").param("name", " Beta ")
                        .param("providerType", " cinema ").param("mock", "true"))
                .andExpect(status().is3xxRedirection());
        var beta = providers.findByCode("BETA").orElseThrow();
        assertEquals("Beta", beta.getName());
        assertEquals("ACTIVE", beta.getStatus());
        mvc.perform(post("/providers/{id}", beta.getProviderId()).with(csrf())
                        .param("code", "BETA").param("name", "Beta mới").param("providerType", "CINEMA"))
                .andExpect(status().is3xxRedirection());
        assertEquals("Beta mới", beta.getName());
        assertFalse(beta.getMock());
    }

    @Test void duplicateAndInvalidFormsShowErrorsWithoutWriting() throws Exception {
        mvc.perform(post("/providers").with(csrf()).param("code", "CGV")
                        .param("name", "Duplicate").param("providerType", "CINEMA"))
                .andExpect(status().isOk()).andExpect(model().hasErrors())
                .andExpect(content().string(containsString("Mã nhà cung cấp đã tồn tại")));
        mvc.perform(post("/providers").with(csrf()).param("code", "bad-code")
                        .param("name", " ").param("providerType", "CINEMA"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("providerForm", "code", "name"));
        assertEquals(2, providers.count());
    }

    @Test void cannotChangeProviderIdentity() throws Exception {
        mvc.perform(post("/providers/{id}", active.getProviderId()).with(csrf())
                        .param("code", "OTHER").param("name", "CGV").param("providerType", "CINEMA"))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        assertEquals("CGV", active.getCode());
    }

    @Test void invalidAndMissingLookupsReturnClientErrors() throws Exception {
        mvc.perform(get("/providers/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/mappings/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/mappings").param("entity", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/mappings").param("status", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/mappings").param("providerId", "999999")).andExpect(status().isNotFound());
    }

    @Test void protectsPagesAndMutations() throws Exception {
        mvc.perform(get("/providers").with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/mappings").with(user("member").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/providers/{id}/status", active.getProviderId()).param("enabled", "false"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/providers/{id}/status", active.getProviderId())
                        .with(user("member").roles("USER")).with(csrf()).param("enabled", "false"))
                .andExpect(status().isForbidden());
        assertEquals("ACTIVE", active.getStatus());
    }

    @Test void escapesSourceValuesAndHandlesUnsyncedMapping() throws Exception {
        mapping.setExternalId("<script>alert(1)</script>");
        mapping.setLastSyncedAt(null);
        mappings.saveAndFlush(mapping);
        mvc.perform(get("/mappings/{id}", mapping.getMappingId())).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("Chưa đồng bộ")));
    }

    private DataProvider provider(String code, String name, String status) {
        var p = new DataProvider(); p.setCode(code); p.setName(name);
        p.setProviderType("CINEMA"); p.setMock(true); p.setStatus(status);
        return providers.saveAndFlush(p);
    }
    private ProviderMapping mapping(DataProvider provider, String entity, Long internal, String external, String status) {
        var m = new ProviderMapping(); m.setProvider(provider); m.setEntityType(entity);
        m.setInternalId(internal); m.setExternalId(external); m.setStatus(status);
        m.setLastSyncedAt(LocalDateTime.of(2026, 10, 23, 14, 30));
        return mappings.saveAndFlush(m);
    }
}

