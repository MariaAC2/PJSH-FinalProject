package com.quizapp.controllers;

import com.quizapp.audit.Audit;
import com.quizapp.audit.AuditRepository;
import com.quizapp.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditController.class)
@Import(AuditControllerTest.MethodSecurityTestConfig.class)
class AuditControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AuditRepository auditRepository;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_returnsAuditsForAdmin() throws Exception {
        Audit audit = new Audit();
        audit.setAction("login");
        audit.setUsername("admin@example.com");
        audit.setCreatedAt(Instant.parse("2024-03-03T12:00:00Z"));

        when(auditRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(audit)));

        mockMvc.perform(get("/api/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("login"))
                .andExpect(jsonPath("$[0].username").value("admin@example.com"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_capsLimitAt500() throws Exception {
        when(auditRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/audits?limit=600"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRepository).findAll(pageableCaptor.capture());
        assertEquals(500, pageableCaptor.getValue().getPageSize());
    }

    @Test
    @WithMockUser(roles = "USER")
    void list_forbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/audits"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(auditRepository);
    }
}