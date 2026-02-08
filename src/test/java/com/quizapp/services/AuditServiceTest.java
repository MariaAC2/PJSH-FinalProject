package com.quizapp.services;

import com.quizapp.audit.Audit;
import com.quizapp.audit.AuditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditRepository auditRepository;

    @InjectMocks AuditService auditService;

    @Test
    void record_savesAudit() {
        Audit audit = new Audit();
        audit.setAction("start_attempt");
        audit.setUsername("test@example.com");

        auditService.record(audit);

        verify(auditRepository).save(audit);
    }

    @Test
    void record_swallowsRepositoryExceptions() {
        Audit audit = new Audit();
        audit.setAction("submit_attempt");
        audit.setUsername("test@example.com");

        doThrow(new RuntimeException("db down")).when(auditRepository).save(audit);

        assertDoesNotThrow(() -> auditService.record(audit));
    }
}
