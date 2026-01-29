package com.quizapp.controllers;

import com.quizapp.audit.Audit;
import com.quizapp.audit.AuditRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audits")
public class AuditController {
    private final AuditRepository auditRepository;

    public AuditController(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Audit> list(@RequestParam(defaultValue = "50") int limit) {
        int pageSize = Math.max(1, Math.min(500, limit)); // cap page size
        var page = auditRepository.findAll(PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return page.getContent();
    }
}
