package com.quizapp.services;

import com.quizapp.audit.Audit;
import com.quizapp.audit.AuditRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Async("auditExecutor")
    public void record(Audit audit) {
        // ensure no exceptions escape the async thread
        try {
            auditRepository.save(audit);
        } catch (Exception ex) {
            // log to stdout for now; in prod use a logger
            System.err.println("Failed to save audit: " + ex.getMessage());
        }
    }
}
